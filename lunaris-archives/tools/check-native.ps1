# Checks the release APK: the engine library is there for every ABI and every LOAD segment is 16 KB aligned.
# Run from lunaris-archives: powershell -ExecutionPolicy Bypass -File tools\check-native.ps1
param([string]$Apk = "app/build/outputs/apk/release/app-release.apk")
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$abis = @("arm64-v8a", "armeabi-v7a", "x86_64")
$ndk = "27.2.12479018"
$sdkLine = Get-Content local.properties | Where-Object { $_ -like "sdk.dir=*" } | Select-Object -First 1
if (-not $sdkLine) { throw "local.properties has no sdk.dir" }
# local.properties escapes the colon and every backslash.
$sdk = ($sdkLine -replace '^sdk\.dir=', '').Replace('\:', ':').Replace('\\', '\')
$readelf = Join-Path $sdk "ndk/$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe"
if (-not (Test-Path $readelf)) { throw "llvm-readelf not found at $readelf" }
if (-not (Test-Path $Apk)) { throw "no APK at $Apk (run :app:assembleRelease first)" }

$tmp = Join-Path $env:TEMP ("lunaris-so-" + [guid]::NewGuid())
New-Item -ItemType Directory -Force $tmp | Out-Null
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $Apk).Path)
try {
    foreach ($abi in $abis) {
        $entry = $zip.GetEntry("lib/$abi/liblunaris_engine.so")
        if ($null -eq $entry) { throw "missing lib/$abi/liblunaris_engine.so" }
        $so = Join-Path $tmp "$abi.so"
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $so, $true)
        $loads = & $readelf -Wl $so | Where-Object { $_ -match '^\s*LOAD\s' }
        if (-not $loads) { throw "$abi has no LOAD segment (readelf failed?)" }
        $bad = $loads | Where-Object { $_.TrimEnd() -notmatch '0x4000$' }
        if ($bad) { throw "$abi has a LOAD segment that is not aligned to 0x4000: $bad" }
    }
} finally {
    $zip.Dispose()
    Remove-Item -Recurse -Force $tmp
}

$buildTools = Get-ChildItem (Join-Path $sdk "build-tools") -Directory |
    Where-Object { $_.Name -match '^\d+(\.\d+)*$' } | Sort-Object { [version]$_.Name } | Select-Object -Last 1
& (Join-Path $buildTools.FullName "zipalign.exe") -c -P 16 4 $Apk
if ($LASTEXITCODE -ne 0) { throw "zipalign -P 16 check failed" }
"OK 3 libraries, all LOAD segments 0x4000"
