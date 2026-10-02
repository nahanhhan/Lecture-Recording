# 安卓 AI 课堂录音笔首版方案

更新日期：2026-10-02。状态：首版安卓代码已实现，进入编译验证及真机验收阶段；实现与使用说明见 README.md。

## 1. 已确定的产品流程

录音、拍照 → 手机本地实时转写 → 手动调用云端模型整理 → 图文课堂笔记 → PDF / Markdown 导出。

- 面向人工智能、数据结构、操作系统等课程，处理中英文混说、专业术语、带口音英语和带地方口音的普通话。
- 目标手机为 Redmi K80 至尊版（16GB、天玑 9400+）和 OnePlus Ace 5；兼顾识别效果和系统稳定性。
- 首版为个人安装 APK，支持麦克风录音、App 内拍照、暂停/继续、文字编辑、回听、记录管理和图文笔记导出。
- 首版不加入账号、云同步、设备内录、音频文件导入、独立问答及二次识别。
- 录音结束且实时识别队列完成后，由用户手动整理。云端同时读取转写文字和选定照片，只整理课堂来源内容，不额外增加讲解、知识点或例子。
- 后台、锁屏状态下持续保存录音并完成实时转写；已由用户启动的云端整理任务也能在后台继续。将长时间后台稳定性作为发布验收要求。

## 2. 手机实现

使用 Kotlin + Jetpack Compose 原生安卓应用；AudioRecord 录音，CameraX 拍照，Media3 回听，Room 保存记录。仅构建 arm64-v8a；最低 Android 10，编译/目标 Android 16；JDK 17、AGP 8.13.2、Gradle 8.13。

页面包括录音列表、录音页面、记录详情（原始转写/笔记）和设置（模型、云端 API、课程与术语）。音频分文件持续写入，原图、转写及笔记均留存在手机。

实时后端使用 sherpa-onnx 1.12.27，默认 FireRedASR2-AED 完整版 INT8，备用 FireRedASR2-CTC。两种模型独立配置，录音过程中不自动切换。默认 CPU 四个识别线程，不依赖厂商专用加速。

输入采用 16kHz 单声道。按语音停顿分段，约每两秒更新当前段的临时文字，停顿后固定；连续讲话最长十五秒切段。出字延迟 2–5 秒作为真机测试目标，不作为已验证的性能承诺。

模型在 App 内下载，支持进度、续传、校验和安装状态。录音保存、识别和云端整理独立运行；模型未准备好或识别失败仍保存录音。停止时只处理剩余实时任务，不重新识别整节课。

### 后台、锁屏与异常恢复

