@echo off
chcp 65001 >nul
cd /d "%~dp0"
title List - preview WITHOUT sending
echo ==========================================================
echo   PREVIEW - shows what WOULD be sent. Nothing is sent.
echo   Reads images + the Excel file of phone numbers.
echo   Check: every district shows 3 phone numbers.
echo ==========================================================
echo.
python send_web.py --dry-run
echo.
pause
