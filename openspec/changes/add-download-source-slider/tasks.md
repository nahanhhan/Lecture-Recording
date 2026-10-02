# Tasks

## 1. 下载源模型与偏好

- [x] 1.1 在 `models` 包新增 `DownloadSource` 枚举（`GITHUB`/`MODELSCOPE`）及字符串互转（未知值回落 `GITHUB`），并在 `SettingsStore` 增加 `downloadSource` 偏好（键 `download_source`，缺省 `github`）——验证：端侧 `cd verification/checks && uv run python -m checks` 通过（含下载源检查对回落逻辑与偏好键的断言）；Kotlin 编译由 CI 验证（按 ci-verification spec，端侧不要求 Kotlin 环境）
- [x] 1.2 `ModelSpec` 增加 `modelscope`（`组织/仓库`）与 `modelscopeBranch`（`master`）字段并为两个模型填入用户给定的仓库地址，新增 `urlFor(source)` 按源返回直链（GitHub 分支保持现有 Releases 地址）——验证：端侧 `uv run python -m checks` 断言 AED 魔塔 URL 等于 `https://modelscope.cn/models/adaada88/sherpa-onnx-fire-red-asr2-aed-zh-en-int8/resolve/master/sherpa-onnx-fire-red-asr2-zh_en-int8-2026-02-26.tar.bz2`、CTC GitHub URL 与现状一致；编译由 CI 验证

## 2. 下载服务按源工作

- [x] 2.1 `ModelDownloadService` 启动时从 `graph.settings.downloadSource` 取源并传入 `install`，`.part`/`.etag` 命名改为 `{id}.{source}.part`/`{id}.{source}.etag`，首次遇到旧格式 `{id}.part`/`{id}.etag` 时删除——验证：端侧 `uv run python -m checks` 通过（断言服务按源命名并清理旧格式）；编译由 CI 验证；文件行为在 4.3 真机验收
- [x] 2.2 `install` 改用 `model.urlFor(source)` 取下载地址，续传保持"206 且 Content-Range 起点匹配才续传、200 则从 0 覆写、其余报错"的自适应逻辑，不依赖 HEAD 头——验证：端侧 `uv run python -m checks` 通过（断言按源取 URL）；编译由 CI 验证；续传行为在 4.3 真机验收
- [x] 2.3 确认失败路径仍展示含 HTTP 状态码的可读错误且状态可重试（切换源后重新点下载即生效）——验证：手工让直链返回 404（改错仓库名临时验证）时界面显示「下载失败（HTTP 404）」，改回后可重新下载

## 3. 设置页下载源分段切换控件

- [x] 3.1 在「手机识别模型」区域加入 Material3 `SegmentedButton` 两档分段控件（每档标签「魔塔社区」「GitHub」，选中档高亮），初始值来自 `SettingsStore.downloadSource`，切换即持久化——验证：编译与 lint 由 CI 验证；真机切换后杀进程重启，控件选中档与后续下载源保持一致（4.3 验收）
- [x] 3.2 切换控件 `enabled` 联动：`download.status` 为 `downloading`/`verifying` 或 `recording.lessonId != null` 时禁用，结束/失败后恢复——验证：真机下载进行中切换控件不可操作，下载完成或失败后恢复可操作
- [x] 3.3 更新设置页说明文案，提示两源为同一模型镜像、校验一致——验证：真机查看文案与控件布局无重叠、中文显示正常

## 4. 验证与回归

- [x] 4.1 运行调研脚本确认两源直链与 Range 支持：`cd verification/checks && uv run python probe_download_sources.py`——验证：脚本在 2 分钟内结束，两个模型的 GitHub 与魔塔直链均返回 200/206，无 `[FAIL]`
- [x] 4.2 运行端侧静态检查 `cd verification/checks && uv run python -m checks`——验证：全部检查通过（端侧不要求 Kotlin 环境）；Kotlin 构建 `:core:test :app:lintDebug :app:assembleDebug` 由 CI 执行并给出结果
- [ ] 4.3 真机端到端：默认 GitHub 源完整下载并安装 AED 模型，切到魔塔源下载 CTC 模型，中途各暂停一次验证续传，安装后确认转写功能正常——验证：两个模型 `installed.json` 生成、状态显示「安装完成」、录音转写可用
- [x] 4.4 运行 `openspec validate --change "add-download-source-slider"` 并核对 spec 场景逐条可复现——验证：validate 通过，spec 中 6 条需求的场景均有对应实现或手工验证记录