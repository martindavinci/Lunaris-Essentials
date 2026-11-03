# Regenerates app/src/main/assets/viewer/ from the pdfjs-dist package named in manifest.json.
# The download must match the recorded SHA-256. viewer.html and bridge.js are ours and stay.
$ErrorActionPreference = 'Stop'

$manifest = Get-Content (Join-Path $PSScriptRoot 'manifest.json') -Raw | ConvertFrom-Json
$target = Join-Path $PSScriptRoot '..\..\app\src\main\assets\viewer'
$work = Join-Path ([IO.Path]::GetTempPath()) ([IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Path $work | Out-Null

try {
    Push-Location $work
    & npm pack "$($manifest.package)@$($manifest.version)" --silent | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'npm pack failed' }
    $archive = (Get-ChildItem -Filter '*.tgz' | Select-Object -First 1).Name

    $hash = (Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($manifest.sha256 -and $hash -ne $manifest.sha256) {
        throw "SHA-256 mismatch: expected $($manifest.sha256), got $hash"
    }
    if (-not $manifest.sha256) { Write-Output "sha256 to record in manifest.json: $hash" }

    & tar -xzf $archive
    if ($LASTEXITCODE -ne 0) { throw 'tar failed' }
    Pop-Location

    New-Item -ItemType Directory -Force -Path $target | Out-Null
    foreach ($entry in $manifest.files) {
        Copy-Item (Join-Path $work "package\$($entry.from)") (Join-Path $target $entry.to) -Force
    }
    foreach ($folder in $manifest.folders) {
        $destination = Join-Path $target $folder
        if (Test-Path $destination) { Remove-Item $destination -Recurse -Force }
        Copy-Item (Join-Path $work "package\$folder") $destination -Recurse
    }
    foreach ($file in $manifest.exclude) {
        Remove-Item (Join-Path $target $file) -Force
    }
    Write-Output "pdf.js $($manifest.version) copied to $target"
} finally {
    if ((Get-Location).Path -eq $work) { Pop-Location }
    Remove-Item $work -Recurse -Force
}
