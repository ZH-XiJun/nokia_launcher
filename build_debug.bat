@echo off
setlocal

REM ============================================================
REM  Build openDebug APK and copy it to /dist
REM ============================================================

set "ROOT=%~dp0"
set "APK_DIR=%ROOT%app\build\outputs\apk\open\debug"
set "DIST_DIR=%ROOT%dist"

echo [1/3] Building openDebug APK ...
call "%ROOT%gradlew.bat" assembleOpenDebug
if errorlevel 1 (
    echo [ERROR] Build failed. See log above.
    exit /b 1
)

echo [2/3] Preparing dist dir and copying APK ...

REM dist may exist as a file by mistake; remove it so we can make a dir
if exist "%DIST_DIR%" (
    dir /ad "%DIST_DIR%" >nul 2>nul || del /f /q "%DIST_DIR%"
)
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
if not exist "%DIST_DIR%" (
    echo [ERROR] Cannot create dist dir: %DIST_DIR%
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
    exit /b 1
)

copy /Y "%APK_DIR%\%SRC_APK%" "%DIST_DIR%\%SRC_APK%" >nul
if errorlevel 1 (
    echo [ERROR] Failed to copy APK to %DIST_DIR%.
    exit /b 1
)
echo       Copied: %SRC_APK% -^> dist\%SRC_APK%

echo [3/3] Done. APK at: %DIST_DIR%\%SRC_APK%
endlocal
pause
