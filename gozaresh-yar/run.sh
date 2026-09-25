#!/usr/bin/env bash
# ==========================================================
#  گزارش‌یار هوشمند سازمانی — اجرا روی لینوکس / مک
#  اجرا:  bash run.sh        (یا)      bash run.sh --input ./داده‌ها
# ==========================================================
set -e
cd "$(dirname "$0")"

PY=python3
command -v "$PY" >/dev/null 2>&1 || PY=python
if ! command -v "$PY" >/dev/null 2>&1; then
  echo "  [!] پایتون روی این رایانه نصب نیست. لطفاً Python 3 را نصب کنید (python.org)."
  exit 1
fi

echo "  بررسی و نصب پیش‌نیازها (فقط بار اول) ..."
"$PY" -m pip install --quiet --disable-pip-version-check -r requirements.txt || true

echo "  در حال اجرای گزارش‌یار ..."
exec "$PY" gozaresh.py "$@"
