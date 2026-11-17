# 仓库协作约定

- 简体中文沟通、文档和函数内注释；遵守 `~/.codex/AGENTS.md` 的代码形状规则。
- `shared/` 保存唯一功法目录 textproto；`fabric/` 是 Java 模组：木人桩、拳师职业、打木桩处理器、拳师打桩行为、
  创造栏与 `/meridian debug villager_fist` 指令，以及数据包、语言、模型。
- 施放契约归功法库，拳脚打击、付费、做功归 Human，伤害结算归 Damage，生物动作播放归 PA Mobs；不复制其实现或协议。
- 依赖的附属都是硬依赖，不写「没装就跳过」的分支或测试。
- `gametest/` 只是验证装配：测试放在 Human 的 Fabric 包里，借包内入口准备身体，施放一律走 `TechniqueService`。
- 文档来源为 `docs/wiki/`。
- 未经授权不提交、不推送、不创建远端。

## 构建和测试

构建、测试一律走工作区的 `kz`（`~/Code/Kizuna-remote-session/scripts/kz`），不直接运行 `./gradlew`。细则以 `~/Code/Kizuna-remote-session/handoff/codex-worker-rules.md` 为准，动手前先读。

- 单测、构建和服务端 GameTest：`kz test ~/Code/<本仓的 worktree>`
- 要带上别的仓的 worktree 一起测：每个加一次 `--with ~/Code/<那个仓的 worktree>`
- 客户端测试：`kz client ~/Code/<本仓的 worktree>` 列出有哪些项，`kz client ~/Code/<本仓的 worktree> <项>` 跑其中一项
- 要自己写 Gradle 参数时：`kz gradle ~/Code/<本仓的 worktree> -- <参数>`
- 不提交、不推送；在自己的 worktree（`~/Code/wt-*`）里改，不改主目录。
