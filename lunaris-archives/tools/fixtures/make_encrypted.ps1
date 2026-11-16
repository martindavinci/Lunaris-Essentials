# Regenerates the encrypted fixtures with 7-Zip (C:\Program Files\7-Zip\7z.exe). Password: "Lunaris-2026".
# Run from lunaris-archives. 7-Zip stamps the current time, so the files differ byte-wise on every run;
# the tests check contents, not bytes.
$ErrorActionPreference = "Stop"
$7z = "C:\Program Files\7-Zip\7z.exe"
$out = Resolve-Path "app/src/test/resources/fixtures"
$src = Join-Path $env:TEMP "lunaris-enc-src"
Remove-Item -Recurse -Force $src -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$src/docs" | Out-Null
Set-Content -NoNewline -Encoding ascii -Path "$src/docs/secret.md" -Value "# Secret notes`n"
Set-Content -NoNewline -Encoding ascii -Path "$src/readme.txt" -Value "plain readme`n"
$p = "-pLunaris-2026"
$targets = "aes.zip", "zipcrypto.zip", "deflate64-aes.zip", "data-enc.7z", "head-enc.7z", "ppmd-enc.7z"
$targets | ForEach-Object { Remove-Item -Force (Join-Path $out $_) -ErrorAction SilentlyContinue }
Push-Location $src
try {
    & $7z a -tzip -mem=AES256 $p "$out/aes.zip" docs readme.txt | Out-Null
    & $7z a -tzip -mem=ZipCrypto $p "$out/zipcrypto.zip" docs readme.txt | Out-Null
    & $7z a -tzip -mm=Deflate64 -mem=AES256 $p "$out/deflate64-aes.zip" docs | Out-Null
    & $7z a -t7z -mhe=off $p "$out/data-enc.7z" docs readme.txt | Out-Null
    & $7z a -t7z -mhe=on $p "$out/head-enc.7z" docs readme.txt | Out-Null
    & $7z a -t7z -m0=PPMd -mhe=off $p "$out/ppmd-enc.7z" docs | Out-Null
} finally {
    Pop-Location
    Remove-Item -Recurse -Force $src
}
"ok"
