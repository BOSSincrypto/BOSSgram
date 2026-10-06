"""Bump APP_VERSION_CODE in the build tree's gradle.properties by 1.

Usage: python tools/bump-version.py [path/to/gradle.properties]
Default: ../Telegram-upstream/gradle.properties (sibling of this repo).
Each release MUST bump: Android treats same versionCode + same signature as
already-installed for Play, and users need visible progress.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
default = ROOT.parent / "Telegram-upstream" / "gradle.properties"
path = Path(sys.argv[1]) if len(sys.argv) > 1 else default

text = path.read_text(encoding="utf-8")
m = re.search(r"^APP_VERSION_CODE=(\d+)", text, re.M)
if not m:
    sys.exit(f"APP_VERSION_CODE not found in {path}")
old = int(m.group(1))
new = old + 1
path.write_text(text.replace(f"APP_VERSION_CODE={old}", f"APP_VERSION_CODE={new}", 1), encoding="utf-8")
print(f"{path.name}: {old} -> {new}")
