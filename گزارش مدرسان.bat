@echo off
rem ============================================================
rem   Instructor Report Runner  -  ابزار گزارش‌گیری مدرسان
rem   Double-click to run  /  or drag an Excel file onto me
rem ============================================================
chcp 65001 >nul
cd /d "%~dp0"
title گزارش مدرسان

echo.
echo  ================================================
echo        ابزار گزارش‌گیری مدرسان / سخنرانان
echo  ================================================
echo.

rem ---------- 1) پیدا کردن پایتون ----------
set "PY="
py -3 --version >nul 2>&1 && set "PY=py -3"
if not defined PY (
    python --version >nul 2>&1 && set "PY=python"
)
if not defined PY goto NOPYTHON

rem ---------- 2) اگر اسکریپت کنار فایل نبود، از گیت‌هاب دانلود کن ----------
if not exist "instructor_report.py" (
    echo  در حال دانلود اسکریپت از گیت‌هاب...
    curl -s -L -o "instructor_report.py" "https://raw.githubusercontent.com/andrewnorthgate82-lgtm/Font/arena/01a0f15b-font/instructor_report.py"
)
if not exist "instructor_report.py" (
    echo  خطا: دانلود انجام نشد. فایل instructor_report.py را کنار این فایل قرار دهید.
    goto END
)

rem ---------- 3) نصب پیش‌نیاز - فقط بار اول ----------
%PY% -c "import openpyxl" >nul 2>&1
if errorlevel 1 (
    echo  در حال نصب پیش‌نیاز - فقط بار اول، کمی صبر کنید...
    %PY% -m pip install --quiet openpyxl
    %PY% -c "import openpyxl" >nul 2>&1
    if errorlevel 1 (
        %PY% -m pip install --quiet --user openpyxl
    )
    %PY% -c "import openpyxl" >nul 2>&1
    if errorlevel 1 (
        echo  خطا در نصب پیش‌نیاز. اتصال اینترنت را بررسی و دوباره اجرا کنید.
        goto END
    )
)

rem ---------- 4) اجرا ----------
echo.
%PY% instructor_report.py %*
echo.
echo  ------------------------------------------------
echo  کار تمام شد. فایل خروجی کنار فایل اکسل ساخته شد.
goto END

:NOPYTHON
echo  پایتون روی این سیستم نصب نیست.
echo.
echo  الان صفحه دانلود پایتون باز می‌شود:
echo    1- دکمه زرد Download Python را بزنید
echo    2- هنگام نصب حتماً تیک "Add Python to PATH" را بزنید
echo    3- بعد از نصب، دوباره روی همین فایل دابل‌کلیک کنید
echo.
start https://www.python.org/downloads/
goto END

:END
echo.
pause
