# Downloads TextMate grammars + themes per manifest.json into app/src/main/assets/textmate,
# then generates languages.json, themes.json and THIRD_PARTY_NOTICES.md.
# Run from repo root:  powershell -ExecutionPolicy Bypass -File tools\textmate\fetch-textmate-assets.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)  # repo root
$manifest = Get-Content "$PSScriptRoot\manifest.json" -Raw | ConvertFrom-Json
$assets = Join-Path $root "app\src\main\assets\textmate"
$gDir = Join-Path $assets "grammars"
$tDir = Join-Path $assets "themes"
New-Item -ItemType Directory -Force -Path $gDir, $tDir | Out-Null

$gSrc = $manifest.grammarSource
function Grammar-Url($id) { $gSrc.rawBase.Replace("{sha}", $gSrc.sha).Replace("{id}", $id) }

# Collect embed ids needed from language declarations (deduped).
$embeds = [System.Collections.Generic.HashSet[string]]::new()
foreach ($lang in $manifest.languages) {
    if ($lang.PSObject.Properties.Name -contains 'embeds') {
        foreach ($e in $lang.embeds) { [void]$embeds.Add($e) }
    }
}

$missing = @()
$catalog = @()

foreach ($lang in $manifest.languages) {
    # Skip grammar download for plaintext only; every other language downloads <id>.json
    if ($lang.id -eq 'plaintext') {
        $catalog += [ordered]@{ id = 'plaintext'; display = $lang.display; scopeName = $null; grammarAsset = $null; ext = $lang.ext }
        continue
    }
    $url = Grammar-Url $lang.id
    $dest = Join-Path $gDir "$($lang.id).json"
    try {
        Invoke-WebRequest -Uri $url -OutFile $dest -TimeoutSec 60
        $json = Get-Content $dest -Raw | ConvertFrom-Json
        $scope = $json.scopeName
        $exts = if ($lang.PSObject.Properties.Name -contains 'ext' -and $lang.ext) { $lang.ext } elseif ($json.PSObject.Properties.Name -contains 'fileTypes') { $json.fileTypes } else { @() }
        $catalog += [ordered]@{ id = $lang.id; display = $lang.display; scopeName = $scope; grammarAsset = "grammars/$($lang.id).json"; ext = $exts }
    } catch {
        Write-Warning "MISSING grammar: $($lang.id) ($url)"
        $missing += $lang.id
        if (Test-Path $dest) { Remove-Item $dest }
    }
}

# Download embeds that are not themselves user-facing languages (and not already downloaded).
$downloadedIds = ($catalog | Where-Object { $_.grammarAsset -ne $null } | ForEach-Object { $_.id })
foreach ($id in $embeds) {
    if ($downloadedIds -contains $id) { continue }
    $dest = Join-Path $gDir "$id.json"
    if (Test-Path $dest) { continue }
    try { Invoke-WebRequest -Uri (Grammar-Url $id) -OutFile $dest -TimeoutSec 60 }
    catch { Write-Warning "MISSING embed grammar: $id" }
}

# Upstream xml.json nests "end" and "name" of its <%-- rule inside "captures". tm4e casts every
# capture to a rule and throws, leaving XML and every grammar that includes it without highlighting.
function Repair-XmlGrammar([string]$text) {
    $broken = '("begin": "<%--",\s*"captures": \{\s*"0": \{\s*"name": "punctuation\.definition\.comment\.xml"\s*\}),\s*"end": "--%>",\s*"name": "comment\.block\.xml"(\s*)\}'
    $text -replace $broken, '$1$2},$2"end": "--%>",$2"name": "comment.block.xml"'
}
$xmlGrammar = Join-Path $gDir "xml.json"
if (Test-Path $xmlGrammar) {
    $original = [IO.File]::ReadAllText($xmlGrammar)
    $repaired = Repair-XmlGrammar $original
    if ($repaired -ceq $original) { Write-Warning "xml.json: the <%-- rule was not repaired" }
    [IO.File]::WriteAllText($xmlGrammar, $repaired)
}

# Themes: base16 + classics.
$b = $manifest.base16ThemeSource
$themeCatalog = @()
foreach ($name in $manifest.themes.base16) {
    $file = "$name.tmTheme"
    $url = $b.rawBase.Replace("{sha}", $b.sha).Replace("{file}", $file)
    $dest = Join-Path $tDir $file
    try {
        Invoke-WebRequest -Uri $url -OutFile $dest -TimeoutSec 60
        $display = (Get-Culture).TextInfo.ToTitleCase($name.Replace("base16-", "").Replace("-", " "))
        $themeCatalog += [ordered]@{ id = $name; display = $display; themeAsset = "themes/$file"; dark = ($name -notmatch 'light') }
    } catch { Write-Warning "MISSING theme: $name" }
}
foreach ($c in $manifest.themes.classics) {
    $file = ($c.display -replace '\s', '') + ".tmTheme"
    $dest = Join-Path $tDir $file
    try {
        Invoke-WebRequest -Uri $c.url -OutFile $dest -TimeoutSec 60
        $themeCatalog += [ordered]@{ id = ($c.display -replace '\s', '').ToLower(); display = $c.display; themeAsset = "themes/$file"; dark = $true }
    } catch { Write-Warning "MISSING classic theme: $($c.display)" }
}

# Emit catalogs (sorted by display).
# Use [System.IO.File]::WriteAllText with BOM-free UTF-8 so org.json can parse without failure.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$languagesOut = [ordered]@{ languages = @($catalog | Sort-Object { $_.display }) }
$langJson = $languagesOut | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText((Join-Path $assets "languages.json"), $langJson, $utf8NoBom)

$themesOut = [ordered]@{ default = $manifest.themes.default; themes = @($themeCatalog | Sort-Object { $_.display }) }
$themesJson = $themesOut | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText((Join-Path $assets "themes.json"), $themesJson, $utf8NoBom)

# Notices (BOM on this file is fine; it is not parsed at runtime).
$notice = @"
# Third-party assets

## TextMate grammars
Source: https://github.com/$($gSrc.repo) @ $($gSrc.sha)
License: $($gSrc.license)

## base16 themes
Source: https://github.com/$($b.repo) @ $($b.sha)
License: $($b.license)

## Classic themes
$("" + ($manifest.themes.classics | ForEach-Object { "- $($_.display): $($_.url)" } | Out-String))

Generated by tools/textmate/fetch-textmate-assets.ps1. Do not edit by hand.
"@
$notice | Set-Content -Path (Join-Path $assets "THIRD_PARTY_NOTICES.md") -Encoding utf8

Write-Host "Grammars: $((Get-ChildItem $gDir -Filter *.json).Count)  Themes: $((Get-ChildItem $tDir).Count)"
if ($missing.Count -gt 0) { Write-Warning "Missing language grammars: $($missing -join ', ')" }
