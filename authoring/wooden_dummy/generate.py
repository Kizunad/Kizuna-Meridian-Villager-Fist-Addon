#!/usr/bin/env python3
"""从木人桩造型生成自带贴图的 bbmodel、贴图和原版方块模型。

运行：python3 authoring/wooden_dummy/generate.py
作者稿（WoodenDummy.bbmodel、wooden_dummy.png）写在本目录；运行时资源直接写进 fabric 模块的资源目录。
贴图是程序画的像素木纹，每个模型单位对应 1 像素，只借原版木头的色调，不拷原版像素。
"""

import base64
import io
import json
import random
import uuid
from pathlib import Path

from PIL import Image

from model import (ARM_SECTION, LEG_ROOT_Y, MIDDLE_ARM_Y, TRUNK_MAX, TRUNK_MIN, TRUNK_TOP, UPPER_ARM_Y, Box, build)

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT.parents[1] / "fabric/src/main/resources/assets/meridian_villager"
NAMESPACE = "meridian_villager"
TEXTURE_SIZE = 64
JAVA_UV = 16 / TEXTURE_SIZE
ALLOWED_ANGLES = (-45, -22.5, 0, 22.5, 45)
ELEMENT_MIN, ELEMENT_MAX = -16, 32
SEED = 20261114

# 色板从暗到亮：立柱用云杉调，木臂和木腿用浅暖木，木脚用深橡木。
TRUNK = [(78, 56, 31), (92, 67, 38), (104, 76, 44), (116, 86, 50), (128, 95, 56)]
LIMB = [(140, 100, 58), (153, 111, 66), (166, 122, 74), (178, 133, 82), (190, 143, 90)]
FOOT = [(48, 31, 15), (57, 37, 18), (66, 43, 21), (74, 48, 24), (82, 53, 26)]
MORTISE = (52, 36, 20)

# 贴图分区（像素坐标起点和大小）。
TRUNK_SIDE = (0, 0, 8, 24)
TRUNK_FRONT = (8, 0, 8, 24)
TRUNK_TOP_REGION = (16, 0, 8, 8)
LIMB_END = (16, 8, 2, 2)
FOOT_END = (18, 8, 2, 2)
LIMB_LONG = (24, 0, 8, 12)
FOOT_LONG = (32, 0, 4, 18)
LIMB_ACROSS = (0, 24, 12, 4)
FOOT_ACROSS = (0, 28, 18, 4)

DISPLAY = {
    # 桩身 24 高、中心在 y=12：放大到 0.62 后往下挪 2.5，物品栏格子里上下居中。
    "gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.62, 0.62, 0.62]},
    "ground": {"translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -2, 0], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1, 0], "scale": [0.3, 0.3, 0.3]},
}


def stable_id(name):
    """同一个名字每次生成同一个 UUID，重新生成不会让 Blockbench 认成新部件。"""
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"kizuna-meridian/WoodenDummy/{name}"))


def paint_grain(atlas, region, palette, lengthwise, rng, dark_edges=False):
    """画一块木纹：沿纹理方向每一条先定一个基调，再零星压暗或提亮几个像素；dark_edges 把垂直于纹理的两边压暗，显出圆角。"""
    left, top, width, height = region
    lanes = width if lengthwise == "v" else height
    span = height if lengthwise == "v" else width
    for lane in range(lanes):
        tone = rng.choice([1, 2, 2, 3, 3, 4])
        if dark_edges and lane in (0, lanes - 1):
            tone = 0
        elif dark_edges and lane in (1, lanes - 2):
            tone = min(tone, 2)
        for step in range(span):
            shade = tone
            roll = rng.random()
            if roll < 0.10:
                shade = max(0, tone - 1)
            elif roll > 0.92:
                shade = min(len(palette) - 1, tone + 1)
            x, y = (left + lane, top + step) if lengthwise == "v" else (left + step, top + lane)
            atlas.putpixel((x, y), (*palette[shade], 255))


def paint_rings(atlas, region, palette):
    """立柱顶面的年轮：从外到里一圈暗一圈亮，四角是退进去的地方，用最暗的颜色。"""
    left, top, width, height = region
    for y in range(height):
        for x in range(width):
            ring = min(x, y, width - 1 - x, height - 1 - y)
            corner = (x in (0, width - 1)) and (y in (0, height - 1))
            shade = 0 if corner else [1, 3, 2, 4][ring % 4]
            atlas.putpixel((left + x, top + y), (*palette[shade], 255))


def paint_end(atlas, region, palette):
    """木臂、木脚端头的截面：四个像素，对角一明一暗。"""
    left, top, _, _ = region
    for x, y, shade in ((0, 0, 3), (1, 0, 2), (0, 1, 2), (1, 1, 4)):
        atlas.putpixel((left + x, top + y), (*palette[shade], 255))


