# Design

## Context

- 验证职责分两层，前后衔接：**端侧 `uv run`（Python，提交前自检）→ CI action（Kotlin/Android 构建验证）**。uv 是开发时端侧工具，Kotlin 验证才是 GitHub Actions 的职责，两者不混放。
- 现有 [`.github/workflows/android.yml`](../../../.github/workflows/android.yml) 已承担 Kotlin 侧：装 JDK/Android SDK → `bootstrap.py` → `:core:test :app:lintDebug :app:assembleDebug` → 上传产物。本变更不改其职责。
- `verification/` 目前只存放验收报告（`BUILD_REPORT.md`、`DEVICE_CHECKLIST.md`、`screenshots/`）；`scripts/` 存放构建准备脚本；仓库无 uv 项目文件。
- [`IMPLEMENTATION_PLAN.md`](../../../IMPLEMENTATION_PLAN.md) 处于「编译验证 + 真机验收」阶段，其第 5 节真机验收矩阵由人工按设备清单执行，不进 CI；其「大而全」的写法是刻意要克制的参照，本变更的检查项只取最小集。
- 团队约束：部分成员不从事 Android 开发，本机无 bash/Kotlin/Android SDK——他们端侧能跑的只有 Python 检查，Kotlin 验证完全依赖 CI。动机详见 proposal.md - Why。

## Goals / Non-Goals

**Goals:**

- 端侧提交前自检：`uv run` 一条命令跑完静态检查与逻辑校验，无需 Android/Kotlin 环境。
- 检查脚本隔离在 `verification/` 子目录，与验收报告、构建脚本分离。
- CI 保持 Kotlin/Android 验证职责，作为 task 批次的权威验收。
- `tasks.md` 分批小步快跑，每批以 CI 通过为验收。

**Non-Goals:**

- 不把 `uv run` 检查接入 CI——CI 只负责 Kotlin/Android；如后续需要另行提出。
- 不改动 `app/`、`core/` 的 Kotlin 源码与 Gradle 构建行为。
- 不把真机验收自动化进 CI——CI 通过只代表工程验证，真机验收仍走 `DEVICE_CHECKLIST.md`。
- 不建检查框架、不铺开检查项矩阵：最小可用集起步，按实际失败驱动增补（对 PLAN 式过度设计的刻意克制）。

## Decisions

1. **职责分层：uv 归端侧、Kotlin 归 CI**。
   - 理由：无 Android 环境的成员端侧只需 Python 即可完成有意义的自检；Kotlin 构建重、依赖 SDK，交给 CI 统一执行，避免团队成员各自找环境。
   - 备选：uv 检查也进 CI——职责重叠、增加 runner 时间，且端侧自检价值在于「推送前」而非「推送后」。

2. **检查放 `verification/checks/`，uv 项目文件局部在该目录**。
   - 理由：用户要求与验收报告隔离；`scripts/` 语义是「固定依赖准备」，检查是验证职责，不混放；局部 `pyproject.toml` 不污染 Gradle 根工程。
   - 备选：根目录 `pyproject.toml`——让纯 Android 成员多一个无关顶层文件。

3. **首批检查项取最小集**：版本号（`versionName`）与 README 一致性、JSON 资产（`tool-definition.json` 与 `docs/openai-tool-calling/` 示例）可解析且一致。OpenSpec 工件结构校验交给 `openspec validate` 等官方命令，不重复实现。
   - 理由：Python 标准库即可实现、毫秒级、对应已存在的漂移风险（版本号散落多处文档）；OpenSpec 结构已有官方命令，自写检查是重复造轮子。
   - 备选：PLAN 式全量校验矩阵——维护成本高、易与代码漂移，明确不做。

4. **task 分批约定写进 tasks.md 结构**：每批 2–4 个任务、单批对应一次可辨识的 CI 结果，批间可独立回滚。
   - 理由：小步快跑靠粒度而非流程仪式，不引入批次管理工具。

5. **CI workflow 基本不动**：仅在需要时微调触发条件，不向 `android.yml` 添加 uv 步骤。
   - 理由：现有 workflow 已满足 Kotlin 验证职责，改动越少回滚越容易。

## Risks / Trade-offs

- [端侧自检靠成员自觉执行] → 文档明确「推送前 `uv run`」入口；CI 仍兜底 Kotlin 验证，检查缺失不产生错误发布。
- [检查项随代码演进漂移] → 检查只读、失败驱动增补；不追求覆盖全量。
- [无 uv 的成员] → uv 安装为单二进制；即便不装，CI 的 Kotlin 验证仍是批次验收权威，不被阻塞。
- [检查过严误报] → 首批仅三类低误报检查；误报时修检查而非绕过。

## Migration Plan

1. 新建 `verification/checks/`（uv 项目 + 检查脚本），端侧 `uv run` 跑通。
2. `android.yml` 按需微调触发条件，职责不变。
3. 同步 README 工程结构表与验证说明（端侧自检入口 + CI 验收语义）。
4. 回滚：删除 `verification/checks/` 即可，CI 无行为变化。

## Open Questions

- 首批检查项之外的增补（如 Kotlin 源文件风格扫描）留待实际失败驱动，不影响本 spec 与任务拆分。