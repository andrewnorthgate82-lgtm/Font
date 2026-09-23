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
pause
python send_web.py --self-test
echo.
echo ==========================================================
echo   A browser window opens. The FIRST time you must log in
echo   to Rubika yourself - phone number + SMS code.
echo   After login, the script sends the message automatically.
echo   Then check Rubika on your phone: the message must arrive.
echo ==========================================================
echo.
pause
