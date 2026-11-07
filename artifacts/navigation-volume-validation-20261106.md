# 高德导航独立音量：车测说明

本次处理的是“导航声道已有声音，但方向盘滚轮同时改变音乐和导航音量”。实车独立调音量效果尚未验证。

## 依据与实现

- 用户日志 `DiPlay-20261106-190305.txt`：Android 9 红旗车机，导航已成功打开 legacy stream 14；音乐使用媒体用途。两者的 BUS 输出设备类型相同，不能据此认定音量流相同。
- 本地原厂 Carlink 的 TTS 请求使用 `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`、`CONTENT_TYPE_SPEECH` 和 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`。DiPlay 原先跳过导航焦点请求。
- 新增 `NavigationAudioFocusCoordinator`：仅在识别到红旗车机、启用音频焦点且导航实际使用 stream 14 播放 PCM 时请求上述临时焦点，给车机音频策略提供导航用途信息。
- 后续车测日志证明，首次导航轨道已解码并写出约 53 万 PCM 帧，播放头持续推进且 underrun 为 0；但媒体流建立前车机未输出声音。修正后，在调用 `AudioTrack.play()` 前同步请求导航焦点，确保车机先收到导航用途，再打开首帧输出。
- 焦点覆盖已写入的音频缓冲及 900ms 播报间隔；播报结束、导航路由回退、电话或 Siri 发声及断开连接时释放。拒绝或丢失焦点后不会逐包反复抢占。
- 同时跟踪多个音频流，关闭日志时也更新播放状态。避免自己的导航焦点再把音乐额外压低一次。
- 本次不直接修改系统音量，不增加方向盘按键写音量逻辑。车机是否据此正确切换滚轮的音量目标，需要实车确认。

## 验证结果

- 音频针对性测试：48 项通过（包括 Android 9 / API 28），涵盖焦点启动时序、焦点释放、电话/Siri 优先和音量索引日志。
- 已执行 AGENTS.md 要求的完整 CI 命令，并加上 `:mobile:assembleRelease`，使用 `--continue` 收集全部结果。
- 最终 shared：1490 项，3 项失败；common：1266 项，15 项失败、39 项跳过；home：4 项通过。
- 与 `.gradle/apk-size-optimization/test-baseline.json` 逐项比较：18 项失败均为原有失败，没有新增失败；完整 CI 因这些原有失败返回非零状态。
- mobile / home / maphost 的 Debug 构建和 lint 通过；mobile Release 构建及 Release lint 通过。
- 最终完整日志：`.gradle/navigation-volume-fix/final-hs5-volume-build.log`；shared 全套复测：`.gradle/navigation-volume-fix/shared-final-rerun.log`；失败对比：`.gradle/navigation-volume-fix/test-comparison.json`。

## 安装包

- 最初测试文件：`DiPlay-HS5-20261106-navigation-volume.apk`；提前请求焦点的更新版：`DiPlay-HS5-20261106-navigation-volume-v2.apk`；增加音量索引诊断的最终版：`DiPlay-HS5-20261106-navigation-volume-v3.apk`
- 大小：7,709,731 bytes，约 7.71 MB。
- 最终版 SHA-256：`C84E2C4FCA3AA5287BBE9BF42043E890DB524B18A4DF2453148A2DEDF274A7CC`
- v2 签名验证通过，与上一版签名相同；独立连接所需的本地认证资源完整，四种原有 native ABI 保留。

## v4 诊断包

新日志确认 v3 仍未解决车机行为。stream 14 的音量索引读取结果为 unavailable，Android `WheelKeyService` 状态为 wanted=false、allowed=false、bound=false。v4 只扩展诊断采样：导航播报期间记录 stream 14 和 Android 标准语音通话、系统、铃声、媒体、闹钟、通知、DTMF、无障碍各音量流的当前值/最大值变化，不写入音量、不接管滚轮。

- 文件：`DiPlay-HS5-20261106-navigation-volume-v4-diagnostics.apk`
- 大小：7,709,731 bytes；SHA-256：`3A1700B86BD9021818CBB6894A76C642CE4031292FD227EA6D378FC352B70DDD`
- 47 项焦点和音量诊断针对性测试通过；Debug 构建、各模块 lint、Release 构建和签名均通过。
- 完整测试仍为 shared 3 项、common 15 项既有失败；与既有基线比较没有新增失败。

## 真车检查

1. 覆盖安装 v3 包并重新连接 CarPlay，保持高德使用“导航声道”。
2. 播放音乐，同时在高德音量页面启动试听；在导航声音实际播放时转动方向盘音量滚轮，检查是否只改变导航音量。
3. 停止试听或播报，稍等 1–2 秒，再转动滚轮，检查是否调整音乐音量，并检查下一次播报是否保留之前的导航音量。
4. 顺带确认 Siri、电话及重新连接后声音正常。
5. 若仍同时变化，录制并导出新日志。重点核对 `Audio: navigation volume focus requested` 的授权结果和之后的释放/焦点变化记录。

“媒体”音量仍会影响导航、以及“语音”音量反应慢的具体车机音量组关系，现有日志无法判断，因此不能把这两项标记为已完全解决。v3 在导航焦点活动期间会采样 stream 14 和媒体 stream 的当前/最大音量索引，并只在索引变化时写入 `Audio: navigation volume focus volume levels`，便于下一次车测判明两组是否联动。
