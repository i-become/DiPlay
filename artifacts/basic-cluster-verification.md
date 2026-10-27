# 基础仪表实现与验证（2026-10-26）

## 实现

- 按用户提供的 2048×768 蓝色仪表参考图制作可缓存的表盘背景与车辆素材；示例地图、歌词、胎压、刻度、指针从生产背景中移除。
- 刻度采用原车量程：0–7 ×1000 r/min，0–260 km/h；速度高段保留 200、230、260。刻度和指针共享角度函数，车速继续 CAN raw ÷100。
- 中间使用独立 CarPlay 地图 Surface，按 5:3 协商且保持比例；两侧圆形遮罩和渐变融合。保留车标位置设置。
- 上方转向信息、下方剩余距离/预计到达/时间来自现有导航快照；右侧封面、歌曲、歌手、专辑、进度来自媒体订阅。当前接口没有逐句歌词或目的地名称，因此没有捏造这些数据。
- 左侧仅在车辆检测数据确认时显示前车；四轮胎压按推送事件 47、告警 49 更新，初次绑定读一次 26/80，压力换算 raw ×2.75/100 bar。无效值显示未知。
- 修复初次读取期间收到的胎压推送被旧读取值覆盖的竞争问题，并添加回归用例。
- 车速、挡位、胎压等使用订阅。转速遵守车机限制，保留既有后台 750 ms 采样；不在主线程调用 RPC。指针采用已有平滑动画。
- 原始仪表样式保留，设置中的基础样式预览自动使用新渲染器。

## 验证

运行 AGENTS.md 和 .github/workflows/android.yml 的完整单元测试、lint、assembleDebug 命令，额外使用 --continue 收集全部结果。

- 与仪表直接相关的 62 项用例通过：截图 2、View 7、指针动画 3、样式 3、RPM 3、车辆证据 2、车辆 Monitor 11、协议 17、合成 7、布局/刻度 7。
- mobile、home、maphost 的 lintDebug 与 assembleDebug 全部完成。
- 全量单元测试未全绿：shared 1458 项中失败 3；common 1206 项中失败 15，跳过 39。失败与此前检查相同：HotspotJoinNormalizedTransactionTest 3、CarHotspotFallbackTest 1、CarPlayHostThemeDiagnosticsTest 3、CarPlayStageTextTest 4、HostConnectionSettingsRefreshTest 1、SmoothVideoHostLifecycleTest 2、StartupDiagnosticSnapshotTest 2、WifiP2pGroupManagerTest 1、WifiP2pLegacyGroupManagerTest 1。详细错误见 cluster-basic-ci.log。
- 检查了 2048×768 与 1280×480 的 Android 原生渲染截图。预览地图及媒体、胎压数值仅为测试素材，不打包到生产画面。
- 已同步 Superdesign 原有 draft，当前版本 10；不改变其他设计目标。

## 交付

- DiPlay-basic-cluster-debug.apk（37,754,319 字节）
- APK SHA-256：126EF47F686887636896CD77B6D2224008491AF256DA41675076D6AA6D65D93C
- basic-cluster-preview.png 与 basic-cluster-preview-compact.png
- cluster-basic-ci.log

本次未操作实车安装，仍需在 HS5 上确认屏幕缩放、实时导航图层、OEM overlay 清理与真实数值的一致性。