def paint_mortises(atlas):
    """立柱正面在三只木臂和木腿的根部画暗色榫眼。正面按 u = 12 − x、v = 24 − y 投影。

    中臂和木腿各画下沿加两侧；上两臂并排，只画下沿一道，免得两圈连成一条横箍。
    """
    left, top, width, height = TRUNK_FRONT
    centre = (TRUNK_MIN + TRUNK_MAX) / 2
    roots = [(TRUNK_MIN + 1 + ARM_SECTION / 2, UPPER_ARM_Y, False), (TRUNK_MAX - 1 - ARM_SECTION / 2, UPPER_ARM_Y, False),
             (centre, MIDDLE_ARM_Y, True), (centre, LEG_ROOT_Y, True)]
    for root_x, root_y, with_sides in roots:
        u0 = int(TRUNK_MAX - (root_x + ARM_SECTION / 2))
        v0 = int(TRUNK_TOP - (root_y + ARM_SECTION))
        marks = [(u0 + du, v0 + ARM_SECTION) for du in range(ARM_SECTION)]
        if with_sides:
            marks += [(u0 + du, v0 + dv) for du in (-1, ARM_SECTION) for dv in range(ARM_SECTION + 1)]
        for u, v in marks:
            if 0 <= u < width and 0 <= v < height:
                atlas.putpixel((left + u, top + v), (*MORTISE, 255))


def make_texture():
    """整张 64×64 贴图；随机数只用来分布木纹的深浅，种子固定，每次生成结果相同。"""
    atlas = Image.new("RGBA", (TEXTURE_SIZE, TEXTURE_SIZE), (0, 0, 0, 0))
    rng = random.Random(SEED)
    paint_grain(atlas, TRUNK_SIDE, TRUNK, "v", rng, dark_edges=True)
    paint_grain(atlas, TRUNK_FRONT, TRUNK, "v", rng, dark_edges=True)
    paint_mortises(atlas)
    paint_rings(atlas, TRUNK_TOP_REGION, TRUNK)
    paint_end(atlas, LIMB_END, LIMB)
    paint_end(atlas, FOOT_END, FOOT)
    paint_grain(atlas, LIMB_LONG, LIMB, "v", rng)
    paint_grain(atlas, FOOT_LONG, FOOT, "v", rng)
    paint_grain(atlas, LIMB_ACROSS, LIMB, "h", rng)
    paint_grain(atlas, FOOT_ACROSS, FOOT, "h", rng)
    return atlas


def trunk_faces(box):
    """立柱三个部件按世界坐标投影，纹理在部件之间接得上；朝北的是正面，带榫眼。"""
    (x0, y0, z0), (x1, y1, z1) = box.lower, box.upper
    v = (TRUNK_TOP - y1, TRUNK_TOP - y0)
    side, front, top = TRUNK_SIDE, TRUNK_FRONT, TRUNK_TOP_REGION
    flat = [top[0] + x0 - TRUNK_MIN, top[1] + z0 - TRUNK_MIN, top[0] + x1 - TRUNK_MIN, top[1] + z1 - TRUNK_MIN]
    return {
        "north": [front[0] + TRUNK_MAX - x1, v[0], front[0] + TRUNK_MAX - x0, v[1]],
        "south": [side[0] + x0 - TRUNK_MIN, v[0], side[0] + x1 - TRUNK_MIN, v[1]],
        "west": [side[0] + z0 - TRUNK_MIN, v[0], side[0] + z1 - TRUNK_MIN, v[1]],
        "east": [side[0] + TRUNK_MAX - z1, v[0], side[0] + TRUNK_MAX - z0, v[1]],
        "up": flat,
        "down": flat,
    }


def beam_faces(box, lane, long_region, across_region, end_region):
    """沿 z 方向的木条（木臂、大腿、木脚）：上下面顺着长度走竖纹，东西两面走横纹，两头是截面。lane 让并排的几根取不同的纹。"""
    width = box.upper[0] - box.lower[0]
    height = box.upper[1] - box.lower[1]
    length = min(box.upper[2] - box.lower[2], long_region[3], across_region[2])
    lanes = long_region[2] // int(width)
    u = long_region[0] + (lane % lanes) * width
    rows = across_region[3] // int(height)
    v = across_region[1] + (lane % rows) * height
    end = [end_region[0], end_region[1], end_region[0] + width, end_region[1] + height]
    top = [u, long_region[1], u + width, long_region[1] + length]
    side = [across_region[0], v, across_region[0] + length, v + height]
    return {"north": end, "south": end, "west": side, "east": side, "up": top, "down": top}


def post_faces(box, long_region, end_region):
    """竖着的短木条（小腿）：四个侧面走竖纹，上下是截面。"""
    width = box.upper[0] - box.lower[0]
    height = min(box.upper[1] - box.lower[1], long_region[3])
    side = [long_region[0], long_region[1], long_region[0] + width, long_region[1] + height]
    end = [end_region[0], end_region[1], end_region[0] + width, end_region[1] + width]
    return {"north": side, "south": side, "west": side, "east": side, "up": end, "down": end}


