# HS5 基础仪表：地图融合与状态显示验证

日期：2026-10-26

## 本轮改动

- 两侧仪表各再向外移动 40 个源图像素，总偏移为 100；同步刻度、指针、辅助驾驶、媒体和下方光带。
- 地图区域扩大为 2:1；四边渐隐与沿表盘外缘的弧形过渡使用缓存前景绘制，不增加车辆数据轮询。
- 没有有效导航/转向信息时，转向卡和行程条全部隐藏。具备转向信息后自动显示；行程字段只显示实际可用数据，不绘制占位值。
- 车标定位区域从宽区域收窄至目标位置附近，继续读取用户设置。浏览/导航切换使用同一份配置且不重建 Surface。此项是针对高德右偏的协议配置修正，需要重新连接 CarPlay 后实车确认，未宣称已验证高德行为。
- 四轮各自使用 OEM 告警位：对应轮胎和胎压文字变红，并给轮胎增加感叹号；无告警轮胎保持原状态。不自行推断告警阈值。

## 检查结果

相关 10 个测试类共 71 项全部通过，覆盖地图几何、车标位置范围、卡片显隐、四轮告警隔离、图层合成、指针动画及车辆数据协议。

按 AGENTS.md 执行完整 CI（使用已有本地 init-script 重定向构建输出，设置 ANDROID_HOME）：

    ./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug --continue --stacktrace --init-script .gradle/codex-validation-20261020/init.gradle --console=plain

- shared：1458 项，3 项失败。
- common：1215 项，15 项失败，39 项跳过。
- home：4 项，通过。
- mobile/home/maphost 的 lintDebug 与 assembleDebug 均完成。
- 完整 CI 因单元测试失败而失败；18 项失败名称与上一版 artifacts/cluster-refinement-ci.log 完全一致，没有新增失败。
- 最终完整日志：artifacts/cluster-map-fusion-ci-final.log；定向检查日志：artifacts/cluster-map-fusion-targeted.log。
- 已查看原生渲染的导航、无导航、缺少转向、胎压告警、窄屏以及大字体/阿拉伯语截图。最后微调地图上下边缘颜色后，重新完成完整 CI 并查看最终参考图及大字体警告图。

## 交付

- APK：artifacts/DiPlay-cluster-map-fusion-debug.apk（37,754,319 字节）
- SHA-256：E9D99F79BF15BF3C9D328805E38112BE94D81FED6366A17C929681BEA0B58DD7
- 导航预览：artifacts/cluster-map-fusion-preview.png
- 无导航预览：artifacts/cluster-map-fusion-idle.png
- 胎压告警预览：artifacts/cluster-map-fusion-tire-warning.png

预览中的地图、媒体与数值均为测试素材，实车继续使用实时 CarPlay 和车辆数据。

Superdesign 同一草稿已更新为 v12，并回读确认草稿 ID、版本和图片内容哈希；未将源文件检查当作浏览器视觉验证。原生截图为本轮视觉检查依据。

## 实车重点

安装并重新连接 CarPlay，在高德未导航 → 开始导航 → 取消导航之间切换，观察车标是否仍保持设置的位置、卡片是否及时出现与消失。车辆真实告警需确认对应轮胎与胎压值一起变红；软件测试已覆盖四轮独立告警及无压力值时的告警显示。

