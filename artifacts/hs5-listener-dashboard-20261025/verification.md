# HS5 仪表布局与事件更新（2026-10-25）

## 本次改动

1. 删除应用在表盘内部绘制的水温、油量图标和数字。原车下方自带的水温、油量条继续显示，本版不再绘制第二套。
2. 删除表盘外围独立的一圈细密刻度，保留主刻度。左侧 0.5、1.5、2.5 等半千转刻度加长、加粗，仍保留整数主刻度。
3. 两个表盘直径缩小到原来的 90%，围绕原来的顶部边缘缩放。1920×720 参考布局中顶部仍为 y=13，底部约为 y=619.6，距 y=648 的底栏约 28.4 像素。地图区域仍为 x=480、y=72、宽 960、高 576；表盘与地图的遮罩、融合边缘和指针采用一致的缩放。
4. 按用户指定统一将速度原整数除以 100，再用于显示和指针动画。125 → 1.25 km/h，4300 → 43 km/h；不先取整。有效原值为 0～26000，越界或缺失值显示未知，不能驱动出错的指针。该比例来自本次用户要求，尚未与实车原仪表同步校准。
5. 删除车辆 500ms 轮询、CAN 100ms 轮询及仪表页面自身的周期刷新。监听事务 42 注册后，仅在首次绑定成功时读取一次速度、挡位、油量和低油量告警初值，随后接收 QISI 10/11/30/29 回调更新；注销使用事务 43。导航、音乐、连接状态由各自已有的事件入口通知仪表。
6. 推送仅在变化时发送的值会保留到服务断开，不再因几秒没有重复推送而变成未知。服务断开或 Binder 死亡立即清空数据并走重新绑定。界面隐藏、移除后注销订阅，忽略旧会话的迟到回调。监听注册失败时不回退到读取轮询。
7. 指针继续使用收到的数据进行逐帧插值，遵守系统关闭动画的设置；动画不读取车辆接口。绑定超时与断开后的重连延时属于连接生命周期，不是数据查询定时器。

## 接口依据与限制

- 读取了本地接入包 `D:/文档/hs5-vehicle-integration-kit/hs5-vehicle-integration-kit/` 的文档及 SDK 源码。Vehicle15 的速度、挡位、油量回调可用；现有 CAN 示例只有反射 `getMessage` 查询，没有验证过的转速订阅接口。本版不猜测新的 Binder 事务号或 CAN 回调类。
- 转速仍受显示策略隔离，显示未知；没有继续使用已被实车反馈否定的 CAN 公式。前车识别/距离等扩展字段也缺少经过确认的事件接口，扩展状态轮询已移除，不生成虚假的前车目标或距离。这些字段的实时监听尚未完成，须取得可验证的事件协议。
- 水温和油量直接使用原车合成层的下方显示。顶部原车状态栏继续由原车负责，本版不画第二组时间、驾驶模式、灯光、安全带或温度。
- 本次没有新增原车合成层隐藏接口，不能声称此前原车左侧速度块问题已解决。没有改变原车水温/油量数据的解析。
- 会话诊断仍受开关控制；正常速度监听不依赖日志。导出报告读取缓存，不为报告额外查询车辆。关闭日志不生成或保留诊断历史。

## 视觉检查

- 按 apple-design 的可辨识刻度与留白原则检查现有 OEM 仪表，不重新设计界面。
- `full-fixture-only.png`：1920×720；`compact-fixture-only.png`：1280×480；`arabic-fixture-only.png`：阿拉伯语 1920×720。字体配置均为 1.4。已查看三张截图。
- `map-fixture-only.png` 使用明确标注的彩色测试网格验证地图开口与表盘融合，已查看。它不是真实 CarPlay 导航。
- 行驶夹具速度原值 125，按本次规则显示 1.25 km/h；转速未知。辅助数值变化不影响任何渲染像素的测试已通过。
- 离线截图不能捕获 SurfaceView 的真实视频，也不包含原车状态栏、底部条和速度块合成层；实际上下栏、地图与仪表的最终效果仍须上车验证。

## 验证结果

- 已按 AGENTS.md 和 `.github/workflows/android.yml` 执行完整命令：

  `./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest :mobile:lintDebug :home:lintDebug :maphost:lintDebug :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug`

- 使用本机 SDK/JDK 和既有 `.gradle/codex-validation-20261020/init.gradle` 输出目录，未修改 CI 定义。
- common 中相关筛选范围共 60 项全部通过：车辆监听 9、车辆协议 16、车辆证据 2、速度证据 3、仪表视图 7、截图 1、指针动画 3、合成 7、布局 7、既有车辆语音接口 5。shared 导航事件 7 项全部通过。
- 行为测试验证：空闲一分钟不追加车辆查询、不调用 CAN；初始化期间收到的推送覆盖旧初值；无效速度清空指针；服务重连隔离旧监听；注册失败不轮询；界面隐藏不刷新；诊断关闭不保留历史；速度 0～260 km/h 的全范围指针始终可见。
- 完整 CI 仍失败：shared 1458 项、3 失败；common 1178 项、15 失败、39 跳过；home 4 项通过。mobile、home、maphost 三个端的 lintDebug 与 assembleDebug 均完成。
- 将失败用例名称与上一版 `hs5-gauge-guard-ci-20261025.log` 排序逐项比较，18 个失败完全相同，无新增失败。涉及热点事务/回退、主题诊断、连接阶段文案、主屏生命周期、启动快照及 Wi-Fi P2P。
- 完整 CI 日志：`.gradle/codex-validation-20261020/hs5-listener-ci-20261025.log`。该日志和完整测试 XML 证明最终版本的监听注册失败清理测试已通过；此前筛选运行的单项失败已修正。
- `git -c core.safecrlf=false diff --check` 和 `python scripts/check_public_tree.py` 通过。

## 安装包

- 文件：`D:/Projects/DiPlay/DiPlay-1.2.1-hs5-20261025-listener-dashboard.apk`，28,585,833 字节。此前安装包保留。
- SHA-256：`95BB794327B26B160C9C0AEEB368244D1051F62AF89ACC0FCF33CC07030C4AAE`。
- `:mobile:verifyStandaloneAuthentication :mobile:assembleRelease` 成功。日志：`.gradle/codex-validation-20261020/hs5-listener-release-20261025.log`。
- applicationId `com.shihab.diplay`；versionName 1.2.1；versionCode 1201；minSdk 25；targetSdk 37。
- APK v2 签名验证通过，证书 SHA-256：`359e689fd876a64c7d635a9acf4d307bbec6090fcb93f3e172ae5cceddbbc4f5`，与此前车机包一致。
- 两项必要运行资源存在且非空，未输出其内容；dex 包含 `dataMode=listener-only`、`speedUnits=raw-div-100-kph`、`rpmListener=unavailable`、`canPolling=disabled`、`Hs5VehicleSpeed`，确认交付包包含本次实现。
- 尚未安装到车机、上传或发布。由于转速和扩展前车状态的监听协议缺失，本版不应被描述为所有车辆字段已实时接入的生产完成版。
