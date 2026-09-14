# Builds the single universal jar into dist/ (works on all supported MC versions: 26.1–26.2).
# Usage: ./build-all.ps1  (requires Java 25)
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root

$propsFile = Join-Path $root "gradle.properties"
$modver = ((Get-Content -LiteralPath $propsFile) | Select-String '^mod_version=(.+)$').Matches[0].Groups[1].Value.Trim()
New-Item -ItemType Directory -Path (Join-Path $root "dist") -Force | Out-Null

Write-Host "=== Building universal jar (compiled against 26.1 baseline, runs on 26.1-26.2) ==="
& ./gradlew.bat build --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$jar = Get-ChildItem -LiteralPath (Join-Path $root "build/libs") -Filter "Moid-Client-*.jar" |
  Where-Object { $_.Name -notmatch "sources" } | Select-Object -First 1
if (-not $jar) { throw "No jar found after building" }
$dest = Join-Path $root ("dist/Moid-Client-v" + $modver + ".jar")
Copy-Item -LiteralPath $jar.FullName -Destination $dest -Force
Write-Host "Wrote $dest"
Write-Host "Build OK."
