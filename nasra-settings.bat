@echo off
REM ===========================================================================
REM  نسرا اصفهان - عوض کردن تنظیمات
REM
REM  فقط دوبار کلیک. برای وقتی که می‌خواهید توکن ربات، شناسه چت، کلید
REM  هوش مصنوعی، یا حالت انتشار را عوض کنید.
REM
REM  نکته مهم: مقدارهایی که قبلاً داده‌اید نشان داده می‌شوند و با Enter
REM  دست‌نخورده می‌مانند. رمز پنل هم عوض نمی‌شود.
REM  این فایل هیچ چیزی را تا آخر پاک نمی‌کند؛ از فایل قبلی یک نسخهٔ پشتیبان
REM  با پسوند .bak-... کنارش می‌سازد.
REM ===========================================================================
setlocal EnableExtensions
chcp 65001 >nul
title نسرا اصفهان - تنظیمات
cd /d "%~dp0"

cls
echo.
where node >nul 2>nul
if errorlevel 1 goto nonode
if not exist "%~dp0package.json" goto noproject
if not exist "%~dp0.env" goto nosetup

echo   تنظیمات فعلی نشان داده می‌شوند. برای نگه‌داشتن هر مقدار، فقط Enter بزنید.
echo.
node "%~dp0scripts\setup.mjs"
if errorlevel 1 goto failed

echo.
echo   ==============================================
echo    تنظیمات ذخیره شد.
echo   ==============================================
echo.
echo   برای اعمال شدن: اگر سرور باز است، پنجره‌اش را با Ctrl+C
echo   ببندید و دوبار کلیک روی nasra-start.bat.
echo.
pause
exit /b 0

:nonode
echo   [!] Node.js پیدا نشد.
echo.
echo   یک بار نصبش کنید:  https://nodejs.org  (دکمه سبز LTS)
echo.
pause
exit /b 1

:noproject
echo   [!] این فایل باید کنار فایل‌های پروژه باشد.
echo.
echo   روی nasra-setup.bat دوبار کلیک کنید؛ آن فایل پروژه را می‌آورد.
echo.
pause
exit /b 1

:nosetup
echo   [!] هنوز راه‌اندازی اولیه انجام نشده است.
echo.
echo   یک بار روی nasra-setup.bat دوبار کلیک کنید.
echo.
pause
exit /b 1

:failed
echo.
echo   [!] تنظیمات ذخیره نشد.
echo.
echo   این فایل را برای پشتیبانی بفرستید:
echo        %~dp0nasra-setup-log.txt
echo.
pause
exit /b 1
