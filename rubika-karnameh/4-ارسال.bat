@echo off
chcp 65001 >nul
cd /d "%~dp0"
title SEND - Rubika Report Card Sender
echo ==========================================================
echo   SENDING report cards to managers.
echo   Before starting, check:
echo    1- images are inside the karnameh folder
echo    2- month is correct in config.json
echo   Time: about 25 to 40 minutes - do not close the browser!
echo ==========================================================
echo.
pause
python send_web.py
echo.
pause
