# 仓库协作约定

- 简体中文沟通、文档和函数内注释；遵守 `~/.codex/AGENTS.md` 的代码形状规则。
- `shared/` 保存唯一功法目录 textproto；`fabric/` 是 Java 模组：木人桩、拳师职业、打木桩处理器、拳师打桩行为、
  创造栏与 `/meridian debug villager_fist` 指令，以及数据包、语言、模型。
- 施放契约归功法库，拳脚打击、付费、做功归 Human，伤害结算归 Damage，生物动作播放归 PA Mobs；不复制其实现或协议。
- 依赖的附属都是硬依赖，不写「没装就跳过」的分支或测试。
- `gametest/` 只是验证装配：测试放在 Human 的 Fabric 包里，借包内入口准备身体，施放一律走 `TechniqueService`。
- 文档来源为 `docs/wiki/`。Gradle 一律经 `~/Code/Kizuna-remote-session/scripts/kz-gradle-lock` 并带 `--no-daemon`。
- 未经授权不提交、不推送、不创建远端。
