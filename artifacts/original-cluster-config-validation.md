# 原始仪表配置功能验证

日期：2026-10-31

入口：显示 → 仪表管理 → 原始 → 配置。

## 已实现

- 胎压、行车信息、转向卡（包含车道箭头）、导航时间四组组件独立控制显示。
- 默认全部启用，默认位置按提供的 2048 × 768 参考图换算。
- 左侧等比例预览与实际飞屏共用组件绘制代码；预览地图和数据为示例，不连接车辆服务。
- 长按可见组件后拖动，位置按屏幕比例保存并限制在预览范围内。
- 重置仅修改当前草稿；保存持久化并通知正在显示的原始飞屏更新，无需重连 CarPlay。
- 返回、系统返回和顶部页面切换均检查未保存修改；确认丢弃前不写入配置。
- 胎压车辆图片使用 docs/胎压车标.png；实际飞屏接入车辆及导航数据。
- 导航无有效数据时隐藏对应信息卡；缺失的行车数据使用占位符。

## 验证结果

相关 33 项测试通过：OriginalClusterLayoutTest（5）、NewUiOriginalClusterConfigTest（4）、NewUiClusterManagerTest（3）、Hs5VehicleMonitorTest（17）、ClusterStyleTest（3）、SettingsTranslationsTest（1）。

已执行 AGENTS.md 要求的全部 CI 任务，使用 --continue 收集所有任务结果：

```text
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug --continue --stacktrace --console=plain
```

home 单元测试及 mobile/home/maphost 的 lint、Debug 构建通过。完整命令因 shared/common 中的 18 项已有测试失败而失败；失败名称与 artifacts/tire-refresh-ci.log 中的 18 项逐项一致，无新增失败。

完整日志：original-cluster-config-ci-final.log。

已检查字体比例 1.4 的完整、窄窗口及阿拉伯语布局：

- original-cluster-config-full-font-140.png
- original-cluster-config-compact-font-140.png
- original-cluster-config-arabic-font-140.png

尚未进行实车验证。

## 测试安装包

文件：DiPlay-HS5-original-cluster-config.apk

大小：39,229,636 字节。

SHA-256：08D6A8EA63F8BF2D82994E197521A26284BF69B9A1E843B55350BC7FA841AC9D
