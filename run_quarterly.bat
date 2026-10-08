@echo off
chcp 65001 >nul
title NASRA Quarterly 3-Month Performance Aggregation
cd /d "%~dp0"
echo =====================================================================
echo  NASRA 3-MONTH PERFORMANCE AGGREGATION SYSTEM (ISFAHAN)
echo  Multiple Excel files per district aggregated (70 to 100 Score Scale)
echo =====================================================================
python quarterly_aggregation.py
pause
