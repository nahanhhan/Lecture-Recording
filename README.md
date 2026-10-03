# 课堂录音笔 · Lecture Recording

<img src="artwork/app-icon-preview.png" alt="课堂录音笔图标" width="96">

安卓课堂录音工具：录音和拍照 → 手机本地转写 → 手动云端整理 → 图文笔记 → PDF / Markdown。

当前版本为 **0.1.1-alpha**。首版代码已经实现；长时间后台稳定性、识别效果和真实云端往返仍需在目标手机上验收，不能将编译通过视为发布验收通过。

## 已实现

- 课堂列表、麦克风录音、暂停/继续、持续通知控制、异常后已保存音频恢复。
- 16 kHz 单声道 WAV 按 30 秒分文件写入，每秒更新文件头并同步落盘。
- 独立 `:asr` 进程中的 sherpa-onnx 1.12.27，默认 FireRedASR2 AED INT8，备用 CTC；最多 15 秒短段识别，临时结果合并以控制积压。
- App 内模型下载、HTTP Range 续传、固定 SHA-256 校验、安装状态及文件校验清单。
- CameraX 拍照，按音频样本计时，保留原图、拍摄日期、课堂内序号和固定 `ast_` 文件名。
- 段落/照片回听、原稿编辑、照片选择、云端来源版本快照。
- HTTPS Chat Completions 工具调用、严格结构与来源校验、数据库事务、批次幂等保存、工具回执和手动恢复。
- Android Keystore 加密保存 API Key；接口测试使用人工生成的数字图片，不发送课堂材料。
- 离线 Markdown、表格、代码和 KaTeX 公式阅读；Android 保存为 PDF；Markdown 与原图 ZIP 分享。
- 设置页最下方「日志」区块：级别三档 `None` / `Info` / `Debug`（默认 `None` 不记录；`Info` 记录关键事件并自动脱敏凭据；`Debug` 记录全部细节，会记录敏感信息，抓问题后请切回），显示当前日志占用，「清理日志」一键删除全部日志，「导出日志」把主进程与识别进程日志归并为单个 `.log` 经系统分享发出（可能含敏感信息，仅发给开发者排查问题）。

首版不提供账号、云同步、设备内录、音频导入、独立问答或整堂课二次识别。当前分段采用轻量音量阈值；嘈杂课堂、口音、耗电和实际延迟必须通过真机测试调整。

## 构建

需要 JDK 17 或 21、Android SDK platform 36、Build Tools 36.0.0 和 Python 3.10+。

```sh
python scripts/bootstrap.py --native
./gradlew :core:test :app:lintDebug :app:assembleDebug
```

Windows 使用 `gradlew.bat`。设置 `ANDROID_HOME`，或在未提交的 `local.properties` 中填写 `sdk.dir`。首次准备和构建需要访问 GitHub、Google Maven 和 Maven Central。

`bootstrap.py` 从固定官方发布版本提取 arm64 JNI 库并下载同版本 Kotlin 接口，验证原生包 SHA-256；这些生成文件不进入版本控制。Gradle Wrapper 固定为 8.13，并验证分发包 SHA-256。本机也可运行 `python scripts/bootstrap.py --gradle` 下载独立 Gradle。

KaTeX 静态文件已经随源码保存。重新获取时运行 `python scripts/fetch_math_assets.py`，脚本核对 npm 发布的完整性校验值。

GitHub Actions 自动执行核心测试、Android 静态检查和构建，APK 可在成功工作流的 `lecture-recording-debug` 构建产物中下载。

## 私有签名

个人长期安装使用自己的固定签名，后续更新保持同一密钥。在项目根目录创建未提交的 `keystore.properties`：

```properties
storeFile=/absolute/path/to/lecture-release.jks
storePassword=YOUR_LOCAL_PASSWORD
keyAlias=lecture
keyPassword=YOUR_LOCAL_PASSWORD
```

运行 `./gradlew :app:assembleRelease`。密钥与密码必须另行备份，不上传仓库；丢失密钥后无法覆盖安装已有版本。调试 APK 使用开发调试签名，不能覆盖不同签名的正式 APK。

也可以运行 `python scripts/create_signing_key.py` 生成固定本地密钥和配置。脚本保留已有签名配置，密码不输出到终端；将 `.tools/signing/lecture-release.jks` 和根目录 `keystore.properties` 一起私下备份。

## 手机上使用

1. 安装 arm64 APK，在设置中下载默认识别模型并确认安装完成。
2. 检查手机后台/电池设置，开始课堂录音，按需暂停、继续和拍照。
3. 结束后等待剩余实时段落完成。模型未准备好时音频仍保存，之后只处理未完成短段。
4. 配置支持读图和工具调用的云端服务，填写 HTTPS 基础地址、模型名称和 Key，再测试接口。
5. 核对原稿和照片，手动整理笔记，完成后编辑、回听和导出。

普通锁屏与切换页面由前台服务维持；系统强制停止、关机或权限撤销属于中断。再次打开会修复已写入 WAV 文件头、保留未完成任务，并由用户继续操作。

## 工程结构

| 路径 | 职责 |
| --- | --- |
| `app/` | 页面、录音、CameraX、Room、独立识别进程、下载、云端及导出 |
| `core/` | 时间/文件名规则、分段、分批、笔记校验、接口协议及 JVM 测试 |
| `docs/openai-tool-calling/` | 工具定义与完整离线往返示例 |
| `docs/ARCHITECTURE.md` | 数据与任务实现细节 |
| `verification/` | 已验证结果和真机验收清单 |
| `scripts/` | 固定依赖准备脚本 |

完整产品约定见 [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)。

## 第三方来源

- [sherpa-onnx 1.12.27](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.27)，Apache-2.0，许可证保存在 `third_party/`。
- [FireRedASR 模型说明](https://k2-fsa.github.io/sherpa/onnx/FireRedAsr/pretrained.html)，模型在用户设备按需下载。
- [KaTeX 0.16.22](https://github.com/KaTeX/KaTeX/releases/tag/v0.16.22)，MIT，许可证保存在 `third_party/`。
- [OpenAI Function Calling](https://developers.openai.com/api/docs/guides/function-calling)，本应用实现现有方案指定的 Chat Completions 适配器；所选服务和模型必须支持该端点。
