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
    + indent + 'if(out.isEmpty()) out.addAll(cangjieMistypeCandidates(cjCode));\\n'
    + indent + 'return new ArrayList<>(out);'
)
s = s[:m.start()] + replacement + s[m.end():]
'''

src = src[:start] + robust + src[end:]
exec(compile(src, str(patch_path), 'exec'), {'__name__': '__main__'})
