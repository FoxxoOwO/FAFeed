@echo off
cd /d D:\repositories\FAFeed
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
call gradlew.bat --no-daemon --console=plain %* > build_last.log 2>&1
echo EXIT=%ERRORLEVEL% >> build_last.log
