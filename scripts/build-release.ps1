param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Version
)

$Version = $Version.TrimEnd([char[]]@('\', '/'))
if ($Version -notmatch '^(0|[1-9]\d*)[.](0|[1-9]\d*)[.](0|[1-9]\d*)$') {
    throw '版本号必须为主版本.次版本.补丁版本，例如 1.4.3。'
}

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$previousAuthAssets = $env:DIPLAY_AUTH_ASSETS_DIR
Push-Location $repoRoot
try {
    $versionParts = $Version.Split('.') | ForEach-Object { [int]$_ }
    $major, $minor, $patch = $versionParts
    if ($minor -gt 9 -or $patch -gt 99 -or $major -gt 2000000) {
        throw '版本号需符合主版本.0-9.次版本.0-99 补丁号的编码范围。'
    }
    $versionCode = ($major * 1000) + ($minor * 100) + $patch

    if (-not $env:DIPLAY_AUTH_ASSETS_DIR) {
        $defaultAssets = Join-Path $repoRoot 'runtime-assets'
        if (Test-Path -LiteralPath $defaultAssets) {
            $env:DIPLAY_AUTH_ASSETS_DIR = $defaultAssets
        }
    }
    if (-not $env:DIPLAY_AUTH_ASSETS_DIR) {
        throw '未设置 DIPLAY_AUTH_ASSETS_DIR，且仓库 runtime-assets 不存在。'
    }

    $authRoot = (Resolve-Path -LiteralPath $env:DIPLAY_AUTH_ASSETS_DIR).Path
    $identity = Join-Path $authRoot 'offline-mfi/identity.pk8'
    $certificate = Join-Path $authRoot 'offline-mfi/certificate.p7b'
    foreach ($asset in @($identity, $certificate)) {
        if (-not (Test-Path -LiteralPath $asset -PathType Leaf)) {
            throw "CarPlay 认证材料不完整：缺少 $(Split-Path $asset -Leaf)"
        }
        if ((Get-Item -LiteralPath $asset).Length -eq 0) {
            throw "CarPlay 认证材料为空：$(Split-Path $asset -Leaf)"
        }
    }
    $env:DIPLAY_AUTH_ASSETS_DIR = $authRoot

    Write-Host "构建 DiPlay $Version（versionCode $versionCode）Release…"
    & (Join-Path $repoRoot 'gradlew.bat') ':mobile:assembleRelease' `
        "-PdiplayVersionName=$Version" "-PdiplayVersionCode=$versionCode"
    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败，退出码 $LASTEXITCODE" }

    $apk = Join-Path $repoRoot 'mobile/build/outputs/apk/release/mobile-release.apk'
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        $apk = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'mobile/build/outputs/apk/release') `
            -Filter '*.apk' -File | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $apk) { throw 'Release APK 未生成。' }

    $metadataPath = Join-Path $repoRoot 'mobile/build/outputs/apk/release/output-metadata.json'
    if (-not (Test-Path -LiteralPath $metadataPath)) { throw '缺少 APK 输出元数据。' }
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $element = $metadata.elements | Where-Object { $_.outputFile -eq (Split-Path $apk -Leaf) } | Select-Object -First 1
    if (-not $element) { $element = $metadata.elements | Select-Object -First 1 }
    if ($element.versionName -ne $Version -or [long]$element.versionCode -ne $versionCode) {
        throw "APK 版本不匹配：期望 $Version ($versionCode)，实际 $($element.versionName) ($($element.versionCode))"
    }
    if ($metadata.applicationId -ne 'com.shihab.diplay') {
        throw "APK 包名不匹配：$($metadata.applicationId)"
    }

    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk) {
        $localPropertiesPath = Join-Path $repoRoot 'local.properties'
        if (Test-Path -LiteralPath $localPropertiesPath) {
            $sdkLine = Get-Content -LiteralPath $localPropertiesPath |
                Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
            if ($sdkLine) {
                $sdk = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
            }
        }
    }
    if (-not $sdk) { throw '找不到 Android SDK，请设置 ANDROID_HOME。' }
    $buildToolsRoot = Join-Path $sdk 'build-tools'
    $buildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $buildTools) { throw 'Android SDK 中找不到 build-tools。' }
    $apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
    if (-not (Test-Path -LiteralPath $apksigner)) { throw '缺少 apksigner.bat。' }
    & $apksigner verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw 'Release APK 签名校验失败。' }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $entryNames = @($archive.Entries | ForEach-Object FullName)
        foreach ($expected in @('assets/offline-mfi/identity.pk8', 'assets/offline-mfi/certificate.p7b')) {
            if ($entryNames -notcontains $expected) { throw "APK 未包含认证资产：$expected" }
        }
    }
    finally { $archive.Dispose() }

    $outputDirectory = Join-Path $repoRoot "dist/release-$Version"
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $outputApk = Join-Path $outputDirectory "DiPlay-$Version.apk"
    Copy-Item -LiteralPath $apk -Destination $outputApk -Force
    $sha256 = (Get-FileHash -LiteralPath $outputApk -Algorithm SHA256).Hash.ToLowerInvariant()
    "$sha256  DiPlay-$Version.apk" |
        Set-Content -LiteralPath (Join-Path $outputDirectory 'SHA256SUMS.txt') -Encoding ascii

    Write-Host ''
    Write-Host '生产包已完成：' -ForegroundColor Green
    Write-Host "APK:    $outputApk"
    Write-Host "SHA256: $sha256"
    Write-Host "大小:   $((Get-Item -LiteralPath $outputApk).Length) bytes"
}
finally {
    if ($null -eq $previousAuthAssets) {
        Remove-Item Env:DIPLAY_AUTH_ASSETS_DIR -ErrorAction SilentlyContinue
    }
    else {
        $env:DIPLAY_AUTH_ASSETS_DIR = $previousAuthAssets
    }
    Pop-Location
}
