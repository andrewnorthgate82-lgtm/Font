#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR/karkard"

if [ ! -x ".venv/bin/python" ]; then
  echo "Creating local Python environment..."
  python3 -m venv .venv
fi

echo "Installing required packages..."
".venv/bin/python" -m pip install -r requirements.txt

echo
echo "Starting Karkard generator..."
echo
".venv/bin/python" karkard.py

echo
echo "Done. Output files are inside: $DIR/karkard/output"
read -r -p "Press Enter to close..." _
