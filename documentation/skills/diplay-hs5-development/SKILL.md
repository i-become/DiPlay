---
name: diplay-hs5-development
description: 在 DiPlay 红旗 HS5 适配项目中接手功能开发、故障定位、车辆接口、CarPlay/CarLink、仪表飞屏、构建与验证时使用。首次处理仓库任务或跨模块改动前读取本技能。
---

# DiPlay 红旗 HS5 开发技能

本技能帮助 AI 快速接手当前仓库。先以工作区代码和 Git 状态为准；项目文档是导航，不替代源码、测试或固件实测。

## 接手步骤

1. 阅读仓库根目录 `README.md`、`documentation/README.zh-CN.md`、`documentation/红旗HS5车辆接口.md` 和 `documentation/安全与发布.md`。
2. 运行 `git status --short`，记录用户已修改、已暂存和未跟踪文件。不要覆盖、清理或重置不属于本任务的改动，不要自行提交或推送。
3. 确认任务所属模块，沿着调用方、状态所有者、生命周期和 UI 消费方读代码；用 `rg` 搜索符号和测试，不要仅凭类名或旧对话推断当前行为。
4. 查看相邻测试与最近实现，再做范围最小的修改。涉及车辆 Binder、CarLink、渲染线程、媒体会话或启动路径时，先理解现有线程和释放策略。
5. 修改后运行针对性测试及公开目录检查；报告实车验证与模拟器验证之间的区别。

## 项目地图

根 Gradle 项目名仍为 `xcertplay`，应用包名为 `com.shihab.diplay`，主要 Kotlin 包为 `com.shilapi.xcertplay`。不要据项目名或 Java 包名推断用户可见产品名。

| 模块 | 责任 | 从这里开始找 |
| --- | --- | --- |
| `common/` | 应用界面、连接编排、HS5 车辆状态、CarLink 与仪表页面 | `CarPlayHostActivity.kt`、`CarlinkBridge.kt`、`Hs5VehicleMonitor.kt`、`Hs5SteeringKeys.kt` |
| `shared/` | CarPlay 协议与传输、音视频、导航输出、共享状态及原生代码 | `airplay/`、`transport/`、`hud/`、`glance/`、`diagnostics/` |
| `mobile/` | Android 应用入口、权限/组件声明、依赖和 APK 构建后处理 | `mobile/build.gradle.kts`、`mobile/src/main/AndroidManifest.xml` |
| `samples/home/` | 独立首页/UI 样例模块 | `samples/home/src/` |
| `samples/maphost/` | 地图宿主样例 | `samples/maphost/src/` |
| `documentation/` | 中文项目文档、HS5 接口说明、截图与本技能 | 本索引及 `红旗HS5车辆接口.md` |

功能阅读路径：

- 首页与应用入口：`common/.../newui/`、`CarPlayHostActivity.kt`。
- CarPlay 会话：`shared/.../airplay/`、`transport/` 和 `common/.../CarPlayBackgroundSession` 相关代码。
- CarLink 生命周期、SDK 回调和画面 Surface：`common/.../carlink/CarlinkBridge.kt` 及同目录策略、Activity、渲染器。
- 仪表状态和风格：搜索 `Hs5Cluster`、`ClusterDashboard`、`Original`、`Urban`、`Sport`；确认当前实现的实际文件路径。
- HS5 车辆数据：`Hs5VehicleMonitor.kt`、`Hs5VehicleProtocol.kt`、`QisiPositionLights.kt`、`Hs5LeadVehicleReader.kt`。
- 方向盘：`QisiSteeringServiceClient.kt`、`Hs5SteeringKeyPolicy.kt`、`Hs5SteeringKeys.kt`。
- 仪表导航输出：`shared/.../hud/QisiVehicleBinder.kt`、`Hs5GuidanceBridge.kt`、`QisiClusterMapSession.kt`。
- 熄屏状态：`HongqiScreenStatus.kt`。
- 诊断记录：`DiagnosticLogging.kt`、`AsyncDiagnosticLog.kt` 和相关功能自身的诊断入口。

路径中的 `...` 是说明性缩写，不是可直接传给命令的真实路径；用 `rg --files` 定位文件。

## 关键行为约束

