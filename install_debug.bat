@echo off
setlocal

REM ============================================================
REM  Install the latest debug APK from /dist via adb
REM  Usage:
REM    install_debug.bat            (default connected device)
REM    install_debug.bat <serial>   (adb -s <serial>)
REM ============================================================

set "DIST_DIR=%~dp0dist"
set "SERIAL=%1"

set "APK="
for /f "delims=" %%F in ('dir /b /o:-d /a:-d "%DIST_DIR%\*.apk" 2^>nul') do (
    set "APK=%%F"
    goto :found
)
:found
if "%APK%"=="" (
    echo [ERROR] No APK in dist. Run build_debug.bat first.
    pause
    exit /b 1
)

set "APK_PATH=%DIST_DIR%\%APK%"
echo Installing APK: %APK_PATH%

if "%SERIAL%"=="" (
    echo Installing to default device ...
    adb install -r "%APK_PATH%"
) else (
    echo Installing to device %SERIAL% ...
    adb -s %SERIAL% install -r "%APK_PATH%"
)

if errorlevel 1 (
    echo [ERROR] Install failed. Make sure a device is connected via adb.
    pause
    exit /b 1
)

echo Install done.
endlocal
pause
