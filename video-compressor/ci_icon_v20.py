from pathlib import Path
import base64
import re

# Reuse the exact user-selected artwork already embedded for v1.8.
# This time package it as a normal launcher bitmap instead of an adaptive-icon foreground.
old_script = Path("video-compressor/ci_icon_v18.py").read_text(encoding="utf-8")
match = re.search(r'ICON_B64\s*=\s*"""(.*?)"""', old_script, re.S)
if not match:
    raise SystemExit("Could not find embedded icon artwork")
icon_bytes = base64.b64decode(match.group(1))
if len(icon_bytes) < 4000:
    raise SystemExit(f"Icon artwork decode failed: {len(icon_bytes)} bytes")

res = Path("video-compressor/app/src/main/res")
(res / "mipmap-nodpi").mkdir(parents=True, exist_ok=True)
icon_path = res / "mipmap-nodpi" / "ic_launcher_v20.jpg"
icon_path.write_bytes(icon_bytes)

manifest = Path("video-compressor/app/src/main/AndroidManifest.xml")
s = manifest.read_text(encoding="utf-8")

# Use the bitmap directly for both package and launcher activity icons.
# A fresh resource name avoids accidentally resolving the old Android-template icon.
s = re.sub(r'android:icon="@[^"]+"', 'android:icon="@mipmap/ic_launcher_v20"', s, count=1)
s = re.sub(r'android:roundIcon="@[^"]+"', 'android:roundIcon="@mipmap/ic_launcher_v20"', s, count=1)

# Give MainActivity its own explicit icon too, so launchers do not inherit/cache the old app icon.
activity_pat = r'(<activity\s+\n\s*android:name="\.MainActivity"\s*\n\s*android:exported="true")'
if re.search(activity_pat, s):
    s = re.sub(activity_pat, r'\1\n            android:icon="@mipmap/ic_launcher_v20"', s, count=1)
elif 'android:name=".MainActivity"' in s and 'android:icon="@mipmap/ic_launcher_v20"' not in s.split('android:name=".MainActivity"',1)[1].split('>',1)[0]:
    s = s.replace('android:name=".MainActivity"', 'android:name=".MainActivity"\n            android:icon="@mipmap/ic_launcher_v20"', 1)

manifest.write_text(s, encoding="utf-8")
print(f"Installed static launcher icon: {icon_path} ({len(icon_bytes)} bytes)")
