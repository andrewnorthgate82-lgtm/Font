@echo off
chcp 65001 >nul
title Karkard Generator - All Months 1405
cd /d "%~dp0"

if not exist "karkard\karkard.py" (
  echo Error: karkard\karkard.py was not found.
  echo Please extract the zip file completely, then run this file again.
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
  echo Python is not installed or not added to PATH.
  echo Install Python 3 from https://www.python.org/downloads/ and tick "Add python.exe to PATH".
  pause
  exit /b 1
)

cd /d "%~dp0karkard"
if not exist ".venv\Scripts\python.exe" (
  echo Creating local Python environment...
  %PYTHON_CMD% -m venv .venv
  if errorlevel 1 (
    echo Could not create virtual environment.
    pause
    exit /b 1
  )
)

echo Installing required packages...
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 (
  echo Package installation failed.
  pause
  exit /b 1
)

echo.
echo Generating all 12 months of year 1405...
echo.
".venv\Scripts\python.exe" karkard.py --all-months --year 1405

echo.
echo Done. Output files are inside: %~dp0karkard\output
pause
