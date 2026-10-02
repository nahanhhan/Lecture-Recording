# Spec Delta

## Purpose

提供一套开发端侧在进入 CI 之前用 `uv run` 单命令即可执行的静态检查与逻辑校验，隔离存放在 `verification/` 下，任何机器（含无 Android/Kotlin 环境）都能完成提交前自检。

## ADDED Requirements

### Requirement: 检查脚本隔离存放于 verification 子目录
检查脚本及其 uv 项目文件 SHALL 存放在 `verification/` 下的独立子目录，与既有验收报告及 `scripts/` 下的构建准备脚本分离。

#### Scenario: 目录隔离
- **WHEN** 查看 `verification/` 目录
- **THEN** 检查脚本位于独立子目录，既有验收报告保持原位且不被覆盖

### Requirement: 开发端侧通过 uv run 单命令执行
检查 SHALL 在开发端侧、进入 CI 之前通过 `uv run` 一条命令全部执行，依赖由 uv 项目文件声明；执行前置 MUST NOT 包含 Android SDK、JDK 或 Kotlin。

#### Scenario: 无 Android 环境自检
- **WHEN** 在未安装 Android SDK、JDK、Kotlin 的机器上进入检查目录执行 `uv run`
- **THEN** 全部检查正常执行并输出结果，作为提交前自检依据

#### Scenario: 提交前自检失败
- **WHEN** 端侧 `uv run` 检查未通过
- **THEN** 开发者在推送进入 CI 之前即获得失败项与涉及文件

### Requirement: 覆盖最小检查集
检查 SHALL 至少覆盖：版本号与文档表述一致性、JSON 资产可解析且相互一致。检查项 MUST 保持最小可用集，按需增补；OpenSpec 工件结构校验由 `openspec` 官方命令承担，MUST NOT 重复实现。

#### Scenario: 版本号与文档不一致
- **WHEN** `app/build.gradle.kts` 的 `versionName` 与 README 声明的版本不一致
- **THEN** 检查失败并指出两个不一致的文件

#### Scenario: JSON 资产损坏或不一致
- **WHEN** 工具定义或文档示例 JSON 无法解析，或两处工具定义不一致
- **THEN** 检查失败并指出问题文件

### Requirement: 退出码与失败报告
任一检查失败时 SHALL 以非零退出码结束并输出失败检查项与涉及文件；全部通过时 SHALL 以零退出码结束。

#### Scenario: 失败反馈
- **WHEN** 任一检查未通过
- **THEN** 退出码非零，输出包含失败检查名称与文件路径

#### Scenario: 全部通过
- **WHEN** 全部检查通过
- **THEN** 退出码为零

### Requirement: 检查过程只读
检查 SHALL 只读取仓库内容，MUST NOT 修改任何被检查的文件。

#### Scenario: 工作区无变化
- **WHEN** 检查执行完成（无论结果）
- **THEN** 仓库工作区与执行前一致