@echo off
chcp 65001 >nul
cd /d "%~dp0"
title SEND - Rubika Report Card Sender
echo ==========================================================
echo   SENDING report cards to managers.
echo   Before starting, check:
echo    1- phone numbers are filled in the Excel file
echo    2- the Excel file is CLOSED (not open in Excel)
echo    3- images are inside the karnameh folder
echo    4- month is correct in config.json
echo   Tip: run the List file first to preview everything.
echo   Time: about 25 to 40 minutes - do not close the browser!
echo ==========================================================
echo.
pause
python send_web.py
echo.
pause
