from pathlib import Path
import re

patch_path = Path('.github/scripts/ime_v09_patch.py')
src = patch_path.read_text(encoding='utf-8')

# Remove the one accidental duplicate Java switch case from the patch source.
src = src.replace('            case "可": return Arrays.asList("以","能","是");\n', '', 1)

# Replace the brittle exact-string Cangjie mistype insertion with a whitespace-safe regex.
start_marker = "needle = '''        if(pref!=null) for(String item:boost(pref)) out.add(item);"
start = src.find(start_marker)
end = src.find('\nmistype_helper = ', start)
if start < 0 or end < 0:
    raise SystemExit('Could not locate brittle Cangjie mistype patch block')

robust = r'''pattern = re.compile(
    r'(List<String> pref=cj\.get\(cjCode\);\n(?P<indent>[ \t]*)if\(pref!=null\) for\(String item:boost\(pref\)\) out\.add\(item\);\n)(?P=indent)return new ArrayList<>\(out\);'
)
m = pattern.search(s)
if not m:
    raise SystemExit('v0.9 patch failed: Cangjie candidate return not found (regex)')
indent = m.group('indent')
replacement = (
    m.group(1)
    + indent + 'if(out.isEmpty()) out.addAll(cangjieMistypeCandidates(cjCode));\n'
    + indent + 'return new ArrayList<>(out);'
)
s = s[:m.start()] + replacement + s[m.end():]
'''

src = src[:start] + robust + src[end:]
ns = {'__name__': '__main__'}
exec(compile(src, str(patch_path), 'exec'), ns)

# re.sub replacement strings interpret backslashes, which corrupts kaomoji such as
# shrug/table-flip. Rebuild those two generated Java arrays with a callable
# replacement so Java receives literal escaped backslashes.
java_path = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
java = java_path.read_text(encoding='utf-8')
for name in ('emojis', 'kaos'):
    block = ns['java_array'](name, ns[name])
    pattern = rf'    private final String\[\] {name} = \{{.*?\n    \}};'
    java, count = re.subn(pattern, lambda m, block=block: block, java, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f'Could not repair generated Java array: {name}')
java_path.write_text(java, encoding='utf-8')
print('Repaired Java escaping for emoji/kaomoji arrays')
