@echo off
chcp 65001 >nul
cd /d "%~dp0"
title لیست کارنامه ها و گیرنده ها - بدون ارسال
python send_web.py --dry-run
echo.
echo برای بستن این پنجره هر کلیدی را بزنید...
pause >nul
