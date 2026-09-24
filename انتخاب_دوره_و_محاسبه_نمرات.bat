@echo off
chcp 65001 >nul
title سامانه سنجش نمرات دوره‌ای نواحی نسرا
cd /d "%~dp0"
python calculate_period_scores.py
pause
