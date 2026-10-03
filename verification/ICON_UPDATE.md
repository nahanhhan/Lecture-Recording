# 应用图标更新

日期：2026-10-03。版本：0.1.1-alpha，versionCode 2。

以云端 `main` 的 `068e8ac9778fbd50f3f6bb0097a049d3857d5fe0` 为基础合入图标修改，保留该提交及之前其他成员的全部改动，包括下载源切换、日志功能和固定调试签名。

用户提供的 SVG 保存于 `artwork/app-icon.svg`。应用图标使用同一组七条矢量路径和原始颜色，纯白背景；同时提供安卓自适应图标与圆形图标资源。预览见 `artwork/app-icon-preview.png`。

本次验证结果：

- 仓库现有五项检查全部通过。
- 23 项核心测试通过，0 失败、0 错误。
- Debug、Release 编译通过；Android Lint 为 0 错误。
- 两个安装包签名验证通过；Release 的 16 KB ZIP 对齐检查通过。
- Release 签名与此前本地 0.1.0 安装包相同；Debug 沿用仓库已有的固定调试签名。
- 合入后设置页仅将版本文字改为读取实际构建版本，保留最新下载源和日志相关代码。

本地安装包：

- `dist/lecture-recording-0.1.1-alpha.apk`：沿用此前本地私有签名，26,804,015 字节。
- `dist/lecture-recording-0.1.1-alpha-debug.apk`：沿用仓库调试签名，96,514,249 字节。

Release SHA-256：`3c04aee9be6c77089fd7e4a5658d401a8d63ce92aff414742e3fa6d03239afdf`。
