# Proposal

## Why

团队成员环境不一：有人不从事 Android 开发，本机没有 bash、Kotlin 或 Android SDK 环境。验证职责需要明确分层——Kotlin/Android 构建验证由 GitHub Actions 承担，开发者端侧不找 Kotlin 环境；而进入 CI 之前的提交前自检（静态检查与逻辑校验）目前缺失，需要一套任何机器用 `uv run` 就能跑的 Python 检查。项目已启用 OpenSpec spec-driven 流程，要求 task 分批次小步快跑、每一批都有明确可验证的验收点。

## What Changes

- 新增基于 `uv run` 的 Python 静态检查与逻辑校验脚本，隔离存放在 `verification/` 下的独立子目录，与现有验收报告（`BUILD_REPORT.md`、`DEVICE_CHECKLIST.md`、`screenshots/`）及 `scripts/` 构建准备脚本区分开；供开发者在端侧、进入 CI 之前执行，只需 Python + uv，不需要 Android/Kotlin 环境。
- CI（GitHub Actions）继续负责 Kotlin/Android 验证（核心测试、lint、构建），作为批次验收的权威；本变更不把 uv 检查塞进 CI。
- 约定 task 分批节奏：`tasks.md` 中的实现任务按可独立验证的小批次组织，每批以对应 CI 验证通过作为验收标准，而不是在开发者端侧找 Kotlin 环境验证。
- README 本次不改动：仅当出现需要通知用户的行为或约定变化时才同步（约定见 spec）。

## Capabilities

### New Capabilities

- `ci-verification`: CI 上 Kotlin/Android 验证的触发时机与执行内容，以及 task 批次以 CI 结果为验收的约定。
- `static-checks`: 开发端侧由 `uv run` 执行的 Python 静态检查与逻辑校验脚本的行为——存放位置隔离约定、运行方式、检查范围、失败报告与退出码语义。

### Modified Capabilities

（无——当前 `openspec/specs/` 尚无已有能力。）

## Impact

- `verification/`：新增隔离的检查脚本子目录（含 `pyproject.toml` 等 uv 项目文件），现有验收报告目录结构不变。
- `.github/workflows/`：[`android.yml`](../../../.github/workflows/android.yml) 保持 Kotlin 验证职责，仅按需要微调触发条件，不引入 uv 检查。
- `openspec/`：后续 change 的 `tasks.md` 采用分批小步快跑的组织方式。
- `README.md`：本次不改动；仅当出现需要通知用户的变化时才同步。
- 不改动 `app/`、`core/` 任何 Kotlin 源码与现有 Gradle 构建行为。