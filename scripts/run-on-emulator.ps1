# Build and install :mobile:debug on the 1920x720 AVD (or any running emulator).

# Usage: .\scripts\run-on-emulator.ps1

# Optional: $env:DIPLAY_AUTH_ASSETS_DIR for MFi assets in the debug APK.



$ErrorActionPreference = "Stop"

$Sdk = "$env:LOCALAPPDATA\Android\Sdk"

$adb = Join-Path $Sdk "platform-tools\adb.exe"

$emulator = Join-Path $Sdk "emulator\emulator.exe"

$Root = Split-Path $PSScriptRoot -Parent



$serial = & $adb devices | Select-String "emulator-\d+\s+device" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1

if (-not $serial) {

    Write-Host "No running emulator. Starting DiPlay_1920x720 ..."

    if (-not (Test-Path $emulator)) { throw "Android SDK emulator not found at $emulator" }

    Start-Process -FilePath $emulator -ArgumentList @("-avd", "DiPlay_1920x720", "-no-snapshot-load", "-gpu", "auto") | Out-Null

    & $adb wait-for-device

    $deadline = (Get-Date).AddMinutes(3)

    do {

        Start-Sleep -Seconds 3

        $boot = & $adb shell getprop sys.boot_completed 2>$null

        if ($boot -match "1") { break }

    } while ((Get-Date) -lt $deadline)

    $serial = & $adb devices | Select-String "emulator-\d+\s+device" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1

    if (-not $serial) { throw "Emulator did not finish booting." }

}



if (-not $env:DIPLAY_AUTH_ASSETS_DIR) {

    $defaultAssets = Join-Path $Root "runtime-assets"

    if (Test-Path (Join-Path $defaultAssets "offline-mfi\identity.pk8")) {

        $env:DIPLAY_AUTH_ASSETS_DIR = $defaultAssets

    }

}



$env:ANDROID_SERIAL = $serial

Push-Location $Root

try {

    & .\gradlew :mobile:installDebug

    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & $adb -s $serial shell am start -n "com.shihab.diplay.hudtest/com.shilapi.xcertplay.newui.NewUiActivity"

} finally {

    Pop-Location

}


