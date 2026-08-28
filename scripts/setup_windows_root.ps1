param(
    [string]$Root = "C:\SageTV-MiniClient-Dev"
)

$ErrorActionPreference = "Stop"
$folders = @(
    $Root,
    (Join-Path $Root "incoming"),
    (Join-Path $Root "source"),
    (Join-Path $Root "source\dev"),
    (Join-Path $Root "source\existing"),
    (Join-Path $Root "artifacts"),
    (Join-Path $Root "artifacts\firetv"),
    (Join-Path $Root "artifacts\existing"),
    (Join-Path $Root "adb"),
    (Join-Path $Root "logs"),
    (Join-Path $Root "screenshots"),
    (Join-Path $Root "recordings"),
    (Join-Path $Root "config")
)

foreach ($folder in $folders) {
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
}

Write-Host "Windows SageTV development workspace ready: $Root"
Write-Host "Bundled source should already exist in: $(Join-Path $Root 'source')"
Write-Host "Use $(Join-Path $Root 'incoming') only for a future replacement source ZIP."
Write-Host "ADB RSA keys will persist in: $(Join-Path $Root 'adb')"
Write-Host "The Docker entrypoint creates firetv.toml from the project example if it is missing."
Write-Host "The project directory is mounted automatically. SAGETV_WINDOWS_ROOT is only needed as an explicit override."
