@echo off
REM ---------------------------------------------------------------------------
REM  نسرا اصفهان - نصب و اجرا
REM
REM  روش استفاده: روی همین فایل دوبار کلیک کنید.
REM  اگر ویندوز هشدار داد: More info  ->  Run anyway
REM  (این فایل فقط Node.js را در همین پوشه اجرا می‌کند؛ کار دیگری نمی‌کند.)
REM ---------------------------------------------------------------------------
chcp 65001 >nul
title نسرا اصفهان
cd /d "%~dp0"

echo.
echo   ============================================
echo    نسرا اصفهان - استودیوی رسانه
echo   ============================================
echo.

where node >nul 2>nul
if errorlevel 1 goto nonode

if not exist ".env" goto firstrun
goto run

:nonode
echo   [!] Node.js نصب نیست.
echo.
echo   1) به این آدرس بروید:  https://nodejs.org
echo   2) دکمه سبز Download Node.js (LTS) را بزنید
echo   3) فایل msi را نصب کنید (همه‌جا Next)
echo   4) لپ‌تاپ را یک بار ری‌استارت کنید
echo   5) دوباره روی همین فایل دوبار کلیک کنید
echo.
pause
exit /b 1

:firstrun
echo   بار اول است. در حال آماده‌سازی...
echo.
call node scripts\install.mjs
if errorlevel 1 goto failed
echo.
echo   آماده شد. سرور اجرا می‌شود...
echo.
pause

:run
call npm start
goto end

:failed
echo.
echo   [!] آماده‌سازی کامل نشد.
echo   متن خطای بالا را برای پشتیبانی بفرستید.
echo.
pause
exit /b 1

:end
echo.
echo   سرور خاموش شد.
pause
