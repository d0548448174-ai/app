#!/usr/bin/env python3
"""Basic static checks for the Zipedia single-page application."""
from collections import Counter
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
html = (root / "zipedia" / "index.html").read_text(encoding="utf-8")
js = (root / "zipedia" / "app.js").read_text(encoding="utf-8")

ids = re.findall(r'\bid="([^"]+)"', html)
duplicates = sorted(key for key, count in Counter(ids).items() if count > 1)
refs = set(re.findall(r'\$\("([^"]+)"\)', js))
missing = sorted(refs - set(ids))

if duplicates:
    raise SystemExit("Duplicate HTML ids: " + ", ".join(duplicates))
if missing:
    raise SystemExit("Missing HTML element ids: " + ", ".join(missing))
print(f"Static HTML check passed: {len(ids)} unique ids; all {len(refs)} app.js id references exist.")
