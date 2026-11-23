# DiPlay

为红旗 HS5 安卓车机提供定制的有线及无线 CarPlay，采用 DiAuto 风格界面。

> 本项目专为红旗 HS5 车机及其启思导航栈定制，其他车型不在支持范围内。

[下载与中文网站](https://shihabal3amri.github.io/DiPlay/zh-Hans/) · [0.2.18 版本](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.18) · [完整说明](README.md) · [报告问题](https://github.com/shihabal3amri/DiPlay/issues/new/choose)

## 0.2.18 — 公开预览版

请安装在允许 APK 安装的 Android 7.1+（API 25+）车机上；Android 7.1–8.1 尚未经实车验证。无需越狱、转接盒、账户或认证服务器。有线及无线核心连接不要求 ADB；可选车辆数据及车辆控制需要支持的固件和已授权网络 ADB。

Wi-Fi Direct 现支持 Android 7.1–9 的旧版建组路径，使用系统返回的真实凭据。首选信道依赖固件 API；Android 7.1–9 无法读回协商频率，所以请求信道在诊断中标为未经验证，系统默认为信道 0。Android 10+ 保留频率验证。也可使用车机内置热点、USB 或同一局域网；同一局域网模式由车机和 iPhone 自行连接外部路由器，DiPlay 不替你修改默认路由。

### 新增与修正

- **有线连接**：USB 读取恰好在 DiPlay 停止等待时完成，不再导致每隔几分钟断开；Android 7.1 车机、USB 音频驱动占用 iPhone 的车机，以及没有 VPN 授权对话框但已授权网络 ADB 的车机，现在都能启动有线 CarPlay。
- **音乐与提示**：短暂的音乐断音减少；连接界面在所有应用语言下都显示实际的连接阶段和失败原因。
- **蓝牙**：如果车机的安卓蓝牙无法连到 iPhone，无线 CarPlay 会直接给出明确提示并建议改用 USB，不再反复重试。
- **方向盘**：在 CarPlay 画面中，上一首／下一首无需方向盘按键服务即可切歌；方向盘媒体键和 Siri 支持红旗 HS5 车机；停在“已允许，正在启动…”的方向盘按键会自动恢复。
- **设置备份与更新**：在“**设置 → 关于**”中通过二维码用手机备份或恢复设置；安装更新时可选择 MT 管理器等其他安装程序。
- **实验性选项**（默认关闭）：通过 USB 更清晰的导航语音、低延迟解码、关闭媒体按键音频焦点和通话使用车机电话音频。

版本记录见[更新日志](CHANGELOG.md)。Android 7.1–8.1 尚需实车验证，不宣称所有车型的连接、音频或 Siri 问题均已解决。可选功能请停车后测试。

### 请提供 0.2.18 的新诊断报告

1. 更新到 **0.2.18**，复现问题并记录发生时间。开机／自动启动问题发生后，可手动打开 DiPlay 导出。
2. 打开“**设置 → 诊断 → 保存诊断报告**”。Android 10+ 通常保存到 **Downloads/DiPlay**；Android 7.1–9 会请求存储权限并同样保存到该位置，也可点“选择保存位置”。如选择器或公共存储不可用，应用会使用专用外部或私有目录，并在确认中说明目的地。
3. 使用确认中的**查看报告／分享**；没有分享应用时，可在报告视图中选择并复制文本。检查 `.txt` 并删除隐私信息，再附到匹配的[现有问题](https://github.com/shihabal3amri/DiPlay/issues)，或[新建问题](https://github.com/shihabal3amri/DiPlay/issues/new/choose)。报告不会自动上传，请勿公开热点密码或私有认证文件。
4. 注明红旗 HS5 车机、Android/完整固件版本、iPhone/iOS、USB／车机热点／Wi-Fi Direct／同一局域网、相关设置、复现步骤、预期与实际结果及故障时间。

[从源码构建](BUILD.md)：主应用请选择 `mobile` 模块。`maphost` 是地图演示应用。

更新记录见 [CHANGELOG.md](CHANGELOG.md)。

## 来源与许可

项目主体基于 [xcertplay](https://github.com/shilapi/xcertplay)，采用 GPL-3.0。主页、设置界面和网站改编自 [DiAuto](https://github.com/shihabal3amri/DiAuto)，采用 AGPL-3.0。请保留来源和许可声明，详见[第三方声明](THIRD_PARTY_NOTICES.md)及 [LICENSES/DiAuto-AGPL-3.0.txt](LICENSES/DiAuto-AGPL-3.0.txt)。

这是公开预览版，**未经 Apple 认证**。APK 使用从公开 Carlinkit 固件中提取的既有实验性配件身份，并非为 DiPlay 新签发的 MFi 身份；其中的私钥可被提取，未来 iOS 是否继续接受及其公开分发适用性尚未确定。Android 签名密钥和配件身份不进入 Git 或源代码压缩包；普通源代码/CI 构建默认不配置身份。部分车机仍可能卡顿或无法应用图标大小设置。SDK 和二进制组件的来源及分发许可见[第三方声明](THIRD_PARTY_NOTICES.md)，公开发布前必须核实相应许可。

标准导航小组件需要支持 Android 小组件的启动器；红旗 HS5 内置主页可能不接受任意小组件。悬浮地图和嵌入地图需要启用“CarPlay 仪表地图”。应用及发布网站支持英语、简体中文、繁体中文（台湾）、阿拉伯语、俄语、乌克兰语和西班牙语。应用的香港／澳门及 Hant 选择使用台湾译文，不宣称提供独立地区翻译。源代码、构建说明及许可证随版本提供。
