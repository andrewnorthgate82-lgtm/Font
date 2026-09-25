#!/usr/bin/env bash
# ==========================================================
#  گزارش‌یار هوشمند سازمانی — اجرا روی لینوکس / مک
#  اجرا:  bash run.sh        (یا)      bash run.sh --input ./داده‌ها
# ==========================================================
set -e
cd "$(dirname "$0")"

PY=python3
command -v "$PY" >/dev/null 2>&1 || PY=python

echo "  بررسی و نصب پیش‌نیازها (فقط بار اول) ..."
"$PY" -m pip install --quiet --disable-pip-version-check -r requirements.txt || true

echo "  در حال اجرای گزارش‌یار ..."
exec "$PY" gozaresh.py "$@"
