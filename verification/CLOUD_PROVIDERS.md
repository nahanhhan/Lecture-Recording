# 云端供应商与连接测试验证

日期：2026-10-03。版本：0.1.4-alpha，versionCode 4。基于云端最新 `68d6a39430f5b1fa8bb3c6a6256397f0e237d5f1` 实施，上传前再次确认该远端版本未变化。

## 修复与新增

- DeepSeek、OpenRouter、OpenCode Zen、OpenAI、自定义配置入口，独立加密保存密钥和配置，旧版配置按官方域名迁移。
- 基础地址、版本路径、完整 `/chat/completions` 地址统一处理；保留自定义网关路径，拒绝凭据、查询参数、片段及其他 API 格式端点。
- DeepSeek 显式关闭思考模式，省略 `strict` 和 `parallel_tool_calls`；其他服务兼容模式省略 `strict`。本地严格校验不变。
- 连接测试分为文字回复、文字笔记及实际本机保存回执、可选读图。各步骤独立显示状态和供应商错误原因。重新测试先失效旧的通过状态。
- 可获取模型列表；元数据有图片/工具能力时显示，仍要求实际能力测试。
- 关闭照片整理后快照及请求不包含照片；原照片在本机保留，并按录音时间自动插入对应文字所在的笔记小节。恢复包含照片的旧任务时拒绝文字模式，避免绕过开关。

## 已通过

- `:core:test`：44 项，包括照片时间插入、来源匹配、去重、地址规范化、DeepSeek 参数、工具回执、密钥脱敏、模型列表和文字快照。
- `:app:testDebugUnitTest`：5 项，通过可信测试证书的 HTTPS 模拟服务验证真实 OkHttp 路径、鉴权、模型列表、HTTP 错误原因、JSON 错误体、跳转拒绝、请求取消，以及不带出密钥的输入格式检查。
- `:app:lintDebug`：0 个错误；37 个建议级警告。
- `:app:assembleDebug`：arm64 构建成功。
- `CloudSettingsIntegrationTest`：Android API 34 x86_64 模拟器上 4 项通过。验证 Keystore 加密配置迁移、供应商密钥隔离、配置改变/重测失效状态、实际本机保存和回执、文字模式不传图、读图失败独立显示，以及页面切换供应商、照片开关和保存。
- `python -m checks`：5 项通过。
- `git diff --check`：通过。

模拟器使用独立 `.uitest` 应用，不修改正式应用的数据。界面操作使用 Compose 测试接口完成滚动和点击，截图已在本机检查。

## 安装包

`dist/recnote-0.1.4-alpha-debug.apk`：97,586,448 字节，仅 arm64-v8a，包名 `io.github.nahanhhan.lecturerecording`，仓库固定调试签名。

- APK SHA-256：`acc891ab62e52dd970371fb3f7eaa133ef75dd0cb50cda2bcbb5fd1be1641f50`
- 签名证书 SHA-256：`4149018b4ba6e74ff27c12e96d775fe01bbab4b510275509c1a7225a89f81f3e`

APK 不进入源码仓库，云端工作流上传同一版本的构建产物。

## 验证边界

未使用实际供应商 API Key 发起付费请求。测试证明请求处理、配置迁移、本机保存及错误诊断正确，不代表具体账号的余额、权限、网络或任意模型能力已通过。用户需在设备设置中完成分步测试。

OpenCode 接入的是 Zen 云端服务；当前适配器仅支持 Chat Completions。其使用 Responses、Anthropic 或 Gemini 专用格式的模型暂不支持。

官方依据：[DeepSeek 基础地址](https://api-docs.deepseek.com/)、[DeepSeek 严格工具调用](https://api-docs.deepseek.com/guides/tool_calls/)、[DeepSeek 思考模式与指定工具限制](https://api-docs.deepseek.com/api/create-chat-completion/)、[OpenRouter](https://openrouter.ai/docs/quickstart)、[OpenCode Zen 端点](https://opencode.ai/docs/zen/)、[OpenAI 工具调用](https://developers.openai.com/api/docs/guides/function-calling)。
