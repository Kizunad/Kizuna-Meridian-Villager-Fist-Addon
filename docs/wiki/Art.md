# 袖手拳美术

2026-11-14 补齐上卷「外家」的卷轴物品图标、阅读背景和四式战斗图标，并重做木人桩的模型。此前：

- 启动日志报 `meridian_villager:outer_scroll` 缺少贴图、使用功法库通用卷轴图标；
- 四式在战斗槽和功法配置窗口里没有图标；
- 木人桩直接贴原版橡木原木整张图，24 高的面贴图越界被拉花，只有三根短棍，没有腿。

## 图标和卷轴

### 来源

- 全部主体由用户 srv 上的 CPA 生成：模型 `gpt-image-2.5-sunburst`，图片接口，`quality=high`、PNG。
- 调用脚本读 `~/.config/kizuna/cpa-image.env`，日志不打印 key。
- 原稿放仓内 `.local-art/{scroll,background,icon}/<名字>_v<版本>.png`，已加进 `.gitignore`，不进 JAR。
- 逐图提示词在 [art-prompts/](art-prompts/)。

### 成品

资源命名空间 `meridian_villager`，放 `fabric/src/main/resources/assets/meridian_villager/textures/`。路径由功法库约定：

- 卷轴物品 ID 是 `<卷>_scroll`；
- 阅读背景按卷路径取；
- 战斗图标按 `icon_id` 取 `gui/technique/<路径>.png`。

| 卷 | 32×32 卷轴物品图 | 1536×1024 阅读背景 |
|---|---|---|
| 上卷「外家」 | `item/outer_scroll.png` | `gui/scroll/outer.png` |

四式共用一套边框：近黑褐外圈、草绳金内圈、深褐底。各式靠动作剪影区分。

| 式 | icon_id | 主体 | 32×32 | 64×64 |
|---|---|---|---|---|
| 崩拳 | `meridian_villager:crushing_fist` | 直冲的拳头，拳前迸出冲击线 | `gui/technique/crushing_fist.png` | `crushing_fist_64.png` |
| 撩天 | `meridian_villager:golem_lift` | 双掌上托，把石块掀到半空 | `gui/technique/golem_lift.png` | `golem_lift_64.png` |
| 背摔 | `meridian_villager:shoulder_throw` | 拳师把人从肩上摔过去 | `gui/technique/shoulder_throw.png` | `shoulder_throw_64.png` |
| 打木桩 | `meridian_villager:dummy_drill` | 拳头打在木人桩上 | `gui/technique/dummy_drill.png` | `dummy_drill_64.png` |

### 后处理

脚本是 [scripts/process_art.py](../../scripts/process_art.py)，照行药诀那份改的，成品全由原稿和脚本决定，不手修。

- **卷轴**：去品红底，裁去透明边，最近邻缩到 30×30 以内，量化为 32 色，居中放进 32×32，alpha 只有 0 和 255。
- **阅读背景**：保持 1536×1024。
- **战斗图标**：最近邻缩成 64×64；暗底统一成本书底色 `#220C07`，量化为 32 色；重画两圈边框，外圈 `#160605`、内圈 `#E8C66A`，颜色取自原稿实际画出的边框。64×64 每圈宽 2 像素，缩成 32×32 后每圈宽 1 像素。

当前成品全部来自 v1 原稿，复现命令是 `python3 scripts/process_art.py v1`。

## 木人桩

作者稿在 [authoring/wooden_dummy/](../../authoring/wooden_dummy/)，说明见那里的 README。

- 造型照咏春木人桩：八角立柱、上两臂外张、中一臂平伸、一条曲腿，底下两根斜交木脚，共 10 个立方体。
- 贴图是程序画的 64×64 像素木纹，只借原版木头的色调。
- 方块 ID、方块状态、物品模型、碰撞箱都没动，所以拳师的工作站点和「打木桩」认的目标照旧。
- 重新生成：`python3 authoring/wooden_dummy/generate.py`。

## 校验

`verification` 的 `VillagerFistArtTest`：

- 每卷有 32×32 卷轴物品图和 1536×1024 阅读背景；
- 每式的 `icon_id` 与式 ID 同名，32×32 和 64×64 图标都能解码；
- 木人桩模型只引用本仓的 64×64 木纹图，10 个元素的坐标和转角都在原版方块模型允许的范围里，方块状态四个朝向都指向它。
