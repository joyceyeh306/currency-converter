from pathlib import Path
import base64
import re

# Reuse the exact user-selected artwork already embedded for v1.8.
old_script = Path("video-compressor/ci_icon_v18.py").read_text(encoding="utf-8")
match = re.search(r'ICON_B64\s*=\s*"""(.*?)"""', old_script, re.S)
if not match:
    raise SystemExit("Could not find embedded icon artwork")
icon_bytes = base64.b64decode(match.group(1))
if len(icon_bytes) < 10000:
    raise SystemExit(f"Icon artwork looks too small: {len(icon_bytes)} bytes")

res = Path("video-compressor/app/src/main/res")
(res / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
(res / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
(res / "values").mkdir(parents=True, exist_ok=True)

# Keep the selected picture exactly as supplied; only package it as an Android launcher icon.
(res / "drawable-nodpi" / "icon_art_v19.jpg").write_bytes(icon_bytes)

(res / "values" / "icon_v19_colors.xml").write_text(
    '''<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="icon_v19_background">#FFFFFF</color>\n</resources>\n''',
    encoding="utf-8",
)

adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/icon_v19_background" />
    <foreground android:drawable="@drawable/icon_art_v19" />
</adaptive-icon>
'''
(res / "mipmap-anydpi-v26" / "ic_launcher_v19.xml").write_text(adaptive, encoding="utf-8")
(res / "mipmap-anydpi-v26" / "ic_launcher_round_v19.xml").write_text(adaptive, encoding="utf-8")

# Use a fresh resource name so OEM launcher caches cannot keep the broken v1.8 icon.
manifest = Path("video-compressor/app/src/main/AndroidManifest.xml")
s = manifest.read_text(encoding="utf-8")
s = re.sub(r'android:icon="@[^"]+"', 'android:icon="@mipmap/ic_launcher_v19"', s, count=1)
s = re.sub(r'android:roundIcon="@[^"]+"', 'android:roundIcon="@mipmap/ic_launcher_round_v19"', s, count=1)
manifest.write_text(s, encoding="utf-8")
