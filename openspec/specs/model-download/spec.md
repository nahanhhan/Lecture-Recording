# model-download Specification

## Purpose

定义手机端识别模型下载的来源选择与下载行为：用户可在魔塔社区与 GitHub 两源间切换，系统按选中源取直链下载、断点续传、校验并安装模型文件。

## Requirements

### Requirement: 下载源切换与持久化

设置页 SHALL 提供「魔塔社区」与「GitHub」两档下载源切换控件，切换结果 SHALL 立即持久化，并在应用重启后保持。默认下载源 MUST 为 GitHub，保证升级后行为与现状一致。

#### Scenario: 首次进入默认 GitHub

- **WHEN** 用户从未切换过下载源并打开设置页
- **THEN** 下载源切换控件处于「GitHub」档，模型下载使用 GitHub Releases 直链

#### Scenario: 切换到魔塔社区并重启后保持

- **WHEN** 用户将下载源切换控件切到「魔塔社区」后完全退出并重新打开应用
- **THEN** 下载源切换控件仍处于「魔塔社区」档，后续下载使用魔塔社区直链

### Requirement: 按选中源构造下载地址

系统 SHALL 按当前选中的下载源构造模型归档的下载地址：魔塔社区源使用 `https://modelscope.cn/models/{组织}/{仓库}/resolve/{分支}/{文件路径}`，GitHub 源使用 `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/{归档文件名}`。两个源 MUST 指向同一归档文件，即 `ModelSpec` 中声明的 `archive` 文件名。

#### Scenario: 选中魔塔社区时请求魔塔直链

- **WHEN** 下载源为「魔塔社区」且用户开始下载 AED 模型
- **THEN** 请求地址为 `https://modelscope.cn/models/adaada88/sherpa-onnx-fire-red-asr2-aed-zh-en-int8/resolve/master/sherpa-onnx-fire-red-asr2-zh_en-int8-2026-02-26.tar.bz2`

#### Scenario: 选中 GitHub 时请求 GitHub 直链

- **WHEN** 下载源为「GitHub」且用户开始下载 CTC 模型
- **THEN** 请求地址为 `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-fire-red-asr2-ctc-zh_en-int8-2026-02-25.tar.bz2`

### Requirement: 断点续传与下载源绑定

部分下载文件与续传校验信息（ETag）SHALL 与产生它的下载源绑定。恢复下载时，仅当待续传数据来自当前选中的源才可复用；否则 MUST 丢弃旧源的部分数据并从零开始下载，禁止跨源续传。

#### Scenario: 同源恢复下载

- **WHEN** 用户在「GitHub」源下载中断后再次点击继续下载，且下载源未变
- **THEN** 以 Range 续传剩余字节，服务端返回 206 时从断点继续写入

#### Scenario: 切换源后重新下载

- **WHEN** 用户在「GitHub」源下载到一半后将下载源切换控件切到「魔塔社区」再继续下载
- **THEN** 旧源的部分文件不被复用，从 0 字节重新下载，不发送旧源的 Range/If-Range 标头

### Requirement: 下载完整性校验与安装

无论来自哪个下载源，归档下载完成后系统 MUST 校验文件总字节数与 `ModelSpec` 声明的 SHA-256 一致，校验通过才解包安装白名单内的模型文件；校验失败 MUST 删除该临时文件并提示重新下载。

#### Scenario: 校验通过后安装

- **WHEN** 任一源下载的归档字节数与 SHA-256 均与目录声明一致
- **THEN** 解包白名单文件并写入安装清单，状态变为「安装完成」

#### Scenario: 校验失败

- **WHEN** 下载完成但 SHA-256 与声明不一致
- **THEN** 删除临时归档，提示「模型校验失败，请重新下载」，不安装任何文件

### Requirement: 下载源不可用时的错误反馈

当所选源返回非成功状态码或网络不可达时，系统 SHALL 展示可读的失败原因（含 HTTP 状态码），下载状态 SHALL 允许用户在不重启应用的情况下重试，且切换下载源后重试必须生效。

#### Scenario: 直链返回 404

- **WHEN** 所选源的直链返回 HTTP 404
- **THEN** 界面显示「下载失败（HTTP 404）」，用户可切换下载源后重新开始下载

#### Scenario: 网络不可达

- **WHEN** 所选源连接超时或被拒绝
- **THEN** 界面显示可读的失败原因，已下载的部分数据按源绑定规则保留或丢弃

### Requirement: 下载进行中的源切换保护

当存在进行中的下载（下载中或校验安装中）或录音进行中时，下载源切换控件 MUST 禁用，避免下载中途更换来源导致数据不一致。

#### Scenario: 下载中禁用切换

- **WHEN** 某模型处于「下载中」或「正在校验并安装」状态
- **THEN** 下载源切换控件不可操作，下载结束或失败后恢复可操作
