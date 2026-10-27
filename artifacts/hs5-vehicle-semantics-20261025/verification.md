# HS5 最新车况字段语义对接（2026-10-25）

规格来源：`D:/文档/hs5-vehicle-integration-kit/车况字段语义.md`，更新时间 2026-10-25 15:08:52，
对应 VehicleDataLogger / SDK 1.4.0，车辆为 HS5 NEU / Qisi 322200。

本次按协议更新现有只读接入层，没有引入 SDK AAR、厂商 framework JAR 或改变应用最低系统要求。

## 字段接入

| 数据 | 来源 | 映射与显示 |
| --- | --- | --- |
| 挡位 | VehicleState(15)，事务 6；推送 11 | 传感器枚举 0..8 为 P/R/N/D/S/M/W/E/B；14/15 未连接/无效 |
| 车速 | VehicleState，事务 7；推送 10 | km/h 整数，驱动右侧指针 |
| 剩余油量 | VehicleState，事务 31；推送 30 | 0..100 百分比，254/255 初始或无效；驱动原有油表 |
| 低油量报警 | VehicleState，事务 58；推送 29 | 有效非零报警显示黄色油量图标/标记 |
| 主驾安全带 | VehicleState，事务 4 + 座位 0 | 0 未系、1 已系，其余未知；驱动顶部安全带灯 |
| 副驾安全带 | VehicleState，事务 4 + 座位 1 | 保留诊断原值；缺少座椅占用信息，不叠加到主驾报警 |
| 示宽灯 | 现有 VehicleState 事务 19 缓存 | 复用现有日夜联动来源 |
| 近光灯 | BCM(25)，事务 54 | 0 关闭、1 开启；与示宽灯独立 |
| 外温 | ATC(33)，事务 93 | 读取 float 摄氏度，不重复进行 raw /2-40 换算 |
| 驾驶模式 | DMS(18)，事务 2 | 1..10 长续航/舒适/运动/全路况/越野/自定义/经济/雪地/运动+/极限 |
| ADAS 工作状态 | IFC(46)，事务 ACC 2、FCW 8、AEB 7、LDW 14、LKA 15 | 0 关闭、1 待机、2 激活、3 故障；在左侧原车辆场景显示状态文字 |
| FCW/AEB 报警等级 | VehicleState，事务 9/10 | 0 无报警、1 提示、2 警告；有报警时显示真实等级 |
| 转速/水温 | 原有系统 RpcManager 只读 1872，CAN 帧 0/24 | 保留已验证的字节序、四分之一转速系数和 P 挡低噪声门控 |

所有扩展 manager 使用 root 事务 1 + 对应 ID 查询，并校验 Binder descriptor。
外温、近光灯、驾驶模式、ADAS 相互隔离；缺失 manager 间隔 10 秒重查，不重新绑定正常的车辆主接口。
轮询每 500 ms 在独立工作线程执行，主线程只读取不可变快照。
独立字段 2500 ms 后过期；失败轮询保留尚新鲜的旧值，但不延长其时间戳。
仪表隐藏/退出时停止轮询并注销推送，服务断开会清空旧会话状态。

## 仍需实车核对的边界

- 文档明确 carBrake 是制动开关/报警，不足以证明它是 EPB；原值进入诊断，不驱动驻车 P 灯。
- FCW/AEB 报警和 IFC WorkingSt 不代表实时前车距离或检测到的车道线，不生成虚假的前车/绿色跟车格。
- 仍按文档建议，需将油量百分比、灯光、外温、模式和 ADAS 状态与原车仪表对比。
- 没有恢复用户此前移除的中间数字车速/挡位、转向距离底栏或胎压区域。

## 测试与图像

仪表相关 39 项测试全部通过（0 失败）：

| 测试类 | 数量 |
| --- | ---: |
| Hs5VehicleProtocolTest | 12 |
| Hs5VehicleMonitorTest | 9 |
| ClusterDashboardViewTest | 7 |
| OemClusterGeometryTest | 5 |
| OemClusterCompositingTest | 2 |
| Hs5ClusterActivityOutputTest | 3 |
| ClusterDashboardScreenshotTest | 1 |

新增验证包括传感器挡位命名空间、油量边界、负数错误码/254/255、主驾安全带极性、
浮点外温/NaN/无穷/无效值、独立时效、扩展 descriptor/事务号、缺失接口限频重查、
单字段失败与恢复、后台线程和日志关闭后零新增诊断输出。

本目录 `vehicle-*-fixture-only.png` 均为自动测试数据生成的画面，已检查全尺寸、紧凑、
大字体、阿拉伯语和预警状态；它们不是实车截图，测试值不会作为生产默认值打包。
中心 SurfaceView 地图不在 View.draw 截图中，所以显示等待地图；地图的视频布局未变。

## 规格指纹

- 车况字段语义.md SHA-256：`3BA7449A8D3CBB7B1C7C46108258E298BF72AD11F03EAFE9D578FEA787DB6FCD`
- SDK 源码包 SHA-256：`E92A64D5C25C776075341E861216DC52063CB924FC9D7FD1179BC53E470C987B`

## 完整 CI

已运行 AGENTS.md / `.github/workflows/android.yml` 要求的命令，使用 Windows wrapper、
既有输出重定向 init.gradle 和 `--continue` 以执行完所有检查，未加载认证资源：

```text
gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest
  :mobile:lintDebug :home:lintDebug :maphost:lintDebug
  :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
```

- shared：1457 项，3 项失败。
- common：1157 项，15 项失败，39 项跳过。
- 本次仪表相关 39 项在完整运行中再次全部通过。
- home 测试任务、mobile/home/maphost 的 lintDebug 和 assembleDebug 均通过。
- 与 `hs5-vehicle-final-ci-20261025.log` 比较，失败名称完全相同，共 18 项，新增失败 0。
- 整体 CI 因已有的 shared/common 测试失败而失败，不代表全量通过。
- 已有失败涉及热点事务/回退、日夜诊断、连接阶段文字、设置恢复、视频宿主生命周期、启动诊断和 Wi-Fi P2P。
- `git -c core.safecrlf=false diff --check` 和 `scripts/check_public_tree.py` 通过。

本次定向测试日志：`.gradle/codex-validation-20261020/hs5-vehicle-semantics-targeted-20261025.log`。
完整日志：`.gradle/codex-validation-20261020/hs5-vehicle-semantics-full-ci-20261025.log`。
Release 日志：`.gradle/codex-validation-20261020/hs5-vehicle-semantics-release-20261025.log`。

## 安装包

- 文件：`D:/Projects/DiPlay/DiPlay-1.2.1-hs5-20261025-vehicle-semantics.apk`
- 大小：28,585,833 字节。
- SHA-256：`788A4DFA55C459E260B2BE045401CCF6AEF96D269D4968B2F5DF0B29F1D3BCB1`
- applicationId：`com.shihab.diplay`；versionName：1.2.1；versionCode：1201。
- 最低 API 25，目标 API 37，保持原有要求。
- Release 构建通过；apksigner 验证通过，v2 签名有效，与此前车机包使用同一证书。
- 签名证书 SHA-256：`359e689fd876a64c7d635a9acf4d307bbec6090fcb93f3e172ae5cceddbbc4f5`。
- 已检查离线认证资源存在且非空；未在日志或本记录中输出私有内容。

尚未进行实车验证。安装后请对比原车的油量、主驾安全带、外温、驾驶模式、
示宽灯/近光灯和 ACC/LKA 状态。需要排查时再开启“记录会话日志”，
报告已包含各字段原值、映射结果及扩展接口可用状态；关闭时不新增诊断记录。
