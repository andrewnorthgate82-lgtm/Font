@echo off
chcp 65001 >nul
cd /d "%~dp0"
where python >nul 2>nul
if %errorlevel% equ 0 (
    python run_aggregation.py
) else (
    where py >nul 2>nul
    if %errorlevel% equ 0 (
        py run_aggregation.py
    ) else (
        echo [ERROR] Python is not installed or not added to PATH.
        echo Please install Python 3 and check "Add Python to PATH".
    )
)
pause
