param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDirectory,

    [Parameter(Mandatory = $true)]
    [string]$ZipPath
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$sourceRoot = [IO.Path]::GetFullPath($SourceDirectory).TrimEnd([char]92, [char]47)
$rootName = Split-Path -Leaf $sourceRoot

if (Test-Path -LiteralPath $ZipPath) {
    Remove-Item -LiteralPath $ZipPath -Force
}

$zipStream = [IO.File]::Open($ZipPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
try {
    $archive = [IO.Compression.ZipArchive]::new(
        $zipStream,
        [IO.Compression.ZipArchiveMode]::Create,
        $false
    )
    try {
        Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Force |
            Sort-Object FullName |
            ForEach-Object {
                $relative = $_.FullName.Substring($sourceRoot.Length).TrimStart([char]92, [char]47)
                $entryName = ($rootName + "/" + $relative.Replace([char]92, [char]47))
                $entry = $archive.CreateEntry($entryName, [IO.Compression.CompressionLevel]::Optimal)

                $entryStream = $entry.Open()
                try {
                    $inputStream = [IO.File]::OpenRead($_.FullName)
                    try {
                        $inputStream.CopyTo($entryStream)
                    }
                    finally {
                        $inputStream.Dispose()
                    }
                }
                finally {
                    $entryStream.Dispose()
                }
            }
    }
    finally {
        $archive.Dispose()
    }
}
finally {
    $zipStream.Dispose()
}

Write-Host "Portable ZIP created: $ZipPath"
