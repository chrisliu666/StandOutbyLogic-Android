@echo off
cd /d "%~dp0"
set PORT=5188
if not "%~1"=="" set PORT=%~1
echo Starting Stand Out by Logic at http://127.0.0.1:%PORT%/
node dev-server.js %PORT%
