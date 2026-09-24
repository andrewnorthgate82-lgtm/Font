@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"
set "ROOT=%~dp0"
set "APP=%ROOT%karkard"

if not exist "%APP%\karkard.py" (
  echo ERROR: Please Extract All the ZIP first.
  pause
  exit /b 1
)

set "PYTHON_CMD="
where py >nul 2>nul
if %errorlevel%==0 set "PYTHON_CMD=py -3"
if not defined PYTHON_CMD (
  where python >nul 2>nul
  if %errorlevel%==0 set "PYTHON_CMD=python"
)
if not defined PYTHON_CMD (
  echo Python is not installed. Install it from https://www.python.org/downloads/
  echo Tick: Add python.exe to PATH
  pause
  exit /b 1
)

cd /d "%APP%"
if not exist ".venv\Scripts\python.exe" %PYTHON_CMD% -m venv .venv
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 (
  echo Package installation failed.
  pause
  exit /b 1
)
".venv\Scripts\python.exe" karkard.py --all-months --year 1405
if errorlevel 1 (
  echo Generation failed. Please send a screenshot.
  pause
  exit /b 1
)
if not exist "%APP%\output" mkdir "%APP%\output"
explorer "%APP%\output"
echo Done. Output folder: %APP%\output
pause
