from pathlib import Path

p = Path('app/src/main/java/com/joyce/currencyconverter/MainActivity.java')
s = p.read_text(encoding='utf-8')

def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'Patch target not found: {label}')
    s = s.replace(old, new, 1)

rep('import android.graphics.Color;\nimport android.graphics.Typeface;',
    'import android.graphics.Bitmap;\nimport android.graphics.BitmapFactory;\nimport android.graphics.Color;\nimport android.graphics.Typeface;\nimport android.graphics.drawable.BitmapDrawable;',
    'graphics imports')
rep('import android.text.Editable;\nimport android.text.TextWatcher;',
    'import android.text.Editable;\nimport android.text.Spannable;\nimport android.text.SpannableString;\nimport android.text.TextWatcher;\nimport android.text.style.ImageSpan;',
    'text imports')
rep('import android.widget.LinearLayout;\nimport android.widget.ScrollView;',
    'import android.widget.ImageView;\nimport android.widget.LinearLayout;\nimport android.widget.ScrollView;',
    'widget imports')

rep('    private String currentAmountText = "1000";\n',
    '    private String currentAmountText = "1000";\n    private Bitmap flagsSprite;\n    private final Map<String, Bitmap> flagCache = new LinkedHashMap<>();\n',
    'flag fields')

rep('        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);\n        configureSystemBars();',
    '        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);\n        flagsSprite = BitmapFactory.decodeResource(getResources(), R.drawable.flags_sprite);\n        configureSystemBars();',
    'decode sprite')

rep('            final String display = allFlags[i] + "  " + allNames[i] + "  " + code;\n\n            CheckBox cb = new CheckBox(this);\n            cb.setText(display);',
    '            final String display = allNames[i] + "  " + code;\n\n            CheckBox cb = new CheckBox(this);\n            cb.setText(flagText(code, display, 27, 18));',
    'settings flag text')

rep('                TextView v = (TextView) super.getView(position, convertView, parent);\n                v.setTextSize(14);\n                v.setTextColor(TEXT);\n                v.setGravity(Gravity.CENTER_VERTICAL);\n                v.setPadding(dp(8), 0, dp(8), 0);\n                return v;',
    '                TextView v = (TextView) super.getView(position, convertView, parent);\n                v.setTextSize(14);\n                v.setTextColor(TEXT);\n                v.setGravity(Gravity.CENTER_VERTICAL);\n                v.setPadding(dp(8), 0, dp(8), 0);\n                if (position >= 0 && position < spinnerCodes.size()) {\n                    String code = spinnerCodes.get(position);\n                    int i = indexOfCode(code);\n                    v.setText(flagText(code, allNames[i] + "  " + code, 28, 19));\n                }\n                return v;',
    'spinner selected flag')

rep('                TextView v = (TextView) super.getDropDownView(position, convertView, parent);\n                v.setTextSize(15);\n                v.setPadding(dp(12), dp(10), dp(12), dp(10));\n                return v;',
    '                TextView v = (TextView) super.getDropDownView(position, convertView, parent);\n                v.setTextSize(15);\n                v.setPadding(dp(12), dp(10), dp(12), dp(10));\n                if (position >= 0 && position < spinnerCodes.size()) {\n                    String code = spinnerCodes.get(position);\n                    int i = indexOfCode(code);\n                    v.setText(flagText(code, allNames[i] + "  " + code, 30, 20));\n                }\n                return v;',
    'spinner dropdown flag')

rep('        String name = allNames[index];\n        String flag = allFlags[index];',
    '        String name = allNames[index];',
    'remove emoji flag variable')

rep('        TextView flagView = new TextView(this);\n        flagView.setText(flag);\n        flagView.setTextSize(18);\n        top.addView(flagView);',
    '        ImageView flagView = new ImageView(this);\n        flagView.setImageBitmap(flagBitmap(code));\n        flagView.setScaleType(ImageView.ScaleType.FIT_XY);\n        top.addView(flagView, new LinearLayout.LayoutParams(dp(29), dp(19)));',
    'card image flag')

rep('        return allFlags[i] + "  " + allNames[i] + "  " + code;',
    '        return allNames[i] + "  " + code;',
    'plain display name')

marker = '    private int indexOfCode(String code) {'
helpers = '''    private Bitmap flagBitmap(String code) {\n        Bitmap cached = flagCache.get(code);\n        if (cached != null) return cached;\n        int i = indexOfCode(code);\n        if (i < 0 || flagsSprite == null) return null;\n        final int cellW = 96;\n        final int cellH = 64;\n        final int cols = 5;\n        int x = (i % cols) * cellW;\n        int y = (i / cols) * cellH;\n        Bitmap b = Bitmap.createBitmap(flagsSprite, x, y, cellW, cellH);\n        flagCache.put(code, b);\n        return b;\n    }\n\n    private CharSequence flagText(String code, String label, int widthDp, int heightDp) {\n        SpannableString ss = new SpannableString("\\uFFFC  " + label);\n        Bitmap b = flagBitmap(code);\n        if (b != null) {\n            BitmapDrawable d = new BitmapDrawable(getResources(), b);\n            d.setBounds(0, 0, dp(widthDp), dp(heightDp));\n            ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_BASELINE);\n            ss.setSpan(span, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);\n        }\n        return ss;\n    }\n\n'''
if marker not in s:
    raise SystemExit('Patch target not found: helper insertion')
s = s.replace(marker, helpers + marker, 1)

p.write_text(s, encoding='utf-8')
print('Built-in flag image patch applied.')
