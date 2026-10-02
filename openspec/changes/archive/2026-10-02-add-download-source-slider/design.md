# Design

## Context

现状：[`ModelCatalog.kt`](app/src/main/java/io/github/nahanhhan/lecturerecording/models/ModelCatalog.kt) 的 `ModelSpec.url` 以计算属性硬编码 GitHub Releases 地址；[`ModelDownloadService.kt`](app/src/main/java/io/github/nahanhhan/lecturerecording/models/ModelDownloadService.kt) 用 OkHttp 下载到 `filesDir/models/{id}.part`，以 `.etag` 保存 `If-Range` 值实现续传，完成后按 `sha256` 校验并解包白名单文件；[`SettingsScreen.kt`](app/src/main/java/io/github/nahanhhan/lecturerecording/ui/SettingsScreen.kt) 只有模型单选与下载按钮；[`SettingsStore.kt`](app/src/main/java/io/github/nahanhhan/lecturerecording/data/SettingsStore.kt) 用 SharedPreferences 存偏好。动机见 proposal.md。

调研结论（`verification/checks/probe_download_sources.py`，uv 运行，输出见 `verification/checks/probe_result.txt`，失败项 0）：

- 两个模型的魔塔直链 `https://modelscope.cn/models/{组织}/{仓库}/resolve/master/{归档}` 均返回 200，Range GET 返回 206，且 `Content-Range` 总字节与 GitHub 完全一致（AED `838589068`、CTC `520516278`）——同一归档的镜像，`sha256` 跨源共用成立。
- GitHub 直链 302 到 `release-assets.githubusercontent.com`，HEAD 带 `Accept-Ranges: bytes`，Range GET 返回 206。
- 魔塔源的 HEAD 响应不带 `Content-Length`/`Accept-Ranges`（实测 Range GET 却支持 206），因此续传能力必须以实际 Range 响应为准，不能依赖 HEAD 声明。

## Goals / Non-Goals

**Goals:**

- 下载源可在魔塔社区与 GitHub 间切换并持久化，默认 GitHub。
- 两源共用同一套下载、续传、校验、安装管线，仅 URL 与续传作用域不同。
- 切换源后绝不产生跨源续传或跨源校验。

**Non-Goals:**

- 不做多源并发/竞速下载，不做自动测速选源。
- 不改变模型目录结构、安装清单格式与已安装模型的判定方式。
- 不引入下载源的自定义配置界面（仓库地址写死在 `ModelCatalog`）。

## Decisions

**D1：切换控件用 Material3 `SingleChoiceSegmentedButtonRow` + `SegmentedButton` 两档，选中档高亮，整体为胶囊分段样式。**
最初按"滑块"实现了 `Slider` 两档，实际效果确认不是想要的——期望是图二那种分段切换控件。备选：`Slider` 两档（原方案，外观是滑杆、语义是连续量，已否决）、`Switch`（语义是开关，两端标签弱）、自绘胶囊切换（重复造轮子，无障碍语义需自行补全）。选 `SegmentedButton`：单选语义天然两档互斥，自带选中态与无障碍语义（`selected`/`onClick`）；`enabled` 由下载状态与录音状态共同控制（对应 spec「下载进行中的源切换保护」）。

**D2：下载源建模为 `enum class DownloadSource { GITHUB, MODELSCOPE }`，放在 `models` 包，`ModelSpec` 增加 `urlFor(source: String)`（或 `modelscopeRepo`/`modelscopeBranch` 字段 + 拼接函数）。**
备选 A：把完整 URL 存两份在 `ModelSpec` —— 冗余且易失配。备选 B：在 `ModelDownloadService` 里拼 URL —— 违反"目录拥有模型元数据"的现状，服务只该消费 URL。选：`ModelSpec` 持有 `modelscope`（`组织/仓库`）与 `modelscopeBranch`（默认 `master`），`urlFor(source)` 返回对应直链；GitHub 分支保持现有计算属性逻辑不变。偏好以字符串 `"github"`/`"modelscope"` 存 SharedPreferences（键 `download_source`，缺省 `github`），读取时映射到枚举，未知值回落 GitHub。

