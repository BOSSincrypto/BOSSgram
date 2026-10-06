r"""Verify a built BOSSgram APK: identity, signature, content.

Usage: python tools/check-apk.py <app.apk>
Checks:
  1. application-id starts with com.bossgram (unique install identity)
  2. label contains BOSSgram
  3. signature cert SHA-256 printed (compare across releases: same = updates install over)
  4. dex contains com/bossgram hooks + BossGram settings row
  5. launcher icons are ours (byte-compare with bossgram/assets/icons source art)

Needs Android SDK (aapt/apksigner from build-tools). Exit 1 on any FAIL.
"""
import hashlib
import os
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def sdk_file(name):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or \
        r"C:\Users\boss\AppData\Local\Android\Sdk"
    for bt in sorted(Path(sdk, "build-tools").glob("*"), reverse=True):
        p = bt / name
        if p.exists():
            return str(p)
    sys.exit(f"{name} not found in {sdk}/build-tools")


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.stdout + r.stderr


def main():
    apk = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if not apk or not apk.is_file():
        sys.exit("usage: python tools/check-apk.py <app.apk>")
    fails = []

    aapt = sdk_file("aapt.exe")
    badging = run([aapt, "dump", "badging", str(apk)])
    pkg = next((l for l in badging.splitlines() if l.startswith("package:")), "")
    label = next((l for l in badging.splitlines() if l.startswith("application-label:")), "")
    print("PKG:", pkg[:120])
    print("LABEL:", label[:80])
    if "com.bossgram.messenger" not in pkg:
        fails.append("package is not com.bossgram.*")
    if "BOSSgram" not in label:
        fails.append("label has no BOSSgram")

    apksigner = str(Path(sdk_file("apksigner.bat")).with_suffix(".bat"))
    certs = run([apksigner, "verify", "--print-certs", str(apk)])
    sha = next((l.strip() for l in certs.splitlines() if "SHA-256" in l), "")
    print("CERT:", sha[:120])
    if not sha:
        fails.append("no signature cert found")

    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        dex = b"".join(z.read(n) for n in names if n.endswith(".dex"))
        for needle in (b"Lcom/bossgram/core/BossHooks;",
                       b"Lcom/bossgram/modules/antidelete/BossSettingsActivity;",
                       b"BossGram"):
            print(("FOUND " if needle in dex else "MISS  ") + needle.decode())
            if needle not in dex:
                fails.append(f"dex misses {needle!r}")
        for res in ("res/mipmap-xxxhdpi-v4/icon_6_launcher.png",
                    "res/mipmap-xxxhdpi-v4/ic_launcher.png"):
            if res not in names:
                fails.append(f"apk misses {res}")
                continue
            # NOTE: aapt may re-encode PNGs, so compare pixels, not bytes.
            from PIL import Image
            import io as _io
            src = {"icon_6_launcher.png": "mipmap-xxxhdpi/icon_6_launcher.png",
                   "ic_launcher.png": "mipmap-xxxhdpi/ic_launcher.png"}[res.split("/")[-1]]
            want = Image.open(ROOT / "bossgram" / "assets" / "icons" / src).convert("RGBA")
            got = Image.open(_io.BytesIO(z.read(res))).convert("RGBA")
            ok = want.size == got.size and list(want.getdata()) == list(got.getdata())
            print(("ICON-OK " if ok else "ICON-DIFF ") + res)
            if not ok:
                fails.append(f"launcher art differs: {res}")

    if fails:
        print("\nFAIL:")
        for f in fails:
            print(" -", f)
        sys.exit(1)
    print("\nOK: identity, signature present, content verified.")


if __name__ == "__main__":
    main()
