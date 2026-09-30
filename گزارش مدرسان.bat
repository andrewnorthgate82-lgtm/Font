@echo off
rem ============================================================
rem  Instructor Report Runner  (Gozaresh Modaresan)
rem  Double-click to run  /  or drag an Excel file onto me
rem  All Persian messages appear after the script starts.
rem ============================================================
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title Gozaresh Modaresan

echo.
echo  ================================================
echo    Instructor Report  -  Gozaresh Modaresan
echo  ================================================
echo.

rem ---------- 1) find Python ----------
call :FINDPY
if defined PY goto HAVEPY

rem ---------- Python missing: download + silent install (no browser) ----------
echo  Python is not installed. It will be downloaded and
echo  installed automatically. Please wait...
echo.
echo  [Step 1/2] Downloading Python (~25 MB)...
set "PYINST=%TEMP%\python-setup.exe"
curl -# -L -o "%PYINST%" "https://www.python.org/ftp/python/3.12.10/python-3.12.10-amd64.exe"
if not exist "%PYINST%" (
    echo.
    echo  ERROR: download failed. Check your internet connection and run me again.
    goto END
)
echo.
echo  [Step 2/2] Installing Python silently (a few minutes). Do NOT close this window...
start /wait "" "%PYINST%" /quiet InstallAllUsers=0 PrependPath=1 Include_test=0
call :FINDPY
if defined PY (
    del "%PYINST%" >nul 2>&1
    echo  Python installed successfully.
    echo.
    goto HAVEPY
)
echo.
echo  Silent install did not complete. The Python installer window will open now:
echo    1) Check the box "Add python.exe to PATH"
echo    2) Click "Install Now"
echo    3) After it finishes, double-click me again.
start "" "%PYINST%"
goto END

:HAVEPY
rem ---------- 2) get the script from GitHub if it is not next to me ----------
if not exist "instructor_report.py" (
    echo  Downloading script from GitHub...
    curl -s -L -o "instructor_report.py" "https://raw.githubusercontent.com/andrewnorthgate82-lgtm/Font/arena/01a0f15b-font/instructor_report.py"
)
if not exist "instructor_report.py" (
    echo  ERROR: could not download. Put instructor_report.py next to this file.
    goto END
)

rem ---------- 3) install requirement (first run only) ----------
%PY% -c "import openpyxl" >nul 2>&1
if errorlevel 1 (
    echo  Installing requirement - first run only, please wait...
    %PY% -m pip install --quiet openpyxl
    %PY% -c "import openpyxl" >nul 2>&1
    if errorlevel 1 (
        %PY% -m pip install --quiet --user openpyxl
    )
    %PY% -c "import openpyxl" >nul 2>&1
    if errorlevel 1 (
        echo  ERROR: could not install openpyxl. Check your internet and run me again.
        goto END
    )
)

rem ---------- 4) run ----------
echo.
%PY% instructor_report.py %*
echo.
echo  ------------------------------------------------
echo  Done. The output Excel file was created next to your input file.
goto END

:END
echo.
pause
goto :eof

rem ---------- subroutine: find Python everywhere ----------
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
