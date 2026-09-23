@echo off
chcp 65001 >nul
cd /d "%~dp0"
title عیب یابی
python send_web.py --inspect
echo.
echo فایل های پوشه debug را برای پشتیبان بفرستید
echo برای بستن این پنجره هر کلیدی را بزنید...
pause >nul
