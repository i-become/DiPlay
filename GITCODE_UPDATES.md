# GitCode 在线升级

更新主源是公开仓库 `https://gitcode.com/liujing_1998/DiPlay`。
手动检查和后台检查共用同一实现；APK 下载、SHA-256 校验和系统安装器沿用现有流程。
更新检查不改变 CarPlay 使用的网络，也不提前到连接启动阶段运行。
已安装的旧版仍使用旧更新地址，需要先手动安装一次包含此改动的新客户端，之后才会优先检查 GitCode。

## 发布方法

1. 构建准备发布的 mobile APK。升级包须使用原应用相同的包名、签名，并递增 versionCode。
2. 使用与 APK versionName 一致的版本标签，例如 `v1.0.52`。更新检查比较标签中的三段版本号；仅增加 versionCode 或修改测试后缀不会触发更新。
3. 为最终 APK 生成 `SHA256SUMS.txt`。每行格式是 SHA-256、两个空格、附件文件名。
4. 在 GitCode 仓库创建 Release，把最终 APK 和 `SHA256SUMS.txt` 都作为发行附件上传；只推送代码或创建 Tag 不够。每个版本仅放一个供本应用升级的 APK，源码压缩包不影响检查。
5. 用未登录的浏览器确认版本和两个附件都可访问。校验文件中写的 APK 文件名必须与附件名完全一致。
6. 在旧版本应用里检查更新、下载并安装，完成首次实测。

Windows PowerShell 示例（在最终 APK 所在目录执行；替换文件名）：

```powershell
$releaseApk = Get-Item -LiteralPath './DiPlay-1.0.52.apk'
$releaseSha256 = (Get-FileHash -LiteralPath $releaseApk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
"$releaseSha256  $($releaseApk.Name)" | Set-Content -LiteralPath './SHA256SUMS.txt' -Encoding ascii
```

不要把 API 令牌放进 APK、公开源码或更新附件。应用读取公开发行版无需携带发布令牌。

## 请求和回退规则

主源：

```text
https://api.gitcode.com/api/v5/repos/liujing_1998/DiPlay/releases?per_page=20&direction=desc
```

- 使用标准 `application/json`，读取 `tag_name` 与 `assets[].name/browser_download_url`。
- 在返回的发行版中选择版本号最高、同时带 APK 和校验文件的版本，跳过草稿、缺失附件和不可比较的版本标签。
- HTTP 错误、连接错误或无效 JSON 才回退到原 GitHub 发行接口；使用后台检查所选的同一个联网连接工厂。
- GitCode 正常返回空列表、没有完整安装包或版本不高于当前安装版本时，不访问 GitHub，避免用另一个仓库覆盖当前主源的发布决定。
- 两个源都失败时按检查失败处理，不报告“已是最新版本”。未跳过校验，也不因限流自动重试轰炸主源。

## 首次接入验证（2026-10-20）

- 仓库页面 HTTP 200，页面元数据显示公开仓库、默认分支 main、Release 数量 0。
- 匿名发行 API 本次返回 HTTP 429，响应说明匿名代理账号受到限流。因此代码接入完成不等于 GitCode 下载链路已经实测成功。
- 仓库尚无发行附件，无法核验真实 APK 下载、重定向和安装。需要首次 Release 发布后完成上述第 5–6 步。

官方接口说明：

- `https://docs.gitcode.com/docs/apis/get-api-v-5-repos-owner-repo-releases`
- `https://docs.gitcode.com/docs/apis/get-api-v-5-repos-owner-repo-releases-attach-files-file-name-download`
- `https://docs.gitcode.com/docs/apis/post-api-v-5-repos-owner-repo-releases`
