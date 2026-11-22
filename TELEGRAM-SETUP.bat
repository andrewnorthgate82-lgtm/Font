@echo off
REM ===========================================================================
REM  نسرا اصفهان - وصل کردن ربات تلگرام
REM
REM  روش استفاده: روی همین فایل دوبار کلیک کنید.
REM  اگر ویندوز هشدار آبی داد:  More info  ->  Run anyway
REM
REM  چه کاری می‌کند:
REM   1) سه سؤال می‌پرسد: توکن ربات، شناسه چت شما، و کلید هوش مصنوعی (اختیاری)
REM   2) تنظیمات را در فایل .env ذخیره می‌کند
REM   3) سرور را اجرا می‌کند و مرورگر را باز می‌کند
REM
REM  توکن و شناسه فقط روی همین لپ‌تاپ و فقط در فایل .env ذخیره می‌شوند.
REM  هیچ‌چیز به جای دیگری فرستاده نمی‌شود.
REM ===========================================================================
setlocal EnableExtensions
chcp 65001 >nul
title نسرا اصفهان - ربات تلگرام
cd /d "%~dp0"

echo.
echo   ============================================
echo    نسرا اصفهان - وصل کردن ربات تلگرام
echo   ============================================
echo.

if not exist "%~dp0package.json" goto noproject
if not exist "%~dp0.env" goto noinstall
if not exist "%~dp0scripts\setup-wizard.mjs" goto noproject

where node >nul 2>nul
if errorlevel 1 goto nonode

echo   در سه سؤال، توکن ربات و شناسه چت را می‌پرسیم.
echo   اگر جایی نمی‌دانید، فقط Enter بزنید.
echo.

node "%~dp0scripts\setup-wizard.mjs"
if errorlevel 1 goto wizardfailed

echo.
echo   ============================================
echo    حالا ربات را در تلگرام تست کنید
echo   ============================================
echo.
echo   در تلگرام به ربات خودتان این را بفرستید:
echo.
echo        /ping
echo.
echo   این پنجره را نبندید. برای خاموش کردن: Ctrl+C
echo.
pause

call npm start
goto end

:noproject
echo   [!] فایل‌های پروژه در این پوشه نیستند.
echo.
echo   اول روی فایل INSTALL-WINDOWS.bat دوبار کلیک کنید.
echo.
pause
exit /b 1

:noinstall
echo   [!] هنوز نصب انجام نشده است.
echo.
echo   اول روی فایل INSTALL-WINDOWS.bat دوبار کلیک کنید.
echo.
pause
exit /b 1

:nonode
echo   [!] Node.js پیدا نشد.
echo.
echo   لپ‌تاپ را یک بار خاموش و روشن کنید، بعد دوباره امتحان کنید.
echo.
pause
exit /b 1

:wizardfailed
echo.
echo   [!] تنظیمات ذخیره نشد.
echo.
echo   متن خطای بالا را برای پشتیبانی بفرستید.
echo   فایل .env دست‌نخورده باقی ماند.
echo.
pause
exit /b 1

:end
echo.
echo   سرور خاموش شد.
pause
