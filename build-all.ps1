# Builds one jar per supported Minecraft version into dist/.
# Usage: ./build-all.ps1  (requires Java 25)
$ErrorActionPreference = "Stop"

$versions = @(
  @{ mc = "26.1"; loader = "0.19.3"; api = "0.145.1+26.1" },
  @{ mc = "26.2"; loader = "0.19.5"; api = "0.160.0+26.2" }
)

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root

$propsFile = Join-Path $root "gradle.properties"
$backup = Join-Path $root "gradle.properties.buildall-bak"
if (Test-Path -LiteralPath $backup) {
  Write-Warning "Stale backup found (previous run was killed?) - restoring it first."
  Move-Item -LiteralPath $backup -Destination $propsFile -Force
}
Copy-Item -LiteralPath $propsFile -Destination $backup -Force
New-Item -ItemType Directory -Path (Join-Path $root "dist") -Force | Out-Null

try {
  $modver = ((Get-Content -LiteralPath $propsFile) | Select-String '^mod_version=(.+)$').Matches[0].Groups[1].Value.Trim()
  foreach ($v in $versions) {
    Write-Host "=== Building for MC $($v.mc) ==="
    $props = Get-Content -LiteralPath $propsFile
    $props = $props -replace '^minecraft_version=.*', ("minecraft_version=" + $v.mc)
    $props = $props -replace '^loader_version=.*', ("loader_version=" + $v.loader)
    $props = $props -replace '^fabric_api_version=.*', ("fabric_api_version=" + $v.api)
    Set-Content -LiteralPath $propsFile -Value $props

    & ./gradlew.bat build --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "Build failed for MC $($v.mc)" }

    $jar = Get-ChildItem -LiteralPath (Join-Path $root "build/libs") -Filter "Moid-Client-*.jar" |
      Where-Object { $_.Name -notmatch "sources" } | Select-Object -First 1
    if (-not $jar) { throw "No jar found after building for MC $($v.mc)" }
    $dest = Join-Path $root ("dist/Moid-Client-v" + $modver + "+" + $v.mc + ".jar")
    Copy-Item -LiteralPath $jar.FullName -Destination $dest -Force
    Write-Host "Wrote $dest"
  }
  Write-Host "All builds OK."
} finally {
  Move-Item -LiteralPath $backup -Destination $propsFile -Force
  Write-Host "gradle.properties restored."
}
