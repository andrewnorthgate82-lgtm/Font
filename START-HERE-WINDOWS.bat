@echo off
chcp 65001 >nul
title Karkard Generator - Windows
cd /d "%~dp0"

echo =========================================
echo Karkard Monthly Generator - Windows
echo =========================================
echo.

if not exist "karkard\karkard.py" (
  echo ERROR: The folder "karkard" was not found.
  echo Please extract the ZIP file completely, then run this file again.
  echo Do NOT run this file from inside the ZIP preview window.
  echo.
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
  echo.
  echo Step 1: Install Python from:
  echo https://www.python.org/downloads/
  echo.
  echo Step 2: During install, tick this option:
  echo Add python.exe to PATH
  echo.
  echo Step 3: Close this window and run START-HERE-WINDOWS.bat again.
  echo.
  pause
  exit /b 1
)

cd /d "%~dp0karkard"
if not exist ".venv\Scripts\python.exe" (
  echo Creating local Python environment...
  %PYTHON_CMD% -m venv .venv
  if errorlevel 1 (
    echo Could not create Python virtual environment.
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
echo Now answer the questions to generate one month.
echo.
".venv\Scripts\python.exe" karkard.py

echo.
echo Done.
echo Your Excel file is inside:
echo %~dp0karkard\output
echo.
pause
