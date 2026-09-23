@echo off
chcp 65001 >nul
cd /d "%~dp0"
title List - preview WITHOUT sending
python send_web.py --dry-run
echo.
pause
