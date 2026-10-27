# HS5 仪表重复显示与错误数值隔离（2026-10-25）

## 本版范围

这是部分修复和错误读数隔离版本，尚不能作为六项问题全部解决的生产版本。

依据报告：`D:/xwechat_files/wxid_jsqk58gaw70d22_8b8a/msg/file/2026-10/DiPlay-20261025-181833.txt`。

- 报告确认副屏为 1920×720，`overlay display=1 type=2038` 已成功启用；因此不能再将原车速度块残留归因于尚未切换悬浮窗口。
- 车辆快照只有停车后的 `speed=0`、P 挡，当前 CAN 公式给出 `rpmRaw=145.5`、`coolantC=53.0`。没有同步行驶原整数与原车时速、转速、水温对照，不能确定正确比例或字节位。
- 当前接入包仍将 Vehicle15/getter7、push10 定义为整数 km/h，并将 CAN 转速定义为 payload[3:4] 小端 ×0.25、水温定义为 payload[7]。代码与包内示例相符，但实车反馈否定了这些读数的可靠性。本版没有猜测除以 10/100，也没有尝试未经证实的 CAN 偏移。
- QISI 导航全屏 mode=4 和导航请求已在上一版使用。现有资料没有给出独立隐藏原车速度块、原车底部水温/油量条的已验证接口。本版不调用未知事务号、模式或特权窗口类型。

## 已实现

1. 删除应用自行绘制的时钟、驾驶模式、安全带、灯光及温度顶部栏，让原车状态栏成为唯一一组；保留原有仪表和地图分区。
2. 删除应用自行绘制的水温/油量弧线及小刻度。原车合成层自身的底部条尚未关闭，不能将这一项标记为已全部解决。
3. 两个表盘内部使用更大的图标和数字：油量用百分比，例如报告中的 22%；低油量时使用警示色。水温未核准时显示 `—°C`。
4. 将显示策略与原始采样分开：HS5 车速仅保留已确认的零值，非零值不再驱动指针；转速、水温暂不显示未经核准的数值。缺失车速、转速以 `—` 表示。此措施阻止错误读数，并没有修复单位与信号定义。已存在的 P 挡低转速过滤不能证明刹车就是原因；本报告不足以判断两者的因果。
5. 开启会话日志时保留最多 64 次速度原值变化，以及最多 600 条、约每秒一次的车速/挡位/刹车/CAN 原帧与公式候选值。关闭、重开飞屏不清除本轮证据；关闭日志立即清除，重新开启不会恢复旧记录。日志关闭时不采样、保留或格式化诊断历史；正常仪表所需读取不受这一开关控制。
6. 原先将多行车辆报告直接送入启动日志，会被隐私过滤器拒绝。现在启动日志只接收有界单行摘要，原始帧只进入明确导出的车辆证据段；没有放宽隐私过滤规则。

## 尚缺的依据

- 本车固件经过确认的车速单位、CAN 转速与水温信号定义，优先需要生成该接入包的 VehicleDataLogger / 原车解析工程。
- 原车仪表合成层隐藏速度块及底部状态条的实际接口或实现。
- 这两项取得之前，不能承诺真实车速、转速、水温显示正确，或承诺完全去除原车叠层。

## 视觉检查

- 使用 apple-design 的对比度、读数辨识原则放大辅助读数，没有重新设计仪表布局。
- `full-fixture-only.png`：1920×720；`compact-fixture-only.png`：1280×480；`arabic-fixture-only.png`：1920×720 阿拉伯语配置。均为字体配置 1.4 的离线测试夹具。
- 夹具使用未校准的 42 km/h、145.5 rpm、53°C 候选值验证显示策略，实际显示 `—` / `—°C`，油量夹具为 22%。这些数值不是本次上车观测数据。
- 离线截图不包含原车合成层，也不能捕获 SurfaceView 的真实 CarPlay 视频；不能用来证明原车速度块已消失。本版尚未上车验证。

## 验证结果

- 相关筛选测试 48 项通过，包括采样监听、原始证据保留/容量/关闭清除、显示隔离、合成及大字体截图。
- 按 AGENTS.md / `.github/workflows/android.yml` 执行完整 CI：shared 1457 项、3 失败；common 1182 项、15 失败、39 跳过；home 4 项通过。三个端的 lintDebug 与 assembleDebug 均完成。
- 完整 CI 仍失败。失败的 18 个用例名称与上一版 `hs5-front-warning-final-ci-20261025.log` 排序逐项比较完全相同，没有新增失败；本次日志为 `.gradle/codex-validation-20261020/hs5-gauge-guard-ci-20261025.log`。
- `git -c core.safecrlf=false diff --check` 与 `python scripts/check_public_tree.py` 已通过。

## 安装包

- 文件：`D:/Projects/DiPlay/DiPlay-1.2.1-hs5-20261025-gauge-guard.apk`，28,585,833 字节。保留此前安装包。
- SHA-256：`8B27B2FB9D07514E7A9E4408BE6E7A44F0A409E8E45B76667C67C4DC57E7CCBF`。
- `:mobile:verifyStandaloneAuthentication :mobile:assembleRelease` 成功；日志 `.gradle/codex-validation-20261020/hs5-gauge-guard-release-20261025.log`。
- applicationId `com.shihab.diplay`；versionName 1.2.1；versionCode 1201；minSdk 25、targetSdk 37。
- APK v2 签名验证通过；证书 SHA-256 `359e689fd876a64c7d635a9acf4d307bbec6090fcb93f3e172ae5cceddbbc4f5` 与此前车机包一致。
- 两项必要运行资源存在且非空，未输出其内容。dex 已核实包含 `Hs5VehicleDisplayPolicy`、`Hs5VehicleEvidence`、`speedUnits=unverified-on-road`、`rpmFormula=unverified-on-road`、`HS5_VEHICLE state=`、`vehicleEvidence=[`，确认交付包包含本次实现。
- 本版用于检查应用自身绘制与诊断保留行为，不是车速/转速/水温已校准的生产包。未安装到车机、上传或发布。
