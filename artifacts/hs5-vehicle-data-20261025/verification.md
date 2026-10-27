# HS5 仪表车况数据接入验证（2026-10-25）

数据规格来源：用户提供的 `D:/文档/hs5-vehicle-integration-kit`。
适用车辆为红旗 HS5 NEU / Qisi 322200（7901010DD52，Android 9）。
在项目中实现所需的只读协议，没有引入整套 SDK AAR 或厂商 framework JAR。

## 已接入

- 车速：Qisi Adapter 事务 7；推送回调 10。
- 转速：系统 RpcManager 只读 opcode 1872，帧 ID 0；payload bytes 3/4 小端乘 0.25。
- 水温：同一只读 RPC，帧 ID 24；payload byte 7，摄氏度。
- 示宽灯：复用原有事务 19 的日夜状态来源，不重复创建灯光轮询。
- 挡位、剩余油量、驾驶员安全带、制动的原始读数进入开启日志后的诊断报告。

所有车况读取都在独立工作线程，每 500 ms 读取一次，支持车速/挡位/油量推送。
仪表可见时启动，最后一个仪表窗口隐藏或退出时停止轮询、注销推送并解绑。
超过 2500 ms 未更新的数据失效，不保存车况供下次启动使用。
轮询失败不会擦掉尚未过期的推送值，也不会延长旧值的有效期。
绑定最多等待 8 秒，失败后间隔 3 秒重试，旧绑定的回调不能覆盖新状态。
关闭会话日志时不构造/写入本模块的诊断记录；实际车况读取仍用于显示。

## 未擅自推断的信号

接入包没有给出完整 PRND 枚举、油量单位/量程、安全带值的极性或制动是否为驻车制动。
仅按包内定义识别挡位 0 为 P（页面没有恢复已移除的中间挡位数字区域）。
未把原始油量当百分比，未把制动踏板值当驻车警示，未猜测安全带警示。
环境温度、驾驶模式、近光灯、ADAS 车道/前车/跟车距离等不在这份协议中，保持不可用。
左侧明确提示“辅助驾驶数据未接入”；不会伪造前车或绿色跟车格。

## 自动验证

最终版本共 32 项仪表相关测试通过，0 失败：

| 测试类 | 数量 |
| --- | ---: |
| Hs5VehicleProtocolTest（新增） | 8 |
| Hs5VehicleMonitorTest（新增） | 6 |
| ClusterDashboardViewTest | 7 |
| OemClusterGeometryTest | 5 |
| OemClusterCompositingTest | 2 |
| Hs5ClusterActivityOutputTest | 3 |
| ClusterDashboardScreenshotTest | 1 |

测试覆盖帧长度/ID/字节序、无效值、P 挡低转速门控、真实怠速保留、独立字段过期、
失败 getter 隔离、推送和轮询兼容、单向 Binder 回调、注销、超时重试、旧回调隔离、
主线程/工作线程分工，以及日志关闭后无诊断输出。

已运行 AGENTS.md 要求的完整 CI 命令（使用项目的 Windows Gradle wrapper）：

```text
gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest
  :mobile:lintDebug :home:lintDebug :maphost:lintDebug
  :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
```

最终日志：`.gradle/codex-validation-20261020/hs5-vehicle-final-ci-20261025.log`。
构建输出重定向到 `.gradle/codex-validation-20261020/outputs/`，未使用认证资源运行 CI。

- shared：1457 项，3 项失败。
- common：1150 项，15 项失败，39 项跳过。
- home 单元测试任务、mobile/home/maphost 的 lintDebug 和 assembleDebug 均完成。
- 因 shared/common 的已知失败，全量 CI 整体结果为失败，不能称为全绿。
- 与上版 `oem-wide-map-full-ci-20261025.log` 逐项比对，失败名称完全相同：18 项，新增失败 0。
- `git -c core.safecrlf=false diff --check` 通过。
- `scripts/check_public_tree.py` 通过。

已知失败涉及热点配置事务、热点回退、日夜诊断、连接阶段文案、设置恢复、视频宿主生命周期、
启动诊断快照及 Wi-Fi P2P 旧固件测试；没有修改这些无关实现来隐藏失败。

## 画面检查

本目录包含全尺寸/紧凑、日间/夜间、等待状态及阿拉伯语截图。
已人工检查 `vehicle-integration-fixture-only.png` 的车速 88 km/h、转速 2100 rpm、水温 90℃ 映射。
这些数值仅在测试源代码中，不会作为安装包默认数据，不代表实车采样。
仪表和地图的布局、顶部栏/底部栏高度保持原样。

## 实车确认

尚未在真实车机上运行这次新增的 Qisi/CAN 读取。
建议对比原车车速、怠速/变化转速、水温和示宽灯；关闭/重新打开飞屏确认恢复。
需要诊断时先开启“记录会话日志”，报告新增 `HS5 instrument vehicle data` 部分，
包含连接状态、映射后的车速/转速/水温、未映射的原始值、RPC 可用性及 CAN 数据年龄。

安装包的构建、签名和摘要验证结果见下方交付记录。

## 交付记录

- 安装包：`D:/Projects/DiPlay/DiPlay-1.2.1-hs5-20261025-vehicle-data.apk`。
- 版本：1.2.1（1201）；包名：`com.shihab.diplay`。
- 文件大小：28,560,569 字节，约 27.24 MiB。
- SHA-256：`214679B2A1F0802631EBE002CE66995F8FA809531DBCF07CCE8EE3996E71EAF0`。
- `:mobile:assembleRelease` 成功，日志为 `.gradle/codex-validation-20261020/hs5-vehicle-release-20261025.log`。
- `apksigner verify --verbose --print-certs` 通过，APK Signature Scheme v2。
- 签名证书 SHA-256：`359e689fd876a64c7d635a9acf4d307bbec6090fcb93f3e172ae5cceddbbc4f5`，与此前交付一致。
- 仅检查了所需离线认证条目的存在和非空，没有读取或输出其内容。
- 合并后的 Release Manifest 已包含 Qisi 只读权限。
- 原先的 `DiPlay-1.2.1-hs5-20261025-oem-wide-map.apk` 未覆盖。
