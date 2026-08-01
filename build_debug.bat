@echo off
setlocal

REM ============================================================
REM  Build openDebug APK.
REM ============================================================

set "ROOT=%~dp0"

echo Building openDebug APK ...
call "%ROOT%gradlew.bat" assembleOpenDebug
if errorlevel 1 (
    echo [ERROR] Build failed. See log above.
    pause
    exit /b 1
)

echo Done. APK at: app\build\outputs\apk\open\debug\
endlocal
pause
