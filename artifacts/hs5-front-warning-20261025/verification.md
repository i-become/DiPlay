# HS5 前向预警接口与车速原值核对（2026-10-25）

## 依据

- 新资料：`D:/文档/hs5-vehicle-integration-kit/hs5-vehicle-integration-kit/仪表前方有车提醒-接口说明.md`。
- 字段语义：同目录 `车况字段语义.md`，SDK / VehicleDataLogger 1.4.0。
- 延续现有自定义仪表与 QISI 只读接入，无车辆控制操作。

## 本次实现

1. 接入 Binder 23，校验 `ltd.qisi.adapterproxy.ICarHUDManager` descriptor；读取事务 17（HUD FCW 显示状态）、10（ADAS 显示状态）。
2. 补充 Binder 46 的事务 5（巡航模式）、27（AEB 开关/显示态）。与既有 ACC/FCW/AEB 工作状态分开保存，单字段过期，不将失败值转换成开启。
3. 沿用工作线程的 500 ms 兜底采样，主线程仅绘制缓存。HUD 服务不可用时独立重试，不重绑车辆状态服务，也不丢弃可用的速度/油量/转速数据。
4. 已明确的 Vehicle15 FCW 报警等级驱动左侧仪表警示图形：0/无效时不显示；1 黄色一级提示；2 红色加粗警示及侧线。保留现有仪表布局，不显示调试数值，不添加声音。
5. HUD FCW/ADAS、巡航模式、AEB 显示、FCW/AEB 报警及 ACC 工作状态发生观测到的变化时，开启日志才记录快照，避免仅靠五秒定期记录漏掉短暂变化。
6. 车速证据在范围检查之前采集，区分 `getter7` 与 `push10`，记录十进制原整数、十六进制、单调时钟、范围有效值和结果。最多缓存 64 次变化，同来源同值去重；行驶后回到零不会立即抹掉此前的四位数证据。日志关闭时不采集或格式化；重新开启不会恢复旧一轮证据。退出采样时清空缓存。

## 尚不能确认的部分

- 新资料未给出 HUD 监听器的注册/回调事务号和完整 Binder descriptor，本次没有猜测注册事务号。HUD/IFC 新增字段目前为后台轮询，既有车速/挡位/油量仍优先使用已确认的车辆回调。
- `getHUDCruiseStatus(int)` / `getHUDAEBWFuncStatus(int)` 所需通道参数未给枚举，本次不向未知通道调用。
- 文档没有给出 HUD FCW/ACC 原值对应“有前车”、实际距离或绿色距离格数的映射。因此新 HUD 值先作为独立状态和诊断证据，不用 ACC 激活或非零报警伪造前车/实际距离。
- 已知 FCW 报警图形并非原车前车目标图标的一比一复刻；只有取得目标/距离的确切协议后，才能驱动现有前车图标和距离格绘制。

## 车速结论

- 当前字段文档仍将 Vehicle15/getter7 和 push10 定义为整数 km/h，并明确未在 Java 层乘 0.1；没有提供本固件四位数原值的换算规则。
- 现有有效范围为 0–260。四位数会被拒绝；超过 2.5 秒没有有效样本，车速失效，指针隐藏。这是能够解释现象的代码路径，但现有报告的零速快照不能证明它是实际原因。
- 本次不擅自除以 10/100，也不把四位数直接送入表盘。要修正比例，需要同一时刻的原始整数和实际时速，或确认过的 CAN / 固件格式。
- 测试中的 4200、4300 都是构造数据，并非用户车辆已观测值。

## 视觉检查

- 预览为 1920×720、1280×480，字体配置 1.4，并包含阿拉伯语配置；图片中的车况均为测试夹具。
- 使用 apple-design 技能检查警示辨识度：一级/二级除颜色外也通过线宽及侧线区分；未改动原有仪表分区。
- 文件：`warning-full-fixture-only.png`、`warning-compact-fixture-only.png`、`warning-arabic-fixture-only.png`。
- 尚未上车验证此版本的返回值、叠层及报警图形。

## 测试与构建检查

- 按 AGENTS.md / `.github/workflows/android.yml` 运行完整 CI，包含 shared/common/home 单测及 mobile/home/maphost 的 lintDebug 和 assembleDebug。
- 最终日志：`.gradle/codex-validation-20261020/hs5-front-warning-final-ci-20261025.log`。shared：1457 项、3 失败；common：1177 项、15 失败、39 跳过；home：4 项通过。三端 lint 与 Debug 组装完成。
- 将失败用例名称与改动前 `hs5-driving-final-ci-20261025.log` 排序逐条比较，18 项完全一致，没有新增失败。完整 CI 仍为失败，不能标记为全部通过。
- 本次相关的 59 项测试全部通过，覆盖车辆监听与轮询、服务恢复、HUD 状态更新/失效、状态变化即时记录、日志关闭与代次隔离、过滤前四位车速历史、碰撞警示图形、不会虚构目标/距离、指针与仪表合成、完整/紧凑/阿拉伯语截图。
- `git -c core.safecrlf=false diff --check`、`python scripts/check_public_tree.py` 均通过。

## 安装包

- 文件：`D:/Projects/DiPlay/DiPlay-1.2.1-hs5-20261025-front-warning.apk`，28,585,833 字节。
- SHA-256：`2694C28A19B90B5671C233172B48717ED4555DC92528DB4B5F5C3643556E223F`。
- applicationId `com.shihab.diplay`；versionName 1.2.1；versionCode 1201；最低 API 25。
- Release 与 `verifyStandaloneAuthentication` 成功；日志 `.gradle/codex-validation-20261020/hs5-front-warning-release-20261025.log`。
- `apksigner verify --verbose --print-certs` 通过，v2 签名有效，证书 SHA-256 `359e689fd876a64c7d635a9acf4d307bbec6090fcb93f3e172ae5cceddbbc4f5` 与此前车机包相同。
- 必要离线运行资源存在且非空，未输出其内容。APK 的 dex 已核实包含 `Hs5SpeedEvidence`、`hudFcwDisplayRaw=`、`rangeValidKph=`、`hudIconMeaning=unverified`，确认交付包包含本次代码。
- 保留此前安装包。本版没有安装到车机、上传或发布。
