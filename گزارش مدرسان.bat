@echo off
rem ============================================================
rem   Instructor Report Runner  -  ابزار گزارش‌گیری مدرسان
rem   Double-click to run  /  or drag an Excel file onto me
rem ============================================================
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title گزارش مدرسان

echo.
echo  ================================================
echo        ابزار گزارش‌گیری مدرسان / سخنرانان
echo  ================================================
echo.

rem ---------- 1) پیدا کردن پایتون ----------
call :FINDPY
if defined PY goto HAVEPY

rem ---------- پایتون نصب نیست: دانلود و نصب کاملاً خودکار (بدون مرورگر) ----------
echo  پایتون روی این سیستم نصب نیست.
echo  نگران نباشید — الان به صورت خودکار دانلود و نصب می‌شود.
echo.
echo  مرحله 1 از 2: دانلود پایتون - حدود 25 مگابایت - صبر کنید...
echo.
set "PYINST=%TEMP%\python-setup.exe"
curl -# -L -o "%PYINST%" "https://www.python.org/ftp/python/3.12.10/python-3.12.10-amd64.exe"
if not exist "%PYINST%" (
    echo.
    echo  خطا در دانلود پایتون. اتصال اینترنت را بررسی کنید و دوباره اجرا کنید.
    goto END
)
echo.
echo  مرحله 2 از 2: نصب پایتون - چند دقیقه طول می‌کشد - این پنجره را نبندید...
start /wait "" "%PYINST%" /quiet InstallAllUsers=0 PrependPath=1 Include_test=0
call :FINDPY
if defined PY (
    del "%PYINST%" >nul 2>&1
    echo  پایتون با موفقیت نصب شد.
    echo.
    goto HAVEPY
)
echo.
echo  نصب بی‌صدا کامل نشد. الان پنجره نصب پایتون باز می‌شود:
echo     1- پایین پنجره، تیک "Add python.exe to PATH" را بزنید
echo     2- روی "Install Now" کلیک کنید
echo     3- بعد از پایان نصب، دوباره روی همین فایل دابل‌کلیک کنید
start "" "%PYINST%"
goto END

:HAVEPY
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

:END
echo.
pause
goto :eof

rem ---------- زیربرنامه: پیدا کردن پایتون در همه حالت‌ها ----------
:FINDPY
set "PY="
py -3 --version >nul 2>&1 && set "PY=py -3"
if not defined PY (
    python --version >nul 2>&1 && set "PY=python"
)
if not defined PY (
    for /d %%D in ("%LocalAppData%\Programs\Python\Python3*") do (
        if exist "%%D\python.exe" set PY="%%D\python.exe"
    )
)
if not defined PY (
    for /d %%D in ("%ProgramFiles%\Python3*") do (
        if exist "%%D\python.exe" set PY="%%D\python.exe"
    )
)
goto :eof
