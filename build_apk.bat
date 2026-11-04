@echo off
setlocal
cd /d "%~dp0"

echo [1/3] Locating Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java (JDK 17) not found. Install Android Studio, or a standalone JDK 17.
    pause
    exit /b 1
)

echo [2/3] Generating Gradle wrapper if missing...
if not exist gradle\wrapper\gradle-wrapper.jar (
    gradle wrapper --gradle-version 8.7
    if errorlevel 1 (
        echo ERROR: Gradle not available. Please build via Android Studio instead:
        echo   Build ^> Build Bundle(s)/APK(s) ^> Build APK(s)
        pause
        exit /b 1
    )
)

echo [3/3] Building APK (assembleDebug)...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo ERROR: Build failed. Check the logs above.
    pause
    exit /b 1
)

if exist app\build\outputs\apk\debug\app-debug.apk (
    echo.
    echo SUCCESS! APK is here:
    echo   app\build\outputs\apk\debug\app-debug.apk
    echo Copy this file to your phone and tap it to install.
) else (
    echo ERROR: APK not found.
)
pause
