"""BOSSgram pre-commit security gate. Fails on BLOCKER patterns, warns on SUSPICIOUS.

Run: python tools/security-check.py [--staged]
- default: scans working tree files (bossgram/, patches/, tools/).
- --staged: scans only staged changes (git diff --cached).
Exit 1 on any BLOCKER. Warnings printed, exit 0.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

BLOCKERS = [
    ("dynamic-code-load", re.compile(r"DexClassLoader|PathClassLoader|loadDex|System\.load\s*\(\s*\"?/", re.IGNORECASE)),
    ("shell-exec", re.compile(r"Runtime\.getRuntime\(\)\.exec|ProcessBuilder|/system/bin/su\b|\bchikarabbit\b", re.IGNORECASE)),
    ("js-bridge", re.compile(r"addJavascriptInterface", re.IGNORECASE)),
    ("hardcoded-secret", re.compile(r"(api_key|apikey|api_secret|BEGIN (RSA |EC )?PRIVATE KEY|RELEASE_KEY_PASSWORD|RELEASE_STORE_PASSWORD)\s*[:=]", re.IGNORECASE)),
    ("webhook-exfil", re.compile(r"https?://(discord\.com/api/webhooks|api\.telegram\.org/bot\d+:|hooks\.slack\.com/)", re.IGNORECASE)),
    ("reflection-hide", re.compile(r"setAccessible\s*\(\s*true\s*\)", re.IGNORECASE)),
    ("install-packages", re.compile(r"REQUEST_INSTALL_PACKAGES", re.IGNORECASE)),
    ("log-message-text", re.compile(r"(FileLog|Log)\.[deviw]\(.*(messageText|message\.messageText|textSnapshots)", re.IGNORECASE)),
]

SUSPICIOUS = [
    ("network-call", re.compile(r"HttpURLConnection|OkHttpClient|new URL\(|fetch\(|XMLHttpRequest", re.IGNORECASE)),
    ("webview", re.compile(r"WebView|setJavaScriptEnabled", re.IGNORECASE)),
    ("exported-component", re.compile(r"android:exported\s*=\s*\"true\"", re.IGNORECASE)),
    ("base64-blob", re.compile(r"[A-Za-z0-9+/]{200,}={0,2}", re.IGNORECASE)),
    ("reflection", re.compile(r"getDeclaredMethod|getDeclaredField|Method\.invoke", re.IGNORECASE)),
    ("crypto-custom", re.compile(r"SecretKeySpec|Cipher\.getInstance", re.IGNORECASE)),
]

SCAN_DIRS = ["bossgram", "patches", "tools"]
SCAN_EXTS = {".java", ".kt", ".py", ".ps1", ".sh", ".patch", ".xml", ".gradle"}
SELF = Path(__file__).resolve()


def files_to_scan(staged: bool):
    if staged:
        out = subprocess.run(["git", "diff", "--cached", "--name-only", "-z"], cwd=ROOT, capture_output=True)
        names = [n for n in out.stdout.decode("utf-8", errors="ignore").split("\0") if n]
        return [ROOT / n for n in names if (ROOT / n).is_file()]
    files = []
    for d in SCAN_DIRS:
        p = ROOT / d
        if not p.exists():
            continue
        for f in p.rglob("*"):
            if f.is_file() and f.suffix in SCAN_EXTS:
                files.append(f)
    return files


def main() -> int:
    staged = "--staged" in sys.argv
    blockers = 0
    for f in files_to_scan(staged):
        if f.resolve() == SELF:
            continue  # свои regex-литералы не сканируем
        try:
            text = f.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        rel = f.relative_to(ROOT)
        for name, rx in BLOCKERS:
            for m in rx.finditer(text):
                line = text.count("\n", 0, m.start()) + 1
                print(f"BLOCKER [{name}] {rel}:{line}: {m.group(0)[:100]}")
                blockers += 1
        for name, rx in SUSPICIOUS:
            for m in rx.finditer(text):
                line = text.count("\n", 0, m.start()) + 1
                print(f"warn [{name}] {rel}:{line}: {m.group(0)[:100]}")
    if blockers:
        print(f"\nFAIL: {blockers} BLOCKER(s). Убрать или обосновать в PR.")
        return 1
    print("\nOK: blockers 0. warnings выше — просмотреть глазами.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
