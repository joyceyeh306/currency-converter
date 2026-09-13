from pathlib import Path

p = Path('app/src/main/java/com/joyce/currencyconverter/MainActivity.java')
s = p.read_text(encoding='utf-8')

countries = '''    private final String[] allFlagCountries = {
            "tw", "us", "eu", "jp", "kr", "gb", "ch", "no",
            "cn", "hk", "sg", "au", "nz", "ca", "th", "my",
            "id", "ph", "vn", "in", "ae", "sa", "tr", "se",
            "dk", "pl", "cz", "hu", "mx", "za"
    };\n\n'''

marker = '    private final Map<String, Double> rates = new LinkedHashMap<>();\n'
if 'allFlagCountries' not in s:
    if marker not in s:
        raise SystemExit('flag country insertion marker not found')
    s = s.replace(marker, countries + marker, 1)

s = s.replace('    private Bitmap flagsSprite;\n', '', 1)
s = s.replace('        flagsSprite = loadFlagsSprite();\n', '', 1)

start = s.find('    private Bitmap loadFlagsSprite() {')
end = s.find('    private void applyFlagDrawable(', start)
if start < 0 or end < 0:
    raise SystemExit('flag loader method block not found')

replacement = '''    private Bitmap flagBitmap(String code) {\n        Bitmap cached = flagCache.get(code);\n        if (cached != null) return cached;\n\n        int i = indexOfCode(code);\n        if (i < 0) return null;\n\n        String resourceName = "flag_" + allFlagCountries[i];\n        int resId = getResources().getIdentifier(resourceName, "drawable", getPackageName());\n        if (resId == 0) return null;\n\n        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId);\n        if (bitmap != null) flagCache.put(code, bitmap);\n        return bitmap;\n    }\n\n'''

s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')
print('Resource flag patch applied.')
