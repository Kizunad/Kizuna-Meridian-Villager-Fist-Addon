"""木人桩的造型：单位为 1/16 格，地面 y=0，木臂朝 -z（方块朝北时朝北）。

照咏春木人桩：八角立柱、上两臂外张、中一臂平伸、一条曲腿，底下两根斜交木脚。
立柱占 x/z 4..12、y 0..24，与方块碰撞箱一致；木臂和木腿伸出碰撞箱，只是外形。
每个部件最多绕一根轴转，角度只用 0/±22.5/±45，各版 Blockbench 都能原样打开编辑。
"""

from dataclasses import dataclass
from math import cos, radians, sin

TRUNK_MIN = 4
TRUNK_MAX = 12
TRUNK_TOP = 24
CHAMFER = 1
UPPER_ARM_Y = 17
UPPER_ARM_SPREAD = 22.5
MIDDLE_ARM_Y = 12
ARM_SECTION = 2
ARM_OUTSIDE = 10
ARM_INSIDE = 1.5
LEG_ROOT_Y = 6
LEG_DROP = -45
THIGH_OUTSIDE = 5.5
SHIN_BOTTOM = 1
FOOT_ANGLE = 45
FOOT_LENGTH = 18
FOOT_SECTION = 2


@dataclass(frozen=True)
class Box:
    group: str
    name: str
    lower: tuple[float, float, float]
    upper: tuple[float, float, float]
    material: str
    axis: str = "y"
    angle: float = 0
    pivot: tuple[float, float, float] | None = None
    hidden_faces: tuple[str, ...] = ()

    def center(self):
        return tuple((low + high) / 2 for low, high in zip(self.lower, self.upper))

    def corners(self):
        """输出旋转后顶点，检查实际伸出范围而非旋转前包围盒。"""
        pivot = self.pivot or self.center()
        first, second = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[self.axis]
        cosine = cos(radians(self.angle))
        sine = sin(radians(self.angle))
        for bits in range(8):
            corner = [self.upper[i] if bits & (1 << i) else self.lower[i] for i in range(3)]
            point = [corner[i] - pivot[i] for i in range(3)]
            rotated_first = cosine * point[first] - sine * point[second]
            rotated_second = sine * point[first] + cosine * point[second]
            point[first] = rotated_first
            point[second] = rotated_second
            yield tuple(point[i] + pivot[i] for i in range(3))


def trunk():
    """一根主方柱加前后两片窄板，四角各退一格，远看是八角圆木。"""
    inner_min = TRUNK_MIN + CHAMFER
    inner_max = TRUNK_MAX - CHAMFER
    return [
        Box("01_trunk", "trunk_core", (TRUNK_MIN, 0, inner_min), (TRUNK_MAX, TRUNK_TOP, inner_max), "trunk"),
        Box("01_trunk", "trunk_front", (inner_min, 0, TRUNK_MIN), (inner_max, TRUNK_TOP, inner_min), "trunk",
            hidden_faces=("south",)),
        Box("01_trunk", "trunk_back", (inner_min, 0, inner_max), (inner_max, TRUNK_TOP, TRUNK_MAX), "trunk",
            hidden_faces=("north",)),
    ]


def arm(name, root_x, root_y, axis, angle):
    """木臂从立柱前面伸出，根部埋进立柱一截，转动后根部不露缝。"""
    half = ARM_SECTION / 2
    lower = (root_x - half, root_y, TRUNK_MIN - ARM_OUTSIDE)
    upper = (root_x + half, root_y + ARM_SECTION, TRUNK_MIN + ARM_INSIDE)
    pivot = (root_x, root_y + half, TRUNK_MIN)
    return Box("02_arms", name, lower, upper, "arm", axis, angle, pivot, hidden_faces=("south",))


def arms():
    """上两臂左右外张，中臂居中平伸。"""
    left_root = TRUNK_MIN + CHAMFER + ARM_SECTION / 2
    right_root = TRUNK_MAX - CHAMFER - ARM_SECTION / 2
    middle_root = (TRUNK_MIN + TRUNK_MAX) / 2
    return [
        arm("upper_arm_west", left_root, UPPER_ARM_Y, "y", UPPER_ARM_SPREAD),
        arm("upper_arm_east", right_root, UPPER_ARM_Y, "y", -UPPER_ARM_SPREAD),
        arm("middle_arm", middle_root, MIDDLE_ARM_Y, "x", 0),
    ]


def leg():
    """大腿斜向前下伸，小腿在膝头处垂直落下，离地留一格缝。"""
    center_x = (TRUNK_MIN + TRUNK_MAX) / 2
    half = ARM_SECTION / 2
    pivot = (center_x, LEG_ROOT_Y + half, TRUNK_MIN)
    thigh = Box("03_leg", "thigh", (center_x - half, LEG_ROOT_Y, TRUNK_MIN - THIGH_OUTSIDE),
                (center_x + half, LEG_ROOT_Y + ARM_SECTION, TRUNK_MIN + ARM_INSIDE), "arm",
                "x", LEG_DROP, pivot, hidden_faces=("south",))
    knee_y, knee_z = knee_point(pivot)
    shin = Box("03_leg", "shin", (center_x - half, SHIN_BOTTOM, knee_z - half),
               (center_x + half, knee_y + half, knee_z + half), "arm", hidden_faces=("up",))
    return [thigh, shin]


def knee_point(pivot):
    """大腿末端中心点，小腿顶端接在这里。"""
    drop = radians(LEG_DROP)
    reach = THIGH_OUTSIDE - ARM_SECTION / 2
    return pivot[1] + sin(drop) * reach, pivot[2] - cos(drop) * reach


def feet():
    """两根木脚斜着交叉压在立柱底下，木臂正前方留空给木腿。"""
    center = (TRUNK_MIN + TRUNK_MAX) / 2
    half_length = FOOT_LENGTH / 2
    half_section = FOOT_SECTION / 2
    lower = (center - half_section, 0, center - half_length)
    upper = (center + half_section, FOOT_SECTION, center + half_length)
    pivot = (center, 0, center)
    return [
        Box("04_feet", "foot_northeast", lower, upper, "foot", "y", FOOT_ANGLE, pivot),
        Box("04_feet", "foot_northwest", lower, upper, "foot", "y", -FOOT_ANGLE, pivot),
    ]


def build():
    return trunk() + arms() + leg() + feet()
