$ErrorActionPreference = 'Stop'
$CommandArguments = @($args)
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = Split-Path -Parent $projectRoot
$buildEnvRoot = if ($env:OPENSAGETV_VIBE_BUILD_ENV_ROOT) {
    $env:OPENSAGETV_VIBE_BUILD_ENV_ROOT
} else {
    Join-Path $workspaceRoot 'opensagetv-vibe-build-env'
}

function Convert-ToWslPath {
    param([Parameter(Mandatory = $true)][string] $Path)

    if ($Path.StartsWith('/')) {
        return $Path
    }

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if ($fullPath -notmatch '^([A-Za-z]):\\(.*)$') {
        throw "Cannot convert path to WSL form: $Path"
    }

    $drive = $Matches[1].ToLowerInvariant()
    $remainder = $Matches[2].Replace('\', '/')
    return "/mnt/$drive/$remainder"
}

if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
    throw 'WSL is required by the Windows wrapper. Install/enable WSL, then retry.'
}

if (-not $env:OPENSAGETV_VIBE_BUILD_ENV_ROOT) {
    $buildScript = Join-Path $buildEnvRoot 'opensagetv-vibe-dev.sh'
    if (-not (Test-Path -LiteralPath $buildScript -PathType Leaf)) {
        throw "Unified build environment not found: $buildScript"
    }
}

$linuxBuildEnvRoot = Convert-ToWslPath $buildEnvRoot
$linuxProjectRoot = Convert-ToWslPath $projectRoot
$forwardedEnvironment = @("OPENSAGETV_VIBE_BUILD_ENV_ROOT=$linuxBuildEnvRoot")
foreach ($name in @(
    'SAGETV_SAGEX_BASE',
    'SAGETV_WEB_BASE',
    'SAGETV_SAGEX_PORTS',
    'SAGETV_SAGEX_USER',
    'SAGETV_SAGEX_PASSWORD',
    'SAGETV_TEST_DEVICE_ALIAS',
    'SAGETV_TEST_SERVER_ALIAS',
    'SAGETV_TEST_SERVER_ADDRESS'
)) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if (-not [String]::IsNullOrEmpty($value)) {
        $forwardedEnvironment += "${name}=$value"
    }
}

$arguments = @('--cd', $linuxProjectRoot, 'env') + $forwardedEnvironment + @(
    'bash', './dev.sh'
) + $CommandArguments

& wsl.exe @arguments
exit $LASTEXITCODE
