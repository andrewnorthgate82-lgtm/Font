@echo off
chcp 65001 >nul
cd /d "%~dp0"
where python >nul 2>nul
if %errorlevel% equ 0 (
    python calculate_period_scores.py 6 70-100
) else (
    where py >nul 2>nul
    if %errorlevel% equ 0 (
        py calculate_period_scores.py 6 70-100
    ) else (
        echo [ERROR] Python is not installed or not added to PATH.
        echo Please install Python 3 and check "Add Python to PATH".
    )
)
pause
