# Tasks

## 1. 端侧检查脚手架

- [x] 1.1 创建 `verification/checks/` 目录与局部 uv 项目文件（`pyproject.toml`），验证：进入该目录执行 `uv run` 可运行且退出码为 0，仓库根目录无新增顶层配置文件
- [x] 1.2 实现检查入口骨架（顺序执行各检查、汇总输出、统一退出码语义），验证：`uv run` 输出通过摘要且退出码 0，在未安装 Android SDK/JDK/Kotlin 的环境可完整执行

## 2. 最小检查集实现

- [x] 2.1 实现版本一致性检查（`app/build.gradle.kts` 的 `versionName` 与 README 声明版本），验证：当前仓库运行通过；临时修改 README 版本号后运行失败并同时指出两个文件，恢复后重新通过
- [x] 2.2 实现 JSON 资产检查（`app/src/main/assets/tool-definition.json` 与 `docs/openai-tool-calling/` 示例可解析且工具定义一致），验证：当前仓库运行通过；临时破坏一个 JSON 后运行失败并指出该文件，恢复后重新通过

## 3. CI 职责确认与文档同步

- [x] 3.1 确认 `android.yml` 保持 Kotlin/Android 验证职责且不含 uv 步骤，仅按需要微调触发条件，验证：workflow 语法有效，push/PR 触发后 `:core:test :app:lintDebug :app:assembleDebug` 正常执行

## 4. 集成验收

- [ ] 4.1 端到端走一遍流程：全新检出 → `verification/checks/` 下 `uv run` 全部通过 → 推送后 CI Kotlin 验证通过，验证：两端结果均记录于本变更目录
- [x] 4.2 运行 `openspec validate --change "add-ci-verification-pipeline"`，验证：校验通过无错误