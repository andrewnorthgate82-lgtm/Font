@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Test - send ONE test message to yourself
echo ==========================================================
echo   TEST - sends ONE test message to YOURSELF. Very safe.
echo.
echo   BEFORE you continue:
echo   1. Open the Excel file in this folder
echo      (the only .xlsx file - name means Contacts).
echo   2. In the row of the TEST district, write YOUR OWN
echo      mobile number in the FIRST phone-number column.
echo   3. Save and close Excel.
echo ==========================================================
echo.
echo   WHAT HAPPENS NEXT (after you press a key):
echo   1. A Google Chrome window opens with Rubika.
echo   2. FIRST TIME ONLY: you log in yourself -
echo      Iran + your mobile number + the SMS code.
echo   3. Do NOT close the Chrome window.
echo   4. Do NOT close this black window.
echo   5. After login, sending starts automatically.
echo      Wait - it takes a minute.
echo ==========================================================
echo.
pause
python send_web.py --self-test
echo.
echo ==========================================================
echo   FINISHED. Now check Rubika ON YOUR PHONE:
echo   the test message must arrive.
echo   If it did NOT arrive: please TYPE into the chat
echo   the Persian lines you see above in this window.
echo ==========================================================
echo.
pause