**D3：`.part`/`.etag` 文件名携带下载源，例如 `{id}.{source}.part`、`{id}.{source}.etag`。**
备选 A：单文件 + 记录"该文件属于哪个源"的元数据文件 —— 多一次 IO 且易遗漏清理。备选 B：切换源时直接删除旧 `.part` —— 用户来回切换会丢掉两边的进度。选 A 的文件名方案：源天然隔离，切换回来仍可续传，无需额外元数据；旧格式 `{id}.part`（升级前产生）在首次遇到时视为无主文件直接删除，避免用旧源数据续新源。

**D4：续传以服务端响应为准，不信任源的 HEAD 声明。**
发 `Range: bytes={offset}-` + `If-Range: {etag}`（仅当同源 `.etag` 存在）；响应 206 且 `Content-Range` 起点与 `offset` 一致 → 续传；响应 200 → 服务端忽略 Range，从 0 覆写；其余状态码 → 报错。这同时覆盖魔塔源可能不支持 Range 的情况，也让 GitHub/魔塔走完全相同的代码路径（现有 `ModelDownloadService.install` 已是此逻辑，只需把 `.part`/`.etag` 命名与 URL 换成按源取）。

**D5：`sha256` 校验跨源共用，不按源区分。**
两源是同一归档文件的镜像，`ModelSpec.sha256`/`bytes` 是文件的固有属性。若某源实际文件不一致，校验失败即删除并提示重新下载（spec「下载完整性校验与安装」），这是期望行为而非缺陷——它把"镜像不一致"暴露为可重试的错误。

**D6：下载源选择存 `SettingsStore`，下载服务启动时读取当前偏好。**
`ModelDownloadService.onStartCommand` 通过 `graph.settings.downloadSource` 取源并传入 `install`。备选：通过 Intent extra 传源 —— 服务被系统重启（`START_NOT_STICKY` 下不重启，但仍可能重投递）时 extra 与偏好可能不一致，以偏好为唯一事实来源更简单。

**D7：调研脚本独立于默认检查。**
`verification/checks/probe_download_sources.py` 不注册进 `checks/__main__.py` 的 `CHECKS`（网络探测不稳定、耗时，不适合提交前静态检查），以 `uv run python probe_download_sources.py` 手动运行；脚本硬性限制最多读 1KB 响应体、单请求 10s 超时、任何网络异常捕获后继续，保证快速终止。

## Risks / Trade-offs

- [魔塔直链可能不支持 Range 续传] → D4 以 206 响应为准，不支持时自动退化为整文件重下，不产生损坏数据；`.part` 按源隔离，切回 GitHub 后进度仍在。
- [魔塔仓库/分支未来被删或改名] → 下载报 HTTP 404 并展示状态码，用户可切回 GitHub；`master`/`main` 分支探测逻辑只在调研脚本中，运行时固定用 `master`（调研已确认可用）。
- [镜像文件与 GitHub 不一致] → 统一 `sha256` 校验拦截，删除临时文件并提示重试（D5）。
- [升级用户残留旧格式 `{id}.part`] → 首次按新命名下载时删除旧文件，代价是丢失一次未完成的进度（可接受：旧文件本就可能来自任一源，无法安全归属）。
- [切换源导致用户误以为进度丢失] → `.part` 按源保留，来回切换不丢进度；UI 无需额外提示。
- [HEAD 探测与真实 GET 行为不一致（调研已观察到）] → 设计不依赖 HEAD 头（D4），调研脚本同时做 Range GET 验证。

## Migration Plan

1. 发布含本变更的版本，`download_source` 缺省 `github`，行为与现状一致，无需数据迁移。
2. 旧 `{id}.part`/`{id}.etag` 在下一次下载启动时清理（D3）。
3. 回滚：切回旧版本即可，新命名的 `.part` 文件旧版本不识别，会重新下载；已安装模型不受影响。

## Open Questions

（无——魔塔仓库地址、分支、文件名已由用户确认，续传策略已由 D4 覆盖。）