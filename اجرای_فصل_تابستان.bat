@echo off
chcp 65001 >nul
cd /d "%~dp0"
where python >nul 2>nul
if %errorlevel% equ 0 (
    python quarterly_aggregation.py 2 1405
) else (
    where py >nul 2>nul
    if %errorlevel% equ 0 (
        py quarterly_aggregation.py 2 1405
    ) else (
        echo [ERROR] Python is not installed or not added to PATH.
        echo Please install Python 3 and check "Add Python to PATH".
    )
)
pause
