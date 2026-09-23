@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Test - send ONE test message to yourself
echo ==========================================================
echo   TEST - sends ONE test message to YOURSELF. Very safe.
echo.
echo   BEFORE you continue:
echo   Save your OWN mobile number in your phone contacts
echo   with the special TEST name - the exact Persian name
echo   is written in the chat guide and in SETUP.md
echo   You can delete this contact after testing.
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
