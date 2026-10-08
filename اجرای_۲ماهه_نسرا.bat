@echo off
chcp 65001 >nul
title سنجش عملکرد ۲ ماهه نواحی نسرا - مقیاس نسرا (۷۰ تا ۱۰۰)
cd /d "%~dp0"
python calculate_period_scores.py 2 70
pause
