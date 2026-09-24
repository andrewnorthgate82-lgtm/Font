@echo off
chcp 65001 >nul
cd /d "%~dp0"
title List - preview WITHOUT sending
echo ==========================================================
echo   PREVIEW - shows what WOULD be sent. Nothing is sent.
echo   Reads images + the Excel file of phone numbers.
echo   First choose the category (numbers or 0 for all), then check the list.
echo ==========================================================
echo.
python send_web.py --dry-run --pick-roles
echo.
pause
