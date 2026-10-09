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

# ADB already lives in the reusable development container. Native Windows
# should not require a second platform-tools install or a working WSL launcher
# merely to connect to a commissioned device or run a scoped ADB diagnostic.
# Prefer the running container for those two commands; retain the established
# WSL workflow below for container creation and every other development task.
$commandName = if ($CommandArguments.Count -gt 0) {
    [string] $CommandArguments[0]
} else {
    ''
}
if ($commandName -in @('connect', 'adb')) {
    $dockerCommand = Get-Command docker.exe -ErrorAction SilentlyContinue
    $containerName = if ($env:OPENSAGETV_VIBE_DEV_CONTAINER) {
        $env:OPENSAGETV_VIBE_DEV_CONTAINER
    } else {
        'opensagetv-vibe-dev'
    }
    if ($dockerCommand) {
        $savedErrorActionPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'SilentlyContinue'
            $running = & $dockerCommand.Source ps `
                --filter "name=$containerName" --format '{{.Names}}' 2>$null
            $dockerInspectExitCode = $LASTEXITCODE
        } catch {
            $running = ''
            $dockerInspectExitCode = 1
        } finally {
            $ErrorActionPreference = $savedErrorActionPreference
        }
        if ($dockerInspectExitCode -eq 0 -and @($running) -contains $containerName) {
            $dockerArguments = @(
                'exec', '-i', '-w', '/workspace/android-client',
                '-e', 'ANDROID_USER_HOME=/workspace/android-client/adb',
                '-e', 'ADB_VENDOR_KEYS=/workspace/android-client/adb',
                '-e', 'SAGETV_WORKSPACE=/workspace/android-client',
                '-e', 'SAGETV_MCP_CONFIG=/workspace/android-client/config/firetv.toml',
                '-e', 'PYTHONPATH=/workspace/android-client/mcp/src',
                '-e', 'PYTHONDONTWRITEBYTECODE=1',
                '-e', 'PYTHONUNBUFFERED=1'
            )
            foreach ($name in @(
                'SAGETV_SAGEX_BASE',
                'SAGETV_WEB_BASE',
                'SAGETV_SAGEX_PORTS',
                'SAGETV_SAGEX_USER',
                'SAGETV_SAGEX_PASSWORD',
                'SAGETV_CORE_MCP_BASE',
                'SAGETV_CORE_MCP_TOKEN',
                'SAGETV_TEST_DEVICE_ALIAS',
                'SAGETV_ADB_SERIAL',
                'SAGETV_TEST_SERVER_ALIAS',
                'SAGETV_TEST_SERVER_ADDRESS'
            )) {
                $value = [Environment]::GetEnvironmentVariable($name)
                if (-not [String]::IsNullOrEmpty($value)) {
                    $dockerArguments += @('-e', "${name}=$value")
                }
            }
            $dockerArguments += $containerName
            if ($commandName -eq 'connect') {
                $dockerArguments += @(
                    'bash',
                    '/workspace/android-client/docker/entrypoint.sh',
                    'connect'
                )
                if ($CommandArguments.Count -gt 1) {
                    $dockerArguments += $CommandArguments[1..($CommandArguments.Count - 1)]
                }
            } else {
                $dockerArguments += @(
                    'bash',
                    '/workspace/android-client/scripts/container_scoped_adb.sh'
                )
                if ($CommandArguments.Count -gt 1) {
                    $dockerArguments += $CommandArguments[1..($CommandArguments.Count - 1)]
                }
            }
            & $dockerCommand.Source @dockerArguments
            exit $LASTEXITCODE
        }
    }
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
    'SAGETV_CORE_MCP_BASE',
    'SAGETV_CORE_MCP_TOKEN',
    'SAGETV_TEST_DEVICE_ALIAS',
    'SAGETV_ADB_SERIAL',
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
