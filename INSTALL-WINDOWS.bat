@echo off
REM ===========================================================================
REM  نسرا اصفهان - نصب و اجرا
REM
REM  روش استفاده: روی همین فایل دوبار کلیک کنید.
REM  اگر ویندوز هشدار آبی داد:  More info  ->  Run anyway
REM
REM  دو حالت دارد:
REM   1) اگر خودِ پروژه است:  نصب و اجرا می‌کند.
REM   2) اگر فقط همین فایل را از zip جدا کرده‌اید:  پروژه را از اینترنت
REM      می‌گیرد، در همین پوشه باز می‌کند و بعد نصب را ادامه می‌دهد.
REM
REM  این فایل هیچ کار دیگری نمی‌کند: Node.js را در همین پوشه اجرا می‌کند،
REM  و در حالت دوم فقط یک فایل zip از GitHub دانلود می‌کند.
REM ===========================================================================
setlocal EnableExtensions
chcp 65001 >nul
title نسرا اصفهان
cd /d "%~dp0"

set "PROJECT_URL=https://codeload.github.com/andrewnorthgate82-lgtm/Font/zip/refs/heads/arena/b48083ae-font"
set "ZIP_NAME=nasra.zip"
set "EXE_DIR=%CD%"

echo.
echo   ============================================
echo    نسرا اصفهان - استودیوی رسانه
echo   ============================================
echo.

REM --- اگر خود پروژه است، همان‌جا ادامه بده -------------------------------
if exist "%EXE_DIR%\package.json" goto checknode

REM --- وگرنه پروژه را بگیر ------------------------------------------------
echo   پروژه در این پوشه نیست؛ در حال گرفتن از اینترنت...
echo.
echo   مسیر: %EXE_DIR%
echo.

if exist "%EXE_DIR%\package.json" goto checknode

REM --- اگر از قبل nasra.zip را در همین پوشه گذاشته‌اید، از آن استفاده کن ---
if exist "%EXE_DIR%\%ZIP_NAME%" (
  echo   فایل %ZIP_NAME% از قبل موجود است؛ از همان استفاده می‌شود.
  goto extractnow
)

curl.exe -L --fail --silent --show-error -o "%EXE_DIR%\%ZIP_NAME%" "%PROJECT_URL%"
if errorlevel 1 goto downloadfail

:extractnow

echo   در حال باز کردن...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$dest='%EXE_DIR%';" ^
  "Expand-Archive -LiteralPath (Join-Path $dest '%ZIP_NAME%') -DestinationPath $dest -Force;" ^
  "$root = Get-ChildItem -LiteralPath $dest -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'package.json') } | Select-Object -First 1;" ^
  "if (-not $root) { Write-Host '  [!] پوشه پروژه بعد از باز کردن پیدا نشد.'; exit 3 };" ^
  "Get-ChildItem -LiteralPath $root.FullName -Force | ForEach-Object { Move-Item -LiteralPath $_.FullName -Destination $dest -Force };" ^
  "Remove-Item -LiteralPath $root.FullName -Recurse -Force;" ^
  "Remove-Item -LiteralPath (Join-Path $dest '%ZIP_NAME%') -Force -ErrorAction SilentlyContinue;"
if errorlevel 1 goto extractfail

if not exist "%EXE_DIR%\package.json" goto extractfail

echo   پروژه در همین پوشه آماده شد.
echo.
goto checknode

REM --- Node نصب است؟ ------------------------------------------------------
:checknode
where node >nul 2>nul
if errorlevel 1 goto nonode

if not exist "%EXE_DIR%\.env" goto firstrun
goto run

REM --- پیام‌ها ------------------------------------------------------------
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
echo   این یعنی اتصال به GitHub برقرار نشد (فیلترینگ، قطعی، یا پروکسی).
echo.
echo   راه دیگر، بدون نیاز به اینترنت از داخل این پنجره:
echo.
echo     1) این آدرس را در مرورگر باز کنید (با فیلترشکن اگر لازم است):
echo        https://codeload.github.com/andrewnorthgate82-lgtm/Font/zip/refs/heads/arena/b48083ae-font
echo     2) فایل zip را در همین پوشه بگذارید:
echo        %EXE_DIR%
echo     3) نامش را دقیقا بگذارید:  %ZIP_NAME%
echo     4) دوباره روی همین فایل دوبار کلیک کنید
echo.
echo   اگر فایل zip را از قبل دارید، فقط مرحله 2 و 3 و 4 را انجام دهید.
echo   برای بررسی خطای شبکه، این آدرس را در مرورگر امتحان کنید:
echo        https://github.com
echo.
pause
exit /b 1

:extractfail
echo   [!] باز کردن فایل‌های پروژه انجام نشد.
echo.
echo   یک بار دیگر روی همین فایل دوبار کلیک کنید.
echo   اگر تکرار شد، متن خطای بالا را برای پشتیبانی بفرستید.
echo.
pause
exit /b 1

REM --- اجرا ---------------------------------------------------------------
:firstrun
echo   بار اول است. در حال آماده‌سازی (حدود ۳۰ ثانیه)...
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
