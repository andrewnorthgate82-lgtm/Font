@echo off
chcp 65001 >nul
cd /d "%~dp0"
python quarterly_aggregation.py 4 1405
pause
