@echo off
chcp 65001 >nul
title سنجش عملکرد ۲ ماهه نواحی نسرا - مقیاس واقعی (۰ تا ۱۰۰)
cd /d "%~dp0"
python calculate_period_scores.py 2 0
pause