def faces(box, lane):
    """每个面在贴图上的矩形，像素坐标；hidden_faces 里的面不贴图、不导出。"""
    if box.material == "trunk":
        mapped = trunk_faces(box)
    elif box.material == "foot":
        mapped = beam_faces(box, lane, FOOT_LONG, FOOT_ACROSS, FOOT_END)
    elif box.name == "shin":
        mapped = post_faces(box, LIMB_LONG, LIMB_END)
    else:
        mapped = beam_faces(box, lane, LIMB_LONG, LIMB_ACROSS, LIMB_END)
    return {face: None if face in box.hidden_faces else [round(n, 4) for n in uv] for face, uv in mapped.items()}


def validate(boxes):
    """名字不重复、角度只用几档、顶点落在原版方块模型允许的范围里；返回旋转后的外包范围。"""
    names = [box.name for box in boxes]
    if len(names) != len(set(names)):
        raise ValueError("部件名称重复")
    for box in boxes:
        if any(high <= low for low, high in zip(box.lower, box.upper)) or box.angle not in ALLOWED_ANGLES:
            raise ValueError(f"尺寸或旋转不合规：{box.name}")
        for corner in box.corners():
            if any(value < ELEMENT_MIN or value > ELEMENT_MAX for value in corner):
                raise ValueError(f"部件超出原版方块模型范围：{box.name} {corner}")
    corners = [corner for box in boxes for corner in box.corners()]
    return ([round(min(point[i] for point in corners), 2) for i in range(3)],
            [round(max(point[i] for point in corners), 2) for i in range(3)])


def bbmodel_element(box, mapped):
    """Blockbench 里的一个立方体；旋转写成三轴数组，只有一根轴非零。"""
    rotation = [0, 0, 0]
    rotation["xyz".index(box.axis)] = box.angle
    return {"name": box.name, "type": "cube", "uuid": stable_id(box.name),
            "from": list(box.lower), "to": list(box.upper),
            "origin": list(box.pivot or box.center()), "rotation": rotation,
            "rescale": False, "box_uv": False, "autouv": 0, "color": ["trunk", "arm", "foot"].index(box.material),
            "faces": {face: {"uv": uv or [0, 0, 0, 0], "texture": None if uv is None else 0}
                      for face, uv in mapped.items()}}


def java_element(box, mapped):
    """原版方块模型里的一个元素；UV 换成 0 到 16 的刻度，藏起来的面不写。"""
    exported = {"name": box.name, "from": list(box.lower), "to": list(box.upper)}
    if box.angle:
        exported["rotation"] = {"origin": list(box.pivot or box.center()), "axis": box.axis,
                                "angle": box.angle, "rescale": False}
    exported["faces"] = {face: {"uv": [round(n * JAVA_UV, 4) for n in uv], "texture": "#dummy"}
                         for face, uv in mapped.items() if uv is not None}
    return exported


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    boxes = build()
    lower, upper = validate(boxes)
    png = io.BytesIO()
    make_texture().save(png, format="PNG")
    mapped = [faces(box, lane) for lane, box in enumerate(boxes)]
    groups = list(dict.fromkeys(box.group for box in boxes))
    write_json(ROOT / "WoodenDummy.bbmodel", {
        "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
        "name": "WoodenDummy", "model_identifier": "wooden_dummy",
        "resolution": {"width": TEXTURE_SIZE, "height": TEXTURE_SIZE},
        "elements": [bbmodel_element(box, uv) for box, uv in zip(boxes, mapped)],
        "outliner": [{"name": group, "uuid": stable_id(group), "origin": [8, 0, 8], "rotation": [0, 0, 0],
                      "export": True, "visibility": True, "isOpen": True,
                      "children": [stable_id(box.name) for box in boxes if box.group == group]} for group in groups],
        "textures": [{"name": "wooden_dummy.png", "id": "0", "uuid": stable_id("texture"),
                      "path": "wooden_dummy.png", "folder": "block", "namespace": NAMESPACE,
                      "width": TEXTURE_SIZE, "height": TEXTURE_SIZE,
                      "uv_width": TEXTURE_SIZE, "uv_height": TEXTURE_SIZE,
                      "particle": True, "render_mode": "default",
                      "source": "data:image/png;base64," + base64.b64encode(png.getvalue()).decode()}],
        "display": DISPLAY,
    })
    (ROOT / "wooden_dummy.png").write_bytes(png.getvalue())

    texture = ASSETS / "textures/block/wooden_dummy.png"
    texture.parent.mkdir(parents=True, exist_ok=True)
    texture.write_bytes(png.getvalue())
    write_json(ASSETS / "models/block/wooden_dummy.json", {
        "credit": "Kizuna's Meridian Villager Fist / authoring/wooden_dummy",
        "parent": "minecraft:block/block",
        "textures": {"dummy": f"{NAMESPACE}:block/wooden_dummy", "particle": f"{NAMESPACE}:block/wooden_dummy"},
        "display": DISPLAY,
        "elements": [java_element(box, uv) for box, uv in zip(boxes, mapped)],
    })
    print(f"WoodenDummy：{len(boxes)} 个立方体，{len(groups)} 组；外包范围 {lower} .. {upper}")


if __name__ == "__main__":
    main()
