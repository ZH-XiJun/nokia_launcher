@echo off
setlocal

REM ============================================================
REM  One-click: build openDebug APK, then install to device(s)
REM  via adb.  install_debug.py reads APK directly from the
REM  build output directory -- no stale /dist cache involved.
REM
REM  Usage:
REM    build_install_debug.bat            (build + install to ALL devices)
REM    build_install_debug.bat <serial>   (build + install only to given device)
REM ============================================================

set "ROOT=%~dp0"

echo [1/3] Building openDebug APK ...
call "%ROOT%gradlew.bat" assembleOpenDebug
if errorlevel 1 (
    echo [ERROR] Build failed. See log above.
    pause
    exit /b 1
)

echo [2/3] Installing APK to device(s) ...
set "SCRIPT=%ROOT%install_debug.py"
if not exist "%SCRIPT%" (
    echo [ERROR] install_debug.py not found next to this bat.
    pause
    exit /b 1
)

python "%SCRIPT%" %*
set "RC=%errorlevel%"

if not "%RC%"=="0" (
    echo [ERROR] Install failed. Check the per-device results above.
    pause
    exit /b %RC%
)

echo [3/3] Done.
endlocal
pause
