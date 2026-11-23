@echo off
REM ===========================================================================
REM  نسرا اصفهان - اجرای روزمره
REM
REM  فقط دوبار کلیک. هیچ چیزی تایپ نمی‌شود.
REM  این فایل همان کاری را می‌کند که nasra-setup.bat در پایان انجام می‌دهد،
REM  ولی هیچ سؤالی نمی‌پرسد و تنظیمات را دست نمی‌زند.
REM
REM  اگر این فایل را از داخل پوشه پروژه باز کرده‌اید، همین‌جا کار می‌کند.
REM  پنجره باز بماند تا سرور و زمان‌بند کار کنند. توقف: Ctrl+C و بعد Y.
REM ===========================================================================
setlocal EnableExtensions
chcp 65001 >nul
title نسرا اصفهان - در حال اجرا
cd /d "%~dp0"

cls
echo.
where node >nul 2>nul
if errorlevel 1 goto nonode
if not exist "%~dp0package.json" goto noproject
if not exist "%~dp0.env" goto nosetup

node "%~dp0scripts\start-nasra.mjs"
echo.
echo   سرور متوقف شد.
echo   اجرای دوباره: دوبار کلیک روی همین فایل.
echo.
pause
exit /b 0

:nonode
echo   [!] Node.js پیدا نشد.
echo.
echo   یک بار نصبش کنید:  https://nodejs.org  (دکمه سبز LTS)
echo   بعد لپ‌تاپ را خاموش و روشن کنید و دوباره همین فایل را باز کنید.
echo.
pause
exit /b 1

:noproject
echo   [!] این فایل باید کنار فایل‌های پروژه باشد.
echo.
echo   فایل nasra-setup.bat را پیدا کنید و روی همان دوبار کلیک کنید؛
echo   آن فایل پروژه را خودش می‌آورد و بعد از نصب، همین فایل را هم می‌سازد.
echo.
pause
exit /b 1

:nosetup
echo   [!] هنوز راه‌اندازی اولیه انجام نشده است.
echo.
echo   یک بار روی فایل nasra-setup.bat دوبار کلیک کنید.
echo   بعد از آن، از این به بعد همیشه همین فایل کافی است.
echo.
pause
exit /b 1
