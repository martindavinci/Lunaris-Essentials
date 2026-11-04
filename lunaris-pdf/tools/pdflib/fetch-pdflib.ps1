# Copies pdf-lib into app/src/main/assets/viewer/ from the package named in manifest.json.
# The download must match the recorded SHA-256.
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
    if ($hash -ne $manifest.sha256) {
        throw "SHA-256 mismatch: expected $($manifest.sha256), got $hash"
    }

    & tar -xzf $archive
    if ($LASTEXITCODE -ne 0) { throw 'tar failed' }
    Pop-Location

    Copy-Item (Join-Path $work "package\$($manifest.from)") (Join-Path $target $manifest.to) -Force
    Write-Output "pdf-lib $($manifest.version) copied to $target"
} finally {
    if ((Get-Location).Path -eq $work) { Pop-Location }
    Remove-Item $work -Recurse -Force
}
