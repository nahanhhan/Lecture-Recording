# Design

## Context

AGP 对 debug 变体默认使用 `~/.android/debug.keystore`（alias `androiddebugkey`、口令 `android`）自动签名；文件不存在时自动生成新密钥。CI 全新 runner 每次都生成新密钥，导致每次产物签名不同、无法覆盖安装。现有 [`android.yml`](.github/workflows/android.yml) 直接执行 `./gradlew :app:assembleDebug` 并上传 `app-debug.apk`，无任何签名加载步骤。release 私有签名走根目录 `keystore.properties`（[`app/build.gradle.kts`](app/build.gradle.kts) 中 `privateRelease` 配置），与本次变更无关。仓库现状：[`.gitignore`](.gitignore) 忽略 `*.keystore`/`*.jks`，[`.gitattributes`](.gitattributes) 的 `* text=auto` 会对文本判定文件做换行转换。动机见 [proposal.md](openspec/changes/use-static-debug-keystore/proposal.md) - Why。

## Goals / Non-Goals

**Goals:**
- debug 签名身份与构建环境解耦：任意两次 CI 构建产物签名一致，可互相覆盖安装
- keystore 初始化与 CI 构建工作流分离：单独的 `init_debug_keystore.yml` 负责一次性创建，`android.yml` 只负责"始终加载"
- 加载逻辑只落在 CI 侧，不改动 Gradle 构建脚本的签名配置

**Non-Goals:**
- 不改动 release 私有签名流程（`keystore.properties`、[`scripts/create_signing_key.py`](scripts/create_signing_key.py)）
- 不统一本地 debug 构建签名（开发机 `~/.android/debug.keystore` 照旧；本地产物与 CI 产物互不覆盖是已知边界）
- 不引入签名指纹比对、防篡改校验或签名轮换机制
- 不涉及 versionCode 递增策略

## Decisions

**D1：`init_debug_keystore.yml` 置于 `.github/workflows/`，`workflow_dispatch` 手动触发。**
备选：放在项目根目录——GitHub 只执行 `.github/workflows/` 下的 yml，根目录文件无法触发；改成本地 Python 脚本——与用户指定的独立 yml 形态不符。工作流的产出物 `debug.keystore` 落在仓库根目录（用户指定位置），文件名与 AGP 默认 debug 密钥同名同义，便于心智对应。

**D2：keystore 采用标准 debug 凭据（JKS、alias `androiddebugkey`、store/key 口令 `android`、`CN=Android Debug,O=Android,C=US`），由 init 工作流用 `keytool` 生成并提交回仓库。**
备选 A：base64 存入 GitHub Secret，CI 解码——需额外配置 secret、fork PR 无法解码，且"项目根目录创建 debug.keystore"落不了地；备选 B：工作流只上传 artifact 人工下载提交——多一步人工易漏。debug 密钥不含信任语义（Android 官方 debug 口令即公开的 `android`），入库安全可接受。使用标准凭据的原因：AGP 默认 debug 签名硬编码该 alias/口令，CI 侧只需放文件、零配置即可生效。

**D3：`android.yml` 在构建前把仓库根目录 `debug.keystore` 复制到 `~/.android/debug.keystore`（`mkdir -p ~/.android` 后 `cp`），缺失则显式失败，MUST NOT 自动生成。**
备选 A：[`app/build.gradle.kts`](app/build.gradle.kts) 增加 debug `signingConfig` 指向根目录文件——需要改构建脚本，与"ci 改成始终加载"的定位不符，且影响本地构建；备选 B：Gradle `-P` 属性注入密钥路径——AGP 无此注入点。复制方案对构建脚本零侵入，AGP 自动采用已放置的 `debug.keystore`。

**D4：`.gitignore` 为根目录 `debug.keystore` 增加例外（如 `!/debug.keystore`），`.gitattributes` 增加 `*.keystore binary`。**
现状 `*.keystore` 规则会把目标文件挡在版本控制外；`* text=auto` 可能对未标记二进制的文件做 CRLF 转换导致密钥损坏、签名漂移。两项缺一不可。

**D5：init 工作流幂等守卫——根目录已存在 `debug.keystore` 时直接成功退出，不覆盖。**
覆盖等于更换签名身份、打断升级链；一次性初始化的语义靠"存在即不动"保证，误触发也无害。

## Risks / Trade-offs

- [debug 密钥入库，仓库写权限者可签发同签名 debug 包] → debug 签名本无信任语义，仅用于开发调试产物分发；release 仍由本地私有密钥签名
- [init 工作流需要 `contents: write` 才能回推提交] → 权限只给该工作流；[`android.yml`](.github/workflows/android.yml) 保持 `contents: read` 不变
- [换行/编码转换损坏 keystore] → `.gitattributes` 标记 `*.keystore binary`（D4）
- [本地 debug 构建与 CI 产物签名不同、互相无法覆盖安装] → 明确为 Non-Goal；如日后需要，可让 Gradle 侧也加载根目录 keystore（本次刻意不做）
- [初次启用前已下载旧签名产物的用户] → 一次性卸载重装（proposal 已标 **BREAKING**）；之后升级链恢复平滑

## Migration Plan

1. 合并本变更后手动触发一次 `init_debug_keystore.yml`，确认 `debug.keystore` 出现在仓库根目录
2. 此后 push/PR 走 [`android.yml`](.github/workflows/android.yml) 正常构建；从下一次 CI 产物起即可覆盖安装旧 CI 产物
3. 回滚：删除根目录 `debug.keystore` 并移除 `android.yml` 的加载步骤，即恢复现状（用户重新经历一次性卸载重装）
