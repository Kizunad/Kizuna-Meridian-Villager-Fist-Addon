#!/usr/bin/env python3
"""由上卷卷轴图标得到中卷的：同一卷纸，把捆卷轴的草绳换成靛青布带。详见 docs/wiki/Art.md。

只动草绳那一类颜色（饱和的橙褐中间调），卷纸和描边一个像素不改；换色时保留每个像素原来的明暗。
用法：python3 scripts/recolor_inner_scroll.py
"""
import colorsys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ITEMS = ROOT / "fabric/src/main/resources/assets/meridian_villager/textures/item"
# 草绳：比卷纸饱和，比描边亮，色相在橙褐一段。
CORD_SATURATION_MIN = 0.62
CORD_VALUE_MIN = 0.30
CORD_VALUE_MAX = 0.82
CORD_HUE_MAX = 0.11
# 靛青布带：色相和饱和度固定，明暗沿用草绳原来的，再整体压暗一点，免得比卷纸还跳。
BAND_HUE = 0.62
BAND_SATURATION = 0.58
BAND_VALUE_SCALE = 0.92
# 草绳只在卷轴中段：沿卷轴方向量（横坐标减纵坐标），两头卷纸的暗部不算。
CORD_ALONG_MIN = -17
CORD_ALONG_MAX = 12
OPAQUE = 255
CHANNEL_MAX = 255


def band(pixel, along):
    red, green, blue, alpha = pixel
    hue, saturation, value = colorsys.rgb_to_hsv(red / CHANNEL_MAX, green / CHANNEL_MAX, blue / CHANNEL_MAX)
    cord = (alpha == OPAQUE and CORD_ALONG_MIN <= along <= CORD_ALONG_MAX and hue <= CORD_HUE_MAX and saturation >= CORD_SATURATION_MIN
            and CORD_VALUE_MIN <= value <= CORD_VALUE_MAX)
    if not cord:
        return pixel
    recoloured = colorsys.hsv_to_rgb(BAND_HUE, BAND_SATURATION, value * BAND_VALUE_SCALE)
    return tuple(round(channel * CHANNEL_MAX) for channel in recoloured) + (alpha,)


def main():
    outer = Image.open(ITEMS / "outer_scroll.png").convert("RGBA")
    inner = Image.new("RGBA", outer.size)
    for y in range(outer.height):
        for x in range(outer.width):
            inner.putpixel((x, y), band(outer.getpixel((x, y)), x - y))
    inner.save(ITEMS / "inner_scroll.png")


if __name__ == "__main__":
    main()
