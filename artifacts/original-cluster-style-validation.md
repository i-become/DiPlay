# 原始仪表组件样式校准

日期：2026-10-31

入口：显示 → 仪表管理 → 原始 → 配置。

## 样式

以用户提供的原始仪表 UI 图为参考，统一换算到 2048 × 768 画布，在实际飞屏和配置预览中共用 OriginalClusterRenderer。

- 胎压、行车信息：约 36% 不透明的炭灰色背景（ARGB 92/11/11/14），地图可透出；圆角、标题、各列文字及分隔线按参考图对齐。
- 胎压车标使用项目提供的 docs/胎压车标.png，裁去透明边距并横向绘制；车轮文字对应车头朝左的方向。
- 转向卡：大圆角深色底，距离和道路字体按参考图设置；直行使用实心箭头与虚线尾部。有效车道信息显示在同一张卡的下部，此时转向箭头上移以避让道路文字。
- 导航时间：胶囊形深灰底、居中文字，中文时长采用“3:35 小时”的样式。
- 取消预览中四个组件常驻的编辑边框；仅长按拖动时标出当前组件。
- 保留组件开关、长按拖动、保存、重置及未保存退出提示。

## 验证

本功能相关 14 项测试全部通过：OriginalClusterLayoutTest（6）、NewUiOriginalClusterConfigTest（4）、NewUiClusterManagerTest（3）、SettingsTranslationsTest（1）。包括组件透明度、导航卡边界、直行箭头、编辑保存与退出，以及字体比例 1.4 的完整、窄窗口和阿拉伯语布局。

执行了 AGENTS.md 中的全部 CI 任务：

```text
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug --continue --stacktrace --console=plain
```

mobile/home/maphost 的 lint 和 Debug 构建通过，home 单元测试通过。完整命令因 shared 的 3 项、common 的 15 项既有测试失败而失败；18 个失败名称与 tire-refresh-ci.log 中的基线逐项一致，无新增失败。

完整日志：original-cluster-style-ci.log。

实际绘制效果图：original-cluster-components-reference.png；含车道信息版本：original-cluster-components-with-lanes.png。两图均使用示例地图和示例数据，组件为实际 Android 原生绘制。

编辑器截图：original-cluster-config-full-font-140.png、original-cluster-config-compact-font-140.png、original-cluster-config-arabic-font-140.png。

尚未实车验证。

## 测试安装包

文件：DiPlay-HS5-original-cluster-style.apk

大小：39,231,055 字节。

SHA-256：FC4169BC9635960DE5CBFB742035CF78DB74F7CE40AB30C4A5B00447A4D75CE9
