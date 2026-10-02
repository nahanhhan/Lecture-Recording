# Proposal

## Why

设置页的模型下载目前只有 GitHub Releases 一个来源，国内网络下直连慢、易中断，用户没有选择余地。增加魔塔社区（ModelScope）镜像源并提供两档分段切换控件，让用户按当前网络环境选择更快、更稳的下载源。

## What Changes

- 设置页「手机识别模型」区域新增**下载源分段切换控件**（两段式，选中档高亮），在「魔塔社区」与「GitHub」两档间切换；选择持久化到 SharedPreferences，默认保持 GitHub（现状不变）。
- [`ModelCatalog.kt`](app/src/main/java/io/github/nahanhhan/lecturerecording/models/ModelCatalog.kt) 的 `ModelSpec` 增加魔塔社区直链信息（组织/仓库、分支、归档文件名），按 `https://modelscope.cn/models/{组织}/{仓库}/resolve/{分支}/{文件路径}` 拼接 URL；GitHub URL 保持现状。
- [`ModelDownloadService.kt`](app/src/main/java/io/github/nahanhhan/lecturerecording/models/ModelDownloadService.kt) 按当前选中的下载源取 URL；断点续传状态与下载源绑定，切换源后不复用旧源的 `.part`/ETag，避免跨源续传位置或内容不一致。
- 两源下载的是同一归档文件，下载完成后仍按 `ModelSpec.sha256` 统一校验，校验通过才安装。
- 新增调研脚本 [`probe_download_sources.py`](verification/checks/probe_download_sources.py)（uv 运行），探测两源直链的状态码、`Content-Length`、`Accept-Ranges` 与 Range 续传支持，作为直链可用性的验证依据。

## Capabilities

### New Capabilities

- `model-download`: 模型下载源选择与下载行为——下载源偏好及其持久化、按源构造下载 URL、断点续传与源的绑定关系、下载校验与安装、源不可用时的错误反馈。

### Modified Capabilities

（无——现有 `ci-verification`、`static-checks` 能力的验收要求不因本变更改变。）

## Impact

- **代码**：[`ModelCatalog.kt`](app/src/main/java/io/github/nahanhhan/lecturerecording/models/ModelCatalog.kt)（`ModelSpec` 增加 ModelScope 源字段与按源取 URL）、[`ModelDownloadService.kt`](app/src/main/java/io/github/nahanhhan/lecturerecording/models/ModelDownloadService.kt)（按源下载、续传与源绑定）、[`SettingsScreen.kt`](app/src/main/java/io/github/nahanhhan/lecturerecording/ui/SettingsScreen.kt)（下载源分段切换控件 UI）、[`SettingsStore.kt`](app/src/main/java/io/github/nahanhhan/lecturerecording/data/SettingsStore.kt)（新增下载源偏好）。
- **数据/文件**：`filesDir/models` 下的 `.part`、`.etag` 命名随下载源变化；已安装模型不受影响。
- **验证**：`verification/checks/probe_download_sources.py`（网络探测，独立运行，不进默认静态检查）。
- **兼容性**：默认源仍为 GitHub，升级后行为与现状一致；已安装模型无需重新下载。