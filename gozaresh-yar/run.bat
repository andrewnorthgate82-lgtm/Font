@echo off
REM ==========================================================
REM  گزارش‌یار هوشمند سازمانی — اجرا روی ویندوز
REM  فقط دوبار روی همین فایل کلیک کنید. تمام!
REM ==========================================================
chcp 65001 >nul
cd /d "%~dp0"

set PY=python
where py >nul 2>nul && set PY=py

echo.
echo   بررسی و نصب پیش‌نیازها (فقط بار اول کمی طول می‌کشد) ...
%PY% -m pip install --quiet --disable-pip-version-check -r requirements.txt

echo   در حال اجرای گزارش‌یار ...
echo.
%PY% gozaresh.py %*

echo.
echo   پایان اجرا. برای بستن پنجره، کلیدی بزنید.
pause >nul