- 这是面向红旗 HS5 启思车机环境的适配项目。事务号、信号映射和状态值不是 Android 通用接口；维护时对照当前实现与 `documentation/红旗HS5车辆接口.md`，对未证实的固件行为保留“未知”。
- 车辆服务、方向盘、HUD、熄屏状态等是可选 OEM 能力。缺少服务或事务时应让对应功能降级，不能拖慢或破坏 CarPlay 主连接。
- Binder 数据检查接口描述符、返回长度、信号范围、车轮序号和缓存时间。断连时释放订阅、焦点、Surface 和工作任务；避免在主线程运行 Binder、解码和文件 I/O。
- CarPlay 与 CarLink 使用不同的会话/导航数据源，但仪表表现和组件布局应由共同 UI 状态及仪表配置决定。修改时检查两个协议的状态清理和重连路径。
- CarLink 解码画面依赖 Surface/EGL 生命周期。Activity 显示切换、回车机、Surface 销毁和手机断开是不同事件；不要把中控回车机误当成断开连接。
- 诊断文件写入必须受 `DiagnosticLogging` 用户开关控制。禁用后不持续写磁盘、不采集高频明细；日志避免配对码、认证材料和不必要的个人信息。
- 驾驶辅助/车辆数据仅用于显示与诊断，不能替代原车安全系统；不新增未经验证的车辆控制事务。
- 保持现有 UI 布局、日夜主题和车辆适配的既有视觉约定。改布局前同时检查 HS5 横屏实机尺寸、模拟器和配置页预览。

## 本地依赖与安全

以下本地输入可能存在于维护者环境，但不会由公开仓库提供；缺少时构建失败可能是预期情况：

- `common/libs/icar-sdk-stubs.jar`：CarLink 编译接口。
- `mobile/libs/icar-sdk-corrected.dex`：CarLink 运行代码。
- `mobile/src/main/jniLibs/<架构>/libovmsink.so`、`libusbio.so`：CarLink 专用本地库。
- `runtime-assets/offline-mfi/`：仅在本地配置 `DIPLAY_AUTH_ASSETS_DIR` 后使用的 CarPlay 认证输入。
- `.private/`、本地签名变量或 `local.properties`：维护者本地构建和发布配置。

不要要求用户粘贴密钥/证书或口令，不要把它们输出到工具日志、文档、测试或 Git。不要尝试从安装包提取、恢复或复制闭源认证/协议材料。发布构建还受签名证书指纹和内容封印公钥约束；更改校验需要明确的密钥轮换方案。

CarPlay 基础代码、DiAuto/DiPlay 适配内容及第三方库有不同来源和许可。新增或搬用代码、UI、图片、字体及库之前检查 `LICENSE`、`documentation/上游与第三方声明.md`、随附许可证和源文件版权头；不能因其已在工作区出现就推断可再分发。

## 构建与验证

当前构建配置要求 Android SDK 37、NDK `28.2.13676358`、项目指定的 Java 工具链 25，以及 CarLink/认证相关本地输入。实际任务开始前检查 `gradle/libs.versions.toml`、`gradle/gradle-daemon-jvm.properties` 和各模块 Gradle 文件，因为这些值可能随分支更新。

Windows 示例：

```powershell
# Kotlin/JVM 单元测试
.\gradlew.bat :shared:testDebugUnitTest
.\gradlew.bat :common:testDebugUnitTest

# 调试包；是否能完成取决于本地二进制是否已配置
.\gradlew.bat :mobile:assembleDebug

# 检查被 Git 跟踪的敏感容器、本地 CarLink 二进制和私钥块
python scripts/check_public_tree.py

# 补丁格式检查
git diff --check
```

按改动范围挑选测试：导航看 `shared/.../hud/*Test.kt`，HS5 Binder/数据策略看 `common` 对应测试，CarLink 渲染看 `CarlinkVideoRendererTest.kt`，诊断开关看 `DiagnosticLoggingTest.kt`。修改 Android 组件生命周期、Compose 页面或渲染行为时，不能只凭编译通过判断完成。

实车专属行为（CarPlay/CarLink 配对、方向盘、熄屏、仪表副屏和车辆数据）必须注明是否真正通过目标 HS5 固件验证。模拟器只证明应用/界面可运行，不证明 OEM Binder 或车辆协议工作。

## 改动交付

完成任务时简要说明行为变化、主要文件、执行的测试和未覆盖的实车验证。列明用户已有的暂存改动如影响交付。除非用户明确要求，不打包发布、不签名、不提交、不推送，也不重写 Git 历史。
