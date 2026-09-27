@echo off
chcp 65001 >nul
title سنجش عملکرد ۳ ماهه نواحی نسرا - مقیاس نسرا (۷۰ تا ۱۰۰)
cd /d "%~dp0"
python calculate_period_scores.py 3 70
pause
