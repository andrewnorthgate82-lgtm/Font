@echo off
REM ===========================================================================
REM  نسرا اصفهان - نصب و راه‌اندازی، همه در یک دوبار کلیک
REM
REM  روش استفاده: این فایل را در یک پوشه خالی بگذارید و دوبار کلیک کنید.
REM  اگر ویندوز هشدار آبی داد:  More info  ->  Run anyway
REM
REM  این فایل خودش:
REM    1) Node.js را بررسی می‌کند
REM    2) پروژه را از GitHub می‌گیرد و در همین پوشه باز می‌کند
REM    3) رمز ورود و تنظیمات را می‌سازد
REM    4) توکن ربات تلگرام و شناسه چت را می‌پرسد
REM    5) سرور را اجرا می‌کند و مرورگر را باز می‌کند
REM
REM  اگر جایی خطا داد، فایل nasra-setup-log.txt ساخته می‌شود.
REM  همان فایل را بفرستید؛ رمز و توکن داخلش نیست.
REM ===========================================================================
setlocal EnableExtensions
chcp 65001 >nul
title نسرا اصفهان - راه‌اندازی
cd /d "%~dp0"

set "PROJECT_URL=https://codeload.github.com/andrewnorthgate82-lgtm/Font/zip/refs/heads/arena/b48083ae-font"
set "ZIP_NAME=nasra.zip"
set "DIR=%CD%"

cls
echo.
echo   ==============================================
echo    نسرا اصفهان - راه‌اندازی
echo   ==============================================
echo.
echo   پوشه: %DIR%
echo.

REM ---------------------------------------------------------------- Node.js --
where node >nul 2>nul
if errorlevel 1 goto nonode
for /f "tokens=*" %%v in ('node -v 2^>nul') do set "NODEVER=%%v"
echo   [1/5] Node.js: %NODEVER%

REM ---------------------------------------------------------------- project --
if exist "%DIR%\package.json" (
  echo   [2/5] فایل‌های پروژه موجود است
  goto havenv
)

echo   [2/5] گرفتن پروژه از اینترنت...
echo.

if exist "%DIR%\%ZIP_NAME%" (
  echo         فایل %ZIP_NAME% از قبل موجود است؛ از همان استفاده می‌شود.
  goto extract
)

curl.exe -L --fail --silent --show-error -o "%DIR%\%ZIP_NAME%" "%PROJECT_URL%"
if errorlevel 1 goto downloadfail

:extract
echo         در حال باز کردن...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$dest='%DIR%';" ^
  "Expand-Archive -LiteralPath (Join-Path $dest '%ZIP_NAME%') -DestinationPath $dest -Force;" ^
  "$root = Get-ChildItem -LiteralPath $dest -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'package.json') } | Select-Object -First 1;" ^
  "if (-not $root) { Write-Host 'root-not-found'; exit 3 };" ^
  "Get-ChildItem -LiteralPath $root.FullName -Force | ForEach-Object { Move-Item -LiteralPath $_.FullName -Destination $dest -Force };" ^
  "Remove-Item -LiteralPath $root.FullName -Recurse -Force;" ^
  "Remove-Item -LiteralPath (Join-Path $dest '%ZIP_NAME%') -Force -ErrorAction SilentlyContinue;"
if errorlevel 1 goto extractfail
if not exist "%DIR%\package.json" goto extractfail
echo         آماده شد.
echo.

REM ---------------------------------------------------------------- install --
:havenv
echo   [3/5] بررسی پایگاه‌داده داخلی
echo   [4/5] پرسش‌های تنظیمات
echo.
node "%DIR%\scripts\setup.mjs"
if errorlevel 1 goto setupfailed

echo.
echo   ==============================================
echo    راه‌اندازی کامل شد.
echo   ==============================================
echo.
echo   سرور را الان بستید. برای اجرای دوباره (هر بار که لپ‌تاپ را
echo   روشن می‌کنید) یکی از این دو:
echo       - دوبار کلیک روی همین فایل  (رمز و تنظیمات دست‌نخورده می‌مانند)
echo       - یا دوبار کلیک روی فایل:  nasra-start.bat
echo.
echo   مرورگر خودش باز می‌شود روی:  http://localhost:8787
echo.
echo   اگر جایی خطا دیدید، این فایل را برای پشتیبانی بفرستید:
echo        %DIR%\nasra-setup-log.txt
echo   (رمز ورود و توکن داخل آن نیست.)
echo.
pause
exit /b 0

REM ------------------------------------------------------------------ errors --
:nonode
echo   [!] Node.js نصب نیست، یا ویندوز هنوز آن را نمی‌شناسد.
echo.
echo   1) به این آدرس بروید:  https://nodejs.org
echo   2) دکمه سبز Download Node.js (LTS) را بزنید
echo   3) فایل msi را نصب کنید (همه‌جا Next و در آخر Install)
echo   4) لپ‌تاپ را یک بار خاموش و روشن کنید
echo   5) دوباره روی همین فایل دوبار کلیک کنید
echo.
pause
exit /b 1

:downloadfail
echo   [!] گرفتن پروژه از اینترنت انجام نشد.
echo.
echo   اتصال به GitHub برقرار نشد (فیلترینگ، قطعی، یا پروکسی).
echo.
echo   راه دیگر:
echo     1) این آدرس را در مرورگر باز کنید (با فیلترشکن اگر لازم است):
echo        %PROJECT_URL%
echo     2) فایل zip را در همین پوشه بگذارید:
echo        %DIR%
echo     3) نامش را دقیقا بگذارید:  %ZIP_NAME%
echo     4) دوباره روی همین فایل دوبار کلیک کنید
echo.
pause
exit /b 1

:extractfail
echo   [!] باز کردن فایل‌های پروژه انجام نشد.
echo.
echo   1) فایل %ZIP_NAME% را در همین پوشه نگاه کنید
echo   2) دوباره روی همین فایل دوبار کلیک کنید
echo.
echo   اگر تکرار شد، این پیام را بفرستید:
echo        extract failed in %DIR%
echo.
pause
exit /b 1

:setupfailed
echo.
echo   [!] راه‌اندازی کامل نشد.
echo.
echo   این فایل را برای پشتیبانی بفرستید:
echo        %DIR%\nasra-setup-log.txt
echo.
echo   (رمز ورود و توکن داخل آن نیست، پس فرستادنش امن است.)
echo.
pause
exit /b 1
