@echo off
chcp 65001 >nul
cd /d "%~dp0"
python quarterly_aggregation.py 2 1405
pause
