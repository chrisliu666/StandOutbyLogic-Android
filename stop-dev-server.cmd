@echo off
setlocal
set PORT=5188
if not "%~1"=="" set PORT=%~1

echo Stopping service on port %PORT%...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT% " ^| findstr "LISTENING"') do (
  echo Killing process %%p
  taskkill /PID %%p /F
)
echo Done.
endlocal
