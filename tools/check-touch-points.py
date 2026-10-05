"""Fail if own code leaked into upstream sources.

Allowed upstream touches: exactly the hook lines listed in patches/0001-boss-hooks.patch.
Everything else must live under bossgram/.
Run: python tools/check-touch-points.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ALLOW_RE = re.compile(r"BossHooks\.(init|onMessagesDeleted|onChatOpened|applyCustomTheme)")

# everybody forgets: this repo is skeleton, Telegram sources live next door.
# If TMessagesProj exists here (full fork), scan it. Else scan bossgram/ presence only.
TM = ROOT / "TMessagesProj"
if not TM.exists():
    print("OK (skeleton): no TMessagesProj here, nothing to leak yet.")
    print("Rule: свой код только в bossgram/, в org.telegram.* только 3 строки BossHooks.")
    sys.exit(0)

bad = []
for p in TM.rglob("*.java"):
    # skip own package
    if "com/bossgram" in p.as_posix() or "com\\bossgram" in p.as_posix():
        continue
    try:
        t = p.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        continue
    if "com.bossgram" in t or "BossHooks" in t:
        # allow only single hook-line files
        lines = [ln for ln in t.splitlines() if "bossgram" in ln.lower() or "BossHooks" in ln]
        if not lines or not all(ALLOW_RE.search(ln) for ln in lines):
            bad.append((p, lines[:5]))

if bad:
    print("FAIL: свой код вне bossgram/:")
    for p, lines in bad[:20]:
        print(f" - {p.relative_to(ROOT)}")
        for ln in lines:
            print(f"    {ln.strip()[:160]}")
    sys.exit(1)
print("OK: upstream touched only by hooks.")
