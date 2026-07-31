@echo off
setlocal

REM ============================================================
REM  One-click: build openDebug APK, copy it to /dist, then install
REM  to device(s) via adb. Merges build_debug.bat + install_debug.bat
REM  so you don't have to run them separately.
REM
REM  Usage:
REM    build_install_debug.bat            (build + install to ALL devices)
REM    build_install_debug.bat <serial>   (build + install only to given device)
REM ============================================================

set "ROOT=%~dp0"
set "APK_DIR=%ROOT%app\build\outputs\apk\open\debug"
set "DIST_DIR=%ROOT%dist"

echo [1/4] Building openDebug APK ...
call "%ROOT%gradlew.bat" assembleOpenDebug
if errorlevel 1 (
    echo [ERROR] Build failed. See log above.
    pause
    exit /b 1
)

echo [2/4] Preparing dist dir and copying APK ...

REM dist may exist as a file by mistake; remove it so we can make a dir
if exist "%DIST_DIR%" (
    dir /ad "%DIST_DIR%" >nul 2>nul || del /f /q "%DIST_DIR%"
)
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
if not exist "%DIST_DIR%" (
    echo [ERROR] Cannot create dist dir: %DIST_DIR%
    pause
    exit /b 1
)

set "SRC_APK="
for /f "delims=" %%F in ('dir /b /o:-d /a:-d "%APK_DIR%\*.apk" 2^>nul') do (
    set "SRC_APK=%%F"
    goto :found
)
:found
if "%SRC_APK%"=="" (
    echo [ERROR] No APK found in %APK_DIR%.
    pause
    exit /b 1
)

copy /Y "%APK_DIR%\%SRC_APK%" "%DIST_DIR%\%SRC_APK%" >nul
if errorlevel 1 (
    echo [ERROR] Failed to copy APK to %DIST_DIR%.
    pause
    exit /b 1
)
echo       Copied: %SRC_APK% -^> dist\%SRC_APK%

echo [3/4] Installing APK to device(s) ...
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

echo [4/4] Done. Built and installed: %SRC_APK%
endlocal
pause
