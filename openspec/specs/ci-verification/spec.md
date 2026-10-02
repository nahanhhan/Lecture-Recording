# ci-verification Specification

## Purpose

让 Kotlin/Android 工程验证由 CI 统一承担，使没有 bash、Kotlin 或 Android SDK 环境的团队成员无需在端侧搭建环境，仅凭 CI 结果即可判断变更是否通过。

## Requirements

### Requirement: 变更推送触发 CI 验证
系统 SHALL 在推送到主干分支或更新拉取请求时自动触发 CI 验证，不要求开发者在端侧执行命令。

#### Scenario: 推送或更新拉取请求
- **WHEN** 代码推送到 `main` 或拉取请求被更新
- **THEN** CI 自动运行验证并给出通过/失败结果

### Requirement: CI 承担 Kotlin/Android 验证
CI SHALL 执行核心单元测试、Android lint 与调试包构建并上传测试报告与 APK 产物；Kotlin/Android 验证 MUST 由 CI 完成，不要求开发者端侧具备相应环境。

#### Scenario: CI 执行构建验证
- **WHEN** CI 验证运行
- **THEN** 执行 `:core:test`、`:app:lintDebug`、`:app:assembleDebug` 并上传产物

#### Scenario: 端侧无 Kotlin 环境
- **WHEN** 开发者本机未安装 Kotlin 或 Android SDK
- **THEN** 仍可通过 CI 结果获得完整的 Kotlin/Android 验证结论

### Requirement: task 批次以 CI 结果为验收
实现任务 SHALL 按可独立验证的小批次组织，每批以对应 CI 通过作为验收；MUST NOT 要求验收者本机具备 Kotlin 或 Android 环境。

#### Scenario: 批次验收
- **WHEN** 某批次任务完成且对应 CI 通过
- **THEN** 该批次视为验收通过，可继续下一批次

### Requirement: CI 结果不等同于发布验收
CI 结果 SHALL 仅表述为工程验证（编译、测试、lint）；系统 MUST NOT 将 CI 通过呈现为真机发布验收通过，真机验收仍按既有设备清单人工执行。

#### Scenario: 结果表述边界
- **WHEN** CI 全部通过
- **THEN** 结果仅标记为工程验证通过，真机验收状态仍以设备清单为准