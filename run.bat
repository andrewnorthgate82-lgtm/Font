@echo off
chcp 65001 > nul
cd /d "%~dp0"
title Nasra District Performance Aggregator

echo ========================================================
echo   Nasra District Performance Aggregator ^& Image Generator
echo ========================================================
echo.

where python >nul 2>nul
if %errorlevel% equ 0 (
    python run_aggregation.py
    goto finish
)

where py >nul 2>nul
if %errorlevel% equ 0 (
    py run_aggregation.py
    goto finish
)

echo [ERROR] Python was not found on your system!
echo Please install Python 3 from https://www.python.org or Microsoft Store.
echo.

:finish
echo.
pause
