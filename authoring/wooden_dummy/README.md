# 木人桩作者稿

照咏春木人桩：八角立柱、上两臂外张、中一臂平伸、一条曲腿，底下两根斜交木脚。共 10 个立方体。

| 文件 | 内容 |
|---|---|
| `model.py` | 造型：每个立方体的位置、大小、转角。单位 1/16 格，地面 y=0，木臂朝 −z |
| `generate.py` | 画贴图、写 `WoodenDummy.bbmodel` 和原版方块模型 |
| `preview.py` | 离线渲染主视图 `preview.png` 和三视图 `views.png` |
| `WoodenDummy.bbmodel` | 自带贴图的 Blockbench 文件，可以直接打开改 |
| `wooden_dummy.png` | 64×64 贴图，和运行时那张相同 |

## 重新生成

```
python3 authoring/wooden_dummy/generate.py
python3 authoring/wooden_dummy/preview.py
```

`generate.py` 只依赖 Pillow，除本目录外还会写两处运行时资源：

- `fabric/src/main/resources/assets/meridian_villager/textures/block/wooden_dummy.png`
- `fabric/src/main/resources/assets/meridian_villager/models/block/wooden_dummy.json`

`preview.py` 另外要已安装的 `bbmodel_maker`。

## 约束

- 立柱占 x/z 4..12、y 0..24，和方块碰撞箱 `WoodenDummyBlock.POST` 一致；木臂、木腿、木脚伸出碰撞箱，只是外形。
- 每个立方体最多绕一根轴转，角度只用 0、±22.5、±45，各版 Blockbench 都能原样打开。
- 顶点都在原版方块模型允许的 −16..32 以内。旋转后外包范围约 x 0.9..15.1、y 0..24、z −6..15.1。
- 方块状态、物品模型、方块 ID 都没动：朝北时木臂朝北，放下时木臂朝着放的人。

## 贴图

程序画的像素木纹，每个模型单位 1 像素；只借原版木头的色调，没有拷原版像素。随机数种子固定，每次生成结果相同。

- 立柱：云杉调，竖纹，左右两列压暗显出圆角；正面在木臂和木腿根部画暗色榫眼；顶面画年轮。
- 木臂、木腿：浅暖木，纹理顺着长度走。
- 木脚：深橡木。

在 Blockbench 里改了造型以后，要把改动抄回 `model.py`，否则下次生成会盖掉。
