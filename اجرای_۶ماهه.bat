@echo off
chcp 65001 >nul
title سنجش عملکرد ۶ ماهه نواحی نسرا
cd /d "%~dp0"
python "محاسبه_نمرات_دوره‌ای.py" 6
pause
