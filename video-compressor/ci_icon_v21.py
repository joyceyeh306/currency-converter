from pathlib import Path
import base64
import hashlib
import re

# Reuse the user-selected white film panel + red play button + yellow download arrow artwork
# that is already embedded in ci_icon_v18.py. We only change how Android packages it.
old_script = Path("video-compressor/ci_icon_v18.py").read_text(encoding="utf-8")
match = re.search(r'ICON_B64\s*=\s*"""(.*?)"""', old_script, re.S)
if not match:
    raise SystemExit("Could not find selected icon artwork")
icon_bytes = base64.b64decode(match.group(1))
if len(icon_bytes) < 4000:
    raise SystemExit(f"Selected icon decode failed: {len(icon_bytes)} bytes")

res = Path("video-compressor/app/src/main/res")

# IMPORTANT: remove Android 8+ adaptive-icon overrides. Those old XML files point to
# the Android Studio robot foreground and can win over bitmap launcher resources.
for p in [
    res / "mipmap-anydpi-v26" / "ic_launcher.xml",
    res / "mipmap-anydpi-v26" / "ic_launcher_round.xml",
]:
    if p.exists():
        p.unlink()

# Put the selected artwork under the standard launcher names at every density.
# The same source artwork is intentional: Android/ColorOS will scale it for the launcher.
for density in ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]:
    d = res / f"mipmap-{density}"
    d.mkdir(parents=True, exist_ok=True)
    (d / "ic_launcher.jpg").write_bytes(icon_bytes)
    (d / "ic_launcher_round.jpg").write_bytes(icon_bytes)

# Remove experimental launcher resources from previous attempts in the CI workspace.
for p in [
    res / "mipmap-nodpi" / "ic_launcher_v20.jpg",
    res / "mipmap-anydpi-v26" / "ic_launcher_v19.xml",
    res / "mipmap-anydpi-v26" / "ic_launcher_round_v19.xml",
]:
    if p.exists():
        p.unlink()

manifest = Path("video-compressor/app/src/main/AndroidManifest.xml")
s = manifest.read_text(encoding="utf-8")
s = re.sub(r'android:icon="@[^"]+"', 'android:icon="@mipmap/ic_launcher"', s, count=1)
s = re.sub(r'android:roundIcon="@[^"]+"', 'android:roundIcon="@mipmap/ic_launcher_round"', s, count=1)
# Remove any activity-level icon left by earlier experiments; MainActivity must inherit app icon.
s = re.sub(
    r'(android:name="\.MainActivity"\s*\n\s*android:exported="true")\s*\n\s*android:icon="@[^"]+"',
    r'\1',
    s,
    count=1,
)
manifest.write_text(s, encoding="utf-8")

print("Selected launcher artwork SHA256:", hashlib.sha256(icon_bytes).hexdigest())
print("Selected launcher artwork bytes:", len(icon_bytes))
for density in ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]:
    p = res / f"mipmap-{density}" / "ic_launcher.jpg"
    print(density, p, p.stat().st_size)
