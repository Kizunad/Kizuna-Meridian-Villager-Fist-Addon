# 袖手拳

村里拳师传下来的拳法。本仓现在有：

- 功法目录 `data/meridian_villager/technique_catalog.txtpb`：书《袖手拳》，上卷「外家」三式，见 [Techniques](Techniques.md)。图标、卷轴贴图和木人桩模型的来历见 [Art](Art.md)。
  中卷、下卷做好再加卷。
- 木人桩方块 `meridian_villager:wooden_dummy`：一格占地，碰撞高 1.5 格，斧头挖得快，能烧。
  配方和拳师的交易表在数据包里。
- 拳师职业 `meridian_villager:fist_master`：无业村民认领木人桩就成了拳师，工作声是木头受击。
  上班时站到桩前循环打桩动作（PA Mobs 的 `villager_dummy_drill`），每 0.5 秒桩上响一下；只是表演，不练功。
- 打木桩处理器 `meridian_villager:dummy_drill`（功法库 `block_work` 的处理器）。
- 创造栏「袖手拳」：木人桩和上卷卷轴。
- 调试指令 `/meridian debug villager_fist target`：看执行者现在对着哪根桩，认法和打木桩起式时一样。

## 依赖

硬依赖：功法库、Framework、Human、Vanilla Damage、PA Mobs、训练假人。没有「未安装时」的退路。

## 村民会还手

本仓往 Threatening 的学卷表登记：村民游炁、真炁两档都学上卷（概率 1.0）。
原版村民没有攻击手段，学了崩拳、撩天、背摔以后，类人大脑照「打得过就打」评估，被打或被怪盯上会迎上去，打不过照旧躲。

- 登记写在 `data/meridian_villager/humanoid_volumes.toml`，由 `fabric.mod.json` 的 `custom` 键 `meridian_humanoid:volumes` 指过去；Threatening 启动时把它接在自己配置文件同一档的后面，不依赖本仓。
- 运营方想改概率，在 `meridian-humanoid-brain.toml` 的村民那一档写同一卷即可，照配置文件的算。
- 功法谱的来源展示读的也是 Threatening 并好的那张表，本仓不用另外登记。

## 打木桩认哪些桩

视线从眼睛往前最多 2 格，先撞到的那样东西是桩才算：

| 种类 | 标签 | 现在的内容 |
|---|---|---|
| 方块 | `#meridian_villager:drill_targets` | 木人桩、`#minecraft:logs`、干草块 |
| 实体 | `#meridian_villager:drill_targets`（实体类型） | 盔甲架、玩家模型、训练假人 |

第一下锁定那根桩；之后每 0.5 秒查桩还在、播它的受击声（方块用自己的受击声，实体按木头）。
起式时没桩，动作栏提示「要对着木桩打」，施放以 `EFFECT_REJECTED` 结束；打到一半桩被挖掉、打掉或捡走，也这样结束。
桩不受伤。

## 验证

```
./gradlew --no-daemon test build :gametest:runGametest -PgametestRunDir=<新目录>
```

- `verification`：目录（一书一卷三式、掉落、槽位、出拳时刻和方式、打木桩事件与训练做功）、数据包 JSON、
  伤害类型和标签、打桩目标标签、中英语言键。
- `fabric` 单测：目录里的声音都是原版事件。
- `gametest`：打木桩没桩拒绝、六种桩都认、挖掉桩中断、和劈柴练进同一组手臂肌肉；崩拳、撩天的出拳 tick 和
  撩天竖直速度（击退抗性 0 和 0.5）；无业村民认领木人桩成为拳师、上班时循环打桩动作。
