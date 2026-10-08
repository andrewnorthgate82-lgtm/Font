@echo off
chcp 65001 >nul
title سنجش عملکرد ۶ ماهه نواحی نسرا
cd /d "%~dp0"
python calculate_period_scores.py 6
pause
