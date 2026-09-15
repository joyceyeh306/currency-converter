from pathlib import Path

p = Path('.github/scripts/v096_fix.py')
src = p.read_text(encoding='utf-8')

old = "(?=    private void feedback\\(\\))"
new = "(?=\\s*private void feedback\\(\\))"
if old not in src:
    raise SystemExit('v0.9.6 runner failed: choose regex source not found')
src = src.replace(old, new, 1)

old_cursor = 'float delta=x-spaceCursorLastX; cursorCarry+=delta; float unit=dp(18);\n                int steps=(int)(cursorCarry/unit);\n                if(steps!=0){ svc.moveCursor(steps); cursorCarry-=steps*unit; spaceCursorLastX=x; }'
new_cursor = 'float delta=x-spaceCursorLastX; spaceCursorLastX=x; cursorCarry+=delta; float unit=dp(18);\n                int steps=(int)(cursorCarry/unit);\n                if(steps!=0){ svc.moveCursor(steps); cursorCarry-=steps*unit; }'
if old_cursor not in src:
    raise SystemExit('v0.9.6 runner failed: cursor delta source not found')
src = src.replace(old_cursor, new_cursor, 1)

old_icon = "icon_path=res/'drawable'/'ic_launcher_ajo.png'"
new_icon = "icon_path=res/'drawable-nodpi'/'ic_launcher_ajo.png'"
if old_icon not in src:
    raise SystemExit('v0.9.6 runner failed: icon source path not found')
src = src.replace(old_icon, new_icon, 1)

exec(compile(src, str(p), 'exec'), {'__name__': '__main__'})
