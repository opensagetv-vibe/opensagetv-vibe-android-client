[CmdletBinding()]
param(
    [string]$OutputPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'artifacts/firetv/dvd-hdmi-validation.mp4'),
    [int]$DurationSeconds = 18,
    [string]$VideoDevice = 'USB Video',
    [string]$AudioDevice = 'Digital Audio Interface (USB Digital Audio)'
)

$ErrorActionPreference = 'Stop'
$vlc = 'C:\Program Files\VideoLAN\VLC\vlc.exe'
if (-not (Test-Path -LiteralPath $vlc)) {
    throw "VLC was not found at $vlc"
}

$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}
$logPath = [IO.Path]::ChangeExtension($resolvedOutput, '.vlc.log')
Remove-Item -LiteralPath $resolvedOutput -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $logPath -Force -ErrorAction SilentlyContinue

$arguments = @(
    '--intf=dummy'
    '--file-logging'
    "--logfile=$logPath"
    '--verbose=2'
    "--run-time=$DurationSeconds"
    '--play-and-exit'
    '--no-repeat'
    '--no-loop'
    '--no-random'
    'dshow://'
    ":dshow-vdev=`"$VideoDevice`""
    ":dshow-adev=`"$AudioDevice`""
    ':dshow-size=1920x1080'
    # This inexpensive UVC bridge advertises 60 fps but alternates a valid
    # JPEG with a non-image transport packet at that mode. Its stable decoded
    # cadence is 30 fps, which is sufficient for detecting duplicate/frozen
    # Fire TV output without generating corrupt evidence.
    ':dshow-fps=30'
    # Decode the UVC MJPEG packets before writing them. Raw passthrough from
    # this bridge produces invalid JPEG packets. H.264/AAC in MP4 gives
    # ffprobe/FFmpeg a complete, timestamped A/V sequence for cadence work.
    "--sout=#transcode{vcodec=h264,vb=16000,fps=30,acodec=mp4a,ab=192,channels=2,samplerate=48000}:standard{access=file,mux=mp4,dst=`"$resolvedOutput`"}"
)

$process = Start-Process -FilePath $vlc -ArgumentList $arguments -PassThru -WindowStyle Hidden
$timeoutMs = ($DurationSeconds + 15) * 1000
if (-not $process.WaitForExit($timeoutMs)) {
    Stop-Process -Id $process.Id -Force
    $process.WaitForExit()
}

if (-not (Test-Path -LiteralPath $resolvedOutput)) {
    throw "HDMI capture did not create $resolvedOutput. See $logPath"
}
$file = Get-Item -LiteralPath $resolvedOutput
if ($file.Length -le 0) {
    throw "HDMI capture is empty: $resolvedOutput"
}

[pscustomobject]@{
    Output = $file.FullName
    Bytes = $file.Length
    DurationSeconds = $DurationSeconds
    VideoDevice = $VideoDevice
    AudioDevice = $AudioDevice
    Log = $logPath
}
