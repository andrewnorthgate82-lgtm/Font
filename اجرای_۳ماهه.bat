@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo =======================================================
echo   سامانه ارزیابی عملکرد 3 ماهه نواحی نسرا
echo =======================================================

where python >nul 2>nul
if %errorlevel% equ 0 (
    python calculate_period_scores.py 3
) else (
    where py >nul 2>nul
    if %errorlevel% equ 0 (
        py calculate_period_scores.py 3
    ) else (
        echo [ERROR] Python is not installed or not added to PATH.
        echo Please install Python 3 and check "Add Python to PATH".
    )
)

echo.
echo =======================================================
echo   عملیات پایان یافت. فایل اکسل نمرات آماده و باز شد.
echo =======================================================
pause
