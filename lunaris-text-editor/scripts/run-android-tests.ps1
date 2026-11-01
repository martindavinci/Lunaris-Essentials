<#
.SYNOPSIS
  Run the Lunaris Text Editor test suite automatically on this PC.

.DESCRIPTION
  1. Boots the `lunaris_api36` emulator headless if no device is attached.
  2. Waits for boot to complete.
  3. Clears the app's stored data (deterministic state for UI tests).
  4. Runs JVM unit tests + on-device instrumented tests.

  The instrumented tests include StartupSmokeTest (catches app-init crashes) and
  SafTreeBrowserInstrumentedTest (catches the document-vs-tree URI crash) — the kinds
  of runtime failures that JVM unit tests cannot surface.

.NOTES
  Requires the `lunaris_api36` AVD (system image: android-36;google_apis_playstore;x86_64).
  Run from the repo root:  powershell -ExecutionPolicy Bypass -File scripts\run-android-tests.ps1
#>

$ErrorActionPreference = "Stop"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"
$emulator = "$sdk\emulator\emulator.exe"
$avd = "lunaris_api36"
$pkg = "io.github.martindavinci.lunaris.texteditor"

function Get-BootedDevice {
    $line = (& $adb devices | Select-String -Pattern "^emulator-\d+\s+device").Line
    if ($line) { return ($line -split "\s+")[0] }
    return $null
}

# 1. Ensure an emulator is running.
if (-not (Get-BootedDevice)) {
    Write-Host "Booting emulator $avd ..." -ForegroundColor Cyan
    Start-Process $emulator -ArgumentList "-avd",$avd,"-no-window","-no-snapshot","-no-boot-anim","-gpu","swiftshader_indirect","-no-audio"
    & $adb wait-for-device
    Write-Host "Waiting for boot to complete ..." -ForegroundColor Cyan
    $deadline = (Get-Date).AddSeconds(300)
    do {
        Start-Sleep -Seconds 5
        $b = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
    } while ($b -ne "1" -and (Get-Date) -lt $deadline)
    if ($b -ne "1") { throw "Emulator failed to boot within 5 minutes." }
}
Write-Host "Device ready." -ForegroundColor Green

# 2. Reset app state so UI tests are deterministic (no leftover prefs/session).
& $adb shell pm clear $pkg | Out-Null

# 3. Run unit tests + instrumented tests.
Write-Host "Running JVM unit tests ..." -ForegroundColor Cyan
& "$PSScriptRoot\..\gradlew.bat" :app:testDebugUnitTest --console=plain
if ($LASTEXITCODE -ne 0) { throw "Unit tests failed." }

Write-Host "Running instrumented tests on device ..." -ForegroundColor Cyan
& "$PSScriptRoot\..\gradlew.bat" :app:connectedDebugAndroidTest --console=plain
if ($LASTEXITCODE -ne 0) { throw "Instrumented tests failed." }

Write-Host "ALL TESTS PASSED" -ForegroundColor Green
