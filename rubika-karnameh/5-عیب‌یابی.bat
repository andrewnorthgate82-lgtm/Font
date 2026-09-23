@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Debug helper
python send_web.py --inspect
echo.
echo Send the files inside the  debug  folder to your helper.
echo.
pause
