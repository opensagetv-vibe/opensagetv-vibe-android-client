@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem -----------------------------------------------------------------------------
rem SageTV MiniClient Dev - compact AI/new-chat handoff ZIP
rem
rem Run this file from anywhere.  It always uses the folder containing this CMD
rem as the project root.
rem
rem The handoff ZIP keeps source code, docs, scripts, tests, MCP tooling, Gradle
rem files and required local Android libraries.  It intentionally excludes build
rem outputs, caches, the duplicate source\existing tree, APKs, ADB downloads,
rem logs/device captures, secrets and other machine-local/generated files.
rem -----------------------------------------------------------------------------

rem Normalize the CMD folder to a full path WITHOUT a trailing backslash.
rem A quoted path ending in \ can be misparsed by robocopy as an escaped quote.
for %%I in ("%~dp0.") do set "ROOT=%%~fI"
pushd "%ROOT%" >nul || (
    echo ERROR: Unable to open project root: "%ROOT%"
    exit /b 1
)

set "VERSION=unknown"
if exist "%ROOT%\VERSION" set /p "VERSION="<"%ROOT%\VERSION"
if not defined VERSION set "VERSION=unknown"

set "ZIP_NAME=opensagetv-vibe-android-client-AI-Handoff-v%VERSION%.zip"
set "ZIP_PATH=%ROOT%\%ZIP_NAME%"
set "STAGE=%TEMP%\SageTV-MiniClient-AI-Handoff-%RANDOM%-%RANDOM%"
set "STAGE_ROOT=%STAGE%\opensagetv-vibe-android-client"

if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE_ROOT%" >nul 2>&1
if errorlevel 1 goto :fail_stage

if exist "%ZIP_PATH%" del /q "%ZIP_PATH%"

 echo.
 echo Creating compact SageTV MiniClient AI handoff...
 echo Project root : %ROOT%
 echo Output       : %ZIP_PATH%
 echo.
 echo Keeping code/docs/tests/MCP/Gradle/required .aar .jar .so libraries.
 echo Excluding generated builds/caches, source\existing, APKs, ADB, captures,
 echo secrets/machine-local files, temp files, and prior ZIPs.
 echo.

rem Robocopy return codes 0-7 are success/non-fatal.  8+ is a real failure.
robocopy "%ROOT%" "%STAGE_ROOT%" /E /COPY:DAT /DCOPY:DAT /R:1 /W:1 /XJ /NFL /NDL /NJH /NJS /NP ^
    /XD "%ROOT%\source\existing" "%ROOT%\adb" "%ROOT%\artifacts" "%ROOT%\incoming" "%ROOT%\logs" "%ROOT%\recordings" "%ROOT%\screenshots" ^
        "%ROOT%\source\dev\playstore" build .gradle .idea .git __pycache__ .pytest_cache .mypy_cache .ruff_cache ^
    /XF "*.zip" "*.apk" "*.aab" "*.apks" "*.class" "*.dex" "*.log" "*.tmp" "*.temp" "*.lock" "*.hprof" ^
        ".env" "firetv.toml" "local.properties" "keystore.properties" "*.jks" "*.keystore" "PROJECT_MANIFEST.sha256"
set "RC=%ERRORLEVEL%"
if %RC% GEQ 8 goto :fail_copy

rem Generate a fresh manifest for exactly what is in this compact package.
set "AI_HANDOFF_STAGE_ROOT=%STAGE_ROOT%"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
  "$root=[IO.Path]::GetFullPath($env:AI_HANDOFF_STAGE_ROOT);" ^
  "$manifest=Join-Path $root 'PROJECT_MANIFEST.sha256';" ^
  "$lines=Get-ChildItem -LiteralPath $root -Recurse -File -Force | Where-Object { $_.FullName -ne $manifest } | Sort-Object FullName | ForEach-Object { $rel=$_.FullName.Substring($root.Length).TrimStart([char]92).Replace([char]92,[char]47); $hash=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(); $hash + '  ' + $rel };" ^
  "[IO.File]::WriteAllLines($manifest,$lines,[Text.Encoding]::ASCII)"
if errorlevel 1 goto :fail_manifest

rem Create a portable ZIP with forward-slash entry names.  Windows PowerShell's
rem ZipFile.CreateFromDirectory can emit backslash entry names, which Linux/AI
rem environments may not extract as a normal directory tree.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\create_portable_zip.ps1" ^
  -SourceDirectory "%STAGE_ROOT%" ^
  -ZipPath "%ZIP_PATH%"
if errorlevel 1 goto :fail_zip

for %%F in ("%ZIP_PATH%") do set "ZIP_BYTES=%%~zF"

rmdir /s /q "%STAGE%"
popd >nul

echo.
echo SUCCESS: Compact AI handoff created.
echo %ZIP_PATH%
echo Size: %ZIP_BYTES% bytes
exit /b 0

:fail_copy
echo.
echo ERROR: Robocopy failed with exit code %RC%.
goto :cleanup_fail

:fail_manifest
echo.
echo ERROR: Could not generate PROJECT_MANIFEST.sha256.
goto :cleanup_fail

:fail_zip
echo.
echo ERROR: Could not create the handoff ZIP.
goto :cleanup_fail

:fail_stage
echo.
echo ERROR: Could not create temporary staging folder:
echo %STAGE%
goto :cleanup_fail

:cleanup_fail
if exist "%STAGE%" rmdir /s /q "%STAGE%"
popd >nul
exit /b 1
