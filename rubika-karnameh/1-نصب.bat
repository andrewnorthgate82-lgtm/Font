@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Install - Rubika Report Card Sender
echo ==========================================================
echo   INSTALLER - needed only ONCE - please wait
echo   DO NOT close this window until it finishes!
echo ==========================================================
echo.
echo Checking Python...
python --version
if errorlevel 1 (
    echo.
    echo [ERROR] Python was NOT found on this computer.
    echo         Install Python from python.org and be sure to tick
    echo         the box: Add python.exe to PATH  during install.
    echo         Then run this file again.
    echo.
    pause
    exit /b 1
)
echo [OK] Python is ready.
echo.
echo [1/3] Installing libraries - takes a few minutes, please wait...
echo ----------------------------------------------------------
python -m pip install -U playwright
if errorlevel 1 (
    echo.
    echo [ERROR] Installing libraries failed.
    echo         Check your internet, then run this file again.
    echo         If it fails again, type the last lines of this window
    echo         and send them to your helper.
    echo.
    pause
    exit /b 1
)
echo.
echo [OK] Libraries installed.
echo.
echo [2/3] Downloading browser - about 150 MB - one time only...
echo ----------------------------------------------------------
python -m playwright install chromium
if errorlevel 1 (
    echo.
    echo [NOTICE] Browser download failed - but do not worry!
    echo          The script will use Google Chrome or Microsoft Edge
    echo          AUTOMATICALLY. No action needed.
    echo.
)
echo.
echo [3/3] Running the self test...
echo ----------------------------------------------------------
python test_flow_mock.py
echo.
echo ==========================================================
echo   If you see  ALL TESTS PASSED  above, install is DONE!
echo   Next step: double-click file number 2 - test to yourself
echo ==========================================================
echo.
pause
