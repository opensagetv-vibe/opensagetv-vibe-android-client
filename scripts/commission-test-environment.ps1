[CmdletBinding()]
param(
    [switch]$Regenerate,
    [switch]$SkipFixtures,
    [switch]$SkipBuild,
    [switch]$Install
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$configPath = Join-Path $projectRoot 'config\firetv.toml'
$examplePath = Join-Path $projectRoot 'config\firetv.example.toml'
$devCommand = Join-Path $projectRoot 'dev.cmd'

function Invoke-Dev {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & $devCommand @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "dev.cmd $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    Copy-Item -LiteralPath $examplePath -Destination $configPath
    Write-Host "Created ignored local configuration: $configPath"
    Write-Host 'Edit its documentation addresses, aliases, paths, and blank credentials, then run this command again.'
    exit 2
}

Push-Location $projectRoot
try {
    Invoke-Dev config-check
    Invoke-Dev preflight

    if (-not $SkipFixtures) {
        $seek = Join-Path $projectRoot 'artifacts\test-media\VibeSeekTest-1080i-MPEG2-AC3-CC.ts'
        $codec = Join-Path $projectRoot 'artifacts\test-media\kodi-codec\fixture-manifest.json'
        $dvd = Join-Path $projectRoot 'artifacts\test-media\VIBE_AUTHORED_DVD\VIBE_DVD_TEST_MANIFEST.json'

        if ($Regenerate -or -not (Test-Path -LiteralPath $seek -PathType Leaf)) {
            Invoke-Dev seek-fixture artifacts/test-media/VibeSeekTest-1080i-MPEG2-AC3-CC.ts 900
        } else {
            Write-Host "REUSE: $seek"
        }
        if ($Regenerate -or -not (Test-Path -LiteralPath $codec -PathType Leaf)) {
            Invoke-Dev codec-fixtures --duration 6 --output-dir artifacts/test-media/kodi-codec
        } else {
            Write-Host "REUSE: $codec"
        }
        if ($Regenerate -or -not (Test-Path -LiteralPath $dvd -PathType Leaf)) {
            $dvdArgs = @('dvd-fixture')
            if ($Regenerate) { $dvdArgs += '--overwrite' }
            Invoke-Dev @dvdArgs
        } else {
            Write-Host "REUSE: $dvd"
        }
    }

    if (-not $SkipBuild) {
        Invoke-Dev test
        Invoke-Dev validate
        Invoke-Dev build
    }
    if ($Install) {
        Invoke-Dev install
        Invoke-Dev launch
    }

    Write-Host 'PASS: OpenSageTV Vibe Android test environment commissioned.'
    if (-not $Install) {
        Write-Host 'APK installation was not requested. Re-run with -Install after first-time device setup is understood.'
    }
} finally {
    Pop-Location
}
