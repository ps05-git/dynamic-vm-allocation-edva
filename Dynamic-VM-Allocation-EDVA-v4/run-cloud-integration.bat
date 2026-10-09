@echo off
cd /d %~dp0
if not exist out mkdir out
echo Compiling EDVA project and MiniCloud integration...
javac -d out src\*.java
if errorlevel 1 (
  echo Compilation failed. Fix the errors above before continuing.
  pause
  exit /b 1
)
echo.
echo IMPORTANT: Start mini-cloud first in a separate window:
echo   cd mini-cloud
echo   run-mini-cloud.bat
echo.
echo This run simulates VM scheduling but submits real Windows processes through the local MiniCloud API.
java -cp out CloudIntegrationMain
pause
