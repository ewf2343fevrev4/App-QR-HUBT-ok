@echo off
setlocal
echo Smart Attendance - Environment Check
echo.
where java >nul 2>&1
if errorlevel 1 (echo [MISSING] Java. Install JDK 17 and set JAVA_HOME.) else (java -version)
if defined JAVA_HOME (echo JAVA_HOME=%JAVA_HOME%) else (echo [MISSING] JAVA_HOME. Set it to your JDK 17 directory.)
set "ATTENDANCE_SDK=%ANDROID_HOME%"
if not defined ATTENDANCE_SDK set "ATTENDANCE_SDK=%ANDROID_SDK_ROOT%"
if not defined ATTENDANCE_SDK set "ATTENDANCE_SDK=%LOCALAPPDATA%\Android\Sdk"
echo Android SDK candidate: %ATTENDANCE_SDK%
if exist "%ATTENDANCE_SDK%\platforms\android-35\android.jar" (echo [OK] Android SDK Platform 35) else (echo [MISSING] Install Android SDK Platform 35 in Android Studio SDK Manager.)
if exist "%ATTENDANCE_SDK%\build-tools\35.0.0\aapt2.exe" (echo [OK] Build Tools 35.0.0) else (echo [MISSING] Install Android Build Tools 35.0.0.)
if exist "%ATTENDANCE_SDK%\platform-tools\adb.exe" (echo [OK] adb) else (echo [MISSING] Install Android Platform Tools.)
if exist "gradle\wrapper\gradle-wrapper.jar" (echo [OK] Gradle Wrapper JAR) else (echo [MISSING] gradle\wrapper\gradle-wrapper.jar)
if exist "gradlew.bat" (echo [OK] Windows wrapper) else (echo [MISSING] gradlew.bat)
if exist "local.properties" (echo [OK] local.properties) else (echo [INFO] Open the project in Android Studio or set sdk.dir in local.properties.)
echo.
echo Build: gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
echo Device tests: gradlew.bat :app:connectedDebugAndroidTest
echo APK: app\build\outputs\apk\debug\app-debug.apk
echo No Python installation is required.
endlocal
