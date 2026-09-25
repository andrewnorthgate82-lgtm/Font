@echo off
REM ==========================================================
REM  گزارش‌یار هوشمند سازمانی — اجرا روی ویندوز
REM  فقط دوبار روی همین فایل کلیک کنید. تمام!
REM ==========================================================
chcp 65001 >nul
cd /d "%~dp0"

set PY=python
where py >nul 2>nul && set PY=py
where %PY% >nul 2>nul || (
  echo.
  echo   [!] پایتون روی این رایانه نصب نیست.
  echo       لطفاً از سایت python.org نسخه‌ی Python 3 را نصب کنید و
  echo       هنگام نصب، تیک "Add python.exe to PATH" را بزنید.
  echo.
  pause >nul
  exit /b 1
)

echo.
echo   بررسی و نصب پیش‌نیازها (فقط بار اول کمی طول می‌کشد) ...
%PY% -m pip install --quiet --disable-pip-version-check -r requirements.txt

echo   در حال اجرای گزارش‌یار ...
echo.
%PY% gozaresh.py %*

echo.
echo   پایان اجرا. برای بستن پنجره، کلیدی بزنید.
pause >nul
