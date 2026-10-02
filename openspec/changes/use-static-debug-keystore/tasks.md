# Tasks

## 1. 仓库配套调整

- [x] 1.1 在 [`.gitignore`](.gitignore) 为根目录 `debug.keystore` 增加例外（如 `!/debug.keystore`），验证 `git check-ignore debug.keystore` 无输出（不被忽略）且 `keystore.properties`、`*.jks` 仍被忽略
- [x] 1.2 在 [`.gitattributes`](.gitattributes) 增加 `*.keystore binary`，验证 `git check-attr binary -- debug.keystore` 返回 `binary: set`

## 2. 初始化工作流 init_debug_keystore.yml

- [x] 2.1 创建 [`.github/workflows/init_debug_keystore.yml`](.github/workflows/init_debug_keystore.yml)：`workflow_dispatch` 手动触发；幂等守卫——根目录已存在 `debug.keystore` 时直接成功退出且不修改；验证：以工作流运行结果为验收（已有文件时运行零改动、无文件时生成），端侧无需 Android/JDK 环境
- [x] 2.2 工作流用 `keytool` 生成 JKS 到仓库根目录 `debug.keystore`（alias `androiddebugkey`，store/key 口令 `android`，`CN=Android Debug,O=Android,C=US`）并提交入库；验证：以工作流运行结果为验收——触发后仓库根目录出现 `debug.keystore` 且提交入库成功
- [x] 2.3 工作流权限最小化为 `permissions: contents: write`（仅回推提交所需）；验证：yml 中无其他 write 权限，[`android.yml`](.github/workflows/android.yml) 仍为 `contents: read`

## 3. CI 始终加载静态签名

- [x] 3.1 在 [`.github/workflows/android.yml`](.github/workflows/android.yml) 构建前增加加载步骤：根目录 `debug.keystore` 复制到 `~/.android/debug.keystore`（先 `mkdir -p ~/.android`），缺失时显式失败；验证：以 CI 结果为验收——构建全绿，连续两次 CI 产物签名证书一致
- [ ] 3.2 验证缺失回退被堵死：以 CI 结果为验收——临时移除根目录 `debug.keystore` 触发 CI，失败日志明确指向缺少静态签名密钥且未产出 APK，而非自动生成新密钥

## 4. 文档与集成验证

- [ ] 4.1 端到端验证升级链：连续两次 CI 构建的 `app-debug.apk` 签名证书指纹一致，且 `adb install -r` 用新产物覆盖安装旧产物成功、应用数据保留；验证：记录两次指纹比对结果与覆盖安装日志（如 [`verification/DEVICE_CHECKLIST.md`](verification/DEVICE_CHECKLIST.md) 所列真机流程）
