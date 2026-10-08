@echo off
chcp 65001 > nul
cd /d "%~dp0"
title Nasra Performance - Tir 1405
python run_aggregation.py 4 1405
if errorlevel 1 (
    py run_aggregation.py 4 1405
)
pause
