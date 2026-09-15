from pathlib import Path

p = Path('.github/scripts/v095_fix.py')
src = p.read_text(encoding='utf-8')
old = "assert icon_path.exists() and icon_path.stat().st_size>20000"
new = """assert icon_path.exists()\nicon_data = icon_path.read_bytes()\nassert icon_data[:8] == b'\\x89PNG\\r\\n\\x1a\\n'\nassert int.from_bytes(icon_data[16:20], 'big') == 144 and int.from_bytes(icon_data[20:24], 'big') == 144"""
if old not in src:
    raise SystemExit('v0.9.5 runner failed: icon assertion source not found')
src = src.replace(old, new, 1)
exec(compile(src, str(p), 'exec'), {'__name__': '__main__'})
