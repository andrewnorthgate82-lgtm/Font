@echo off
chcp 65001 >nul
title سنجش عملکرد ۳ ماهه نواحی نسرا
cd /d "%~dp0"
python "محاسبه_نمرات_دوره‌ای.py" 3
pause
