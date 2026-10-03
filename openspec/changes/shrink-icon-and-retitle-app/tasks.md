# Tasks

## 1. 图标几何缩放（矢量资产）

- [ ] 1.1 按 [design.md](design.md) 参数（s ≈ 0.74256，t ≈ (164.4, 164.1)，±1 视口单位容差）用 `<group>` 包裹 [`ic_launcher_foreground.xml`](../../../../app/src/main/res/drawable/ic_launcher_foreground.xml) 的七条图形路径。验证：XML 可解析；七条 pathData 与改前逐字一致、仅新增 group 包裹；group 数值与 design.md 一致
- [ ] 1.2 同一变换参数包裹旧式 [`mipmap-anydpi/ic_launcher.xml`](../../../../app/src/main/res/mipmap-anydpi/ic_launcher.xml) 与 [`mipmap-anydpi/ic_launcher_round.xml`](../../../../app/src/main/res/mipmap-anydpi/ic_launcher_round.xml) 的图形路径（全幅白色背景路径留在组外）。验证：两文件 group 参数与 1.1 完全一致；背景路径坐标不变
- [ ] 1.3 用 `<g transform="translate(...) scale(...)">` 包裹 [`artwork/app-icon.svg`](../../../../artwork/app-icon.svg) 的同一组七条路径，背景 `<use>` 不动。验证：SVG 可解析；变换参数与 1.1 同源对应
- [ ] 1.4 按同一几何参数重新生成 [`artwork/app-icon-preview.png`](../../../../artwork/app-icon-preview.png)（渲染 SVG，或一次性 Pillow 脚本解析绘制七形状，脚本不入库）。验证：图形外框长边占画布 50%（±1%）、四边留白对称；肉眼核对与矢量资产一致
- [ ] 1.5 本批验收：在 `verification/checks` 执行 `uv run python -m checks` 全绿，推送后 CI（`:core:test`、`:app:lintDebug`、`:app:assembleDebug`）通过（工程验证口径，不含真机发布验收）

## 2. 软件名称统一「录课」

- [ ] 2.1 将 [`AndroidManifest.xml`](../../../../app/src/main/AndroidManifest.xml) 的 `android:label` 改为「录课」。验证：manifest 中不再含「课堂录音笔」，diff 仅该字符串
- [ ] 2.2 将 [`MainActivity.kt`](../../../../app/src/main/java/io/github/nahanhhan/lecturerecording/MainActivity.kt) 首页顶栏标题改为「录课」，设置页「设置」与详情页「课堂记录」保持不变。验证：diff 仅一处字符串字面量
- [ ] 2.3 将 [`RecordingService.kt`](../../../../app/src/main/java/io/github/nahanhhan/lecturerecording/recording/RecordingService.kt) 通知标题改为「录课」。验证：diff 仅一处字符串字面量；全仓搜索「课堂录音笔」仅剩 [`IMPLEMENTATION_PLAN.md`](../../../../IMPLEMENTATION_PLAN.md) 等历史文档
- [ ] 2.4 本批验收：在 `verification/checks` 执行 `uv run python -m checks` 全绿，推送后 CI（`:core:test`、`:app:lintDebug`、`:app:assembleDebug`）通过（工程验证口径）

## 3. 版本递增与文档同步

- [ ] 3.1 将 [`app/build.gradle.kts`](../../../../app/build.gradle.kts) 升至 `versionName = "0.1.2-alpha"`、`versionCode = 3`。验证：diff 仅版本两行
- [ ] 3.2 同步 [`README.md`](../../../../README.md)：版本行为 **0.1.2-alpha**、图标 alt 文案改为「录课图标」（用户可见名称变化）。验证：`uv run python -m checks` 中版本一致性检查通过；README 无其他改动
- [ ] 3.3 在 [`verification/DEVICE_CHECKLIST.md`](../../../../verification/DEVICE_CHECKLIST.md) 增补一条：安装包图标在圆形/圆角遮罩下留白充足不裁切，启动器名称、顶栏与通知标题均显示「录课」。验证：清单含该条目
- [ ] 3.4 本批验收：在 `verification/checks` 执行 `uv run python -m checks` 全绿，推送后 CI（`:core:test`、`:app:lintDebug`、`:app:assembleDebug`）通过（工程验证口径）

## 4. 真机验收（人工）

- [ ] 4.1 在目标手机安装 versionCode 3 的产物，按 [`verification/DEVICE_CHECKLIST.md`](../../../../verification/DEVICE_CHECKLIST.md) 新增项人工核对图标留白与「录课」名称显示并记录结果。验收：清单人工勾选与记录完成（真机发布验收，不由 CI 代替）
