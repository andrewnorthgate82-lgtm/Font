@echo off
chcp 65001 >nul
setlocal EnableExtensions
title Karkard Generator - Windows Menu
cd /d "%~dp0"
set "ROOT=%~dp0"
set "APP=%ROOT%karkard"

echo =========================================
echo      Karkard Generator - Windows
echo =========================================
echo.

if not exist "%APP%\karkard.py" (
  echo ERROR: The folder "karkard" was not found.
  echo Please right-click the ZIP file and choose Extract All first.
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
  echo 1. Install Python from: https://www.python.org/downloads/
  echo 2. During install, tick: Add python.exe to PATH
  echo 3. Close this window and run this file again.
  echo.
  pause
  exit /b 1
)

cd /d "%APP%"
if not exist ".venv\Scripts\python.exe" (
  echo Creating local Python environment. Please wait...
  %PYTHON_CMD% -m venv .venv
  if errorlevel 1 goto ENV_ERROR
)

echo Installing/checking required packages. Please wait...
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 goto PIP_ERROR

:MENU
echo.
echo Choose an option:
echo   1 - Generate ONE month
echo   2 - Generate ALL months of a year
echo   3 - Open output folder
echo   4 - Exit
echo.
choice /c 1234 /n /m "Enter 1, 2, 3, or 4: "
if errorlevel 4 goto END
if errorlevel 3 goto OPEN_OUTPUT
if errorlevel 2 goto ALL_MONTHS
if errorlevel 1 goto ONE_MONTH

:ONE_MONTH
echo.
set "MONTH="
set /p "MONTH=Month number (1-12), example 5 for Mordad: "
if "%MONTH%"=="" set "MONTH=5"
set "YEAR="
set /p "YEAR=Year, example 1405: "
if "%YEAR%"=="" set "YEAR=1405"
set "FULLNAME="
set /p "FULLNAME=Full name (press Enter for default): "

echo.
echo Generating month %MONTH% of year %YEAR% ...
if "%FULLNAME%"=="" (
  ".venv\Scripts\python.exe" karkard.py --month %MONTH% --year %YEAR%
) else (
  ".venv\Scripts\python.exe" karkard.py --month %MONTH% --year %YEAR% --name "%FULLNAME%"
)
if errorlevel 1 goto RUN_ERROR
goto SUCCESS

:ALL_MONTHS
echo.
set "YEAR="
set /p "YEAR=Year for all months, example 1405: "
if "%YEAR%"=="" set "YEAR=1405"
set "FULLNAME="
set /p "FULLNAME=Full name (press Enter for default): "

echo.
echo Generating all 12 months of year %YEAR% ...
if "%FULLNAME%"=="" (
  ".venv\Scripts\python.exe" karkard.py --all-months --year %YEAR%
) else (
  ".venv\Scripts\python.exe" karkard.py --all-months --year %YEAR% --name "%FULLNAME%"
)
if errorlevel 1 goto RUN_ERROR
goto SUCCESS

:SUCCESS
echo.
echo =========================================
echo Done. Excel file(s) were created.
echo Output folder:
echo %APP%\output
echo =========================================
if not exist "%APP%\output" mkdir "%APP%\output"
explorer "%APP%\output"
echo.
pause
goto MENU

:OPEN_OUTPUT
if not exist "%APP%\output" mkdir "%APP%\output"
explorer "%APP%\output"
goto MENU

:ENV_ERROR
echo.
echo ERROR: Could not create the local Python environment.
echo Try installing Python again and tick "Add python.exe to PATH".
pause
exit /b 1

:PIP_ERROR
echo.
echo ERROR: Required packages could not be installed.
echo Check your internet connection, then run this file again.
pause
exit /b 1

:RUN_ERROR
echo.
echo ERROR: The Excel file was not generated.
echo Please send a screenshot of this black window.
pause
goto MENU

:END
exit /b 0