- 录音由 `microphone` 类型前台服务持有，声明 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MICROPHONE` 和 `RECORD_AUDIO`；在用户可见页面获得权限并开始录音，然后允许切换到后台或锁屏。服务及时显示持续通知，提供暂停、继续和停止入口。[安卓麦克风前台服务](https://developer.android.com/develop/background-work/services/fgs/service-types)
- 录音、音频写入和任务状态不依赖 Activity 生命周期；录音服务设置 `stopWithTask=false`，页面关闭、重建、旋转或锁屏不结束录音。相机只在用户打开的拍照页面使用，返回后继续同一条录音。
- sherpa-onnx 在独立绑定进程 `:asr` 中运行，录音服务继续持有麦克风并写入音频。模型进程崩溃或被回收时，录音仍保存；仅恢复未完成的识别任务，已固定段落不重新识别。
- 正在录音或实际处理待完成音频时使用十分钟超时的 `PARTIAL_WAKE_LOCK`，每五分钟按任务状态续租；任务停止、暂停且队列已排空、取消或发生错误时释放，不点亮屏幕。
- 提供后台运行状态页和系统电池设置入口，分别核对澎湃 OS、ColorOS 的后台限制和电池优化状态。Doze 可能限制网络或忽略普通唤醒锁，不能仅凭一个唤醒锁声明后台已得到保障。[Doze 行为](https://developer.android.com/training/monitoring-device-state/doze-standby)
- 用户手动启动云端整理时，由独立 `dataSync` 类型前台服务执行上传、工具调用、保存和结果回传；状态、调用编号、批次与已完成结果持续落盘。网络暂停时保留进度；系统时限到达时保存状态并及时停止服务，进入等待恢复，避免超时造成 ANR。[前台服务时限](https://developer.android.com/develop/background-work/services/fgs/timeout)
- 普通后台切换和锁屏必须持续工作；系统强制停止、关机、权限撤销或严重资源回收属于中断处理场景。重新打开时恢复已写入资料、标明中断位置，由用户继续录音，不尝试绕过系统的停止操作。[系统停止行为](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)

## 3. 图片和回听

照片绑定按下拍照按钮时的录音时间；不使用照片写入或转写返回时间。文字未出现时先保存图片标记，随后按时间关联。

照片命名固定为 `ast_<录音相对时间毫秒>_<拍摄序号>.jpg`：时间补足九位、序号补足三位，超出位数时保留完整数字。例如 `ast_000075123_001.jpg` 表示录音进行到 75.123 秒时拍摄的第 1 张照片。`ast_` 是本应用的固定前缀，不表示 AST 时区。

- 相对时间按已采集音频的样本数换算，暂停时不增长；同时另外保存实际拍摄的 Unix 毫秒时间 `capturedAtEpochMs`，供日期显示与追溯。
- 拍摄序号在同一课堂内从 1 递增并持久化，照片按课堂分目录保存。使用排他文件创建检查重名，冲突时递增序号，避免暂停拍照、连拍或恢复任务时覆盖原图。
- 图片写入成功后再提交照片记录；生成转写、云端整理和人工修改时不改文件名、照片编号或录音时间。
- 整理请求明确提供照片编号 `photos[].id`、`filename`、`audio_time_ms`、可读时间和对应图片，而不依赖云端服务自动获知上传前的文件名。模型按这些资料选择 `photo_ids`，App 根据本地映射插入照片和执行回听。
- Markdown 图片包保留上述文件名；笔记显示和排序仍使用保存的时间、序号与照片编号，不凭模型生成的文字解析位置。

首版按短段文字定位：照片显示在覆盖拍照时间的段落旁；静音时的照片插入相邻段落之间。点击段落从该段起点回听，照片提供自身时间的回听入口。人工编辑文字不改变段落编号和时间。

当前 AED 转换版没有直接提供逐字时间，因此不承诺逐字点击定位。专业术语词表用于云端理解来源及人工编辑帮助，不承诺 FireRed 的额外热词注入。

## 4. 云端：OpenAI API v1 Tool Calling

按 [Tool Calling 接口约定](docs/openai-tool-calling/README.md) 实现云端接口，替换此前普通正文/普通 JSON 返回笔记的设计。

- 使用自定义 HTTPS 基础地址加 `/chat/completions`，兼容 OpenAI Chat Completions；用户配置模型名与 API Key。API Key 使用 Android Keystore 加密保存，不进入日志、导出或模型资料。
- 云端模型必须支持文字、图片和 Function Calling；设置页测试读图、指定工具调用及执行结果回传。
- 通过 `tools` 定义 `save_class_notes`，模型通过 `assistant.tool_calls` 提交结构化笔记。App 解析 JSON 字符串参数，校验 Schema 和来源，事务保存后以 `role: tool` + 对应 `tool_call_id` 回传执行结果。
- 工具只提交标题、分节 Markdown、来源段落和照片编号。当前课堂、批次和来源版本由 App 绑定，模型不生成图片路径或录音时间。
- 每张输入照片旁显式提供其 `ast_` 文件名、照片编号和录音时间，便于模型对应课堂内容；工具返回仍使用照片编号，文件名与时间由 App 校验和解析。
- 默认 `strict: true`、指定 `tool_choice`、`parallel_tool_calls: false`、`stream: false`。兼容服务的严格模式设置按协议文档处理；普通正文不能替代工具结果。
- 分批上限为六张照片或六千字符；分批工具回合完成后 App 按序合并。网络或接口异常保留资料与任务进度，支持手动重试和幂等保存。
- 默认中文整理，保留英文专有名词、缩写、课上公式和代码。听不清或图片不能辨认时标“待核对”。未引用的照片附在笔记末尾。

笔记支持标题、表格、图片、代码块和公式，生成后离线阅读。PDF 使用本地 HTML 渲染与安卓“保存为 PDF”，默认 A4 白底；Markdown 导出 ZIP，内含笔记与相对路径图片，来源显示为录音时间。

## 5. 验收与交付

先完成工程、模型下载和真机识别，再完成稳定录音与图片时间对应，随后接入 Tool Calling 云端整理，最后实现编辑与导出。

验证两台手机完整课堂时长的录音、拍照、锁屏、切换应用、暂停/继续、实际出字延迟和积压；记录系统版本及模型版本。用人工标注片段分别统计中英混说、口音和术语错误，不用云端笔记掩盖转写错误。

后台验收分别在两台手机进行连续 120 分钟锁屏录音、反复切换应用、拍照后返回、页面重建、绑定识别进程意外退出、云端整理中锁屏与断网恢复。普通后台/锁屏过程中无主动退出、未捕获异常、ANR 或音频丢段；识别进程故障不导致录音服务退出。额外测试 Doze、电池优化和用户强制停止，分别报告持续运行条件与中断恢复结果。

照片验收包括相同毫秒连续拍照、暂停时拍照、识别延迟、修改系统时间、任务恢复以及跨课堂同名；文件名可还原录音相对时间，实际拍摄日期独立保存，原图不被覆盖，云端来源与 Markdown 导出保持同一文件名及照片编号。

验证图片发生在文字尚未生成、段落中间、静音和暂停附近时的定位；验证权限拒绝、来电中断、存储不足、模型/下载失败及进程退出后已保存材料的恢复。

云端验收包含工具定义、参数类型、来源编号、tool_call_id 对应、重复保存、失败回执、多图、错误 Key、限流及实际往返。导出检查中文、公式、代码、跨页图片和 Markdown 图片包。

交付完整源码、固定私有签名 APK、安装配置说明和真机报告。签名材料和 API Key 不进入版本控制。已找到本机 Android SDK 并开始工程验证；当前未连接目标手机、未配置云端 Key，真机报告和真实接口验收仍待执行。已完成验证结果记录于 verification/。

## 规范依据

- [OpenAI 官方 Function Calling](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI 官方 Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)
- [Android 构建兼容说明](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Android HTML 打印](https://developer.android.com/training/printing/html-docs)
- [sherpa-onnx FireRed 模型](https://k2-fsa.github.io/sherpa/onnx/FireRedAsr/pretrained.html)
- [安卓前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android Doze 与后台限制](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Android 前台服务时限](https://developer.android.com/develop/background-work/services/fgs/timeout)
- [Android 系统停止行为](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)
