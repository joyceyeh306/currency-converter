from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')

p=root/'app/src/main/AndroidManifest.xml'
s=p.read_text()
if 'com.google.mlkit.vision.DEPENDENCIES' not in s:
    s=s.replace('</application>','''    <meta-data
        android:name="com.google.mlkit.vision.DEPENDENCIES"
        android:value="ocr,ocr_korean" />
</application>''')
p.write_text(s)

p=root/'app/build.gradle'
s=p.read_text()
deps=[
"implementation 'com.google.android.gms:play-services-mlkit-text-recognition:19.0.1'",
"implementation 'com.google.android.gms:play-services-mlkit-text-recognition-korean:16.0.1'"
]
if 'dependencies {' not in s:
    s += "\n\ndependencies {\n    " + "\n    ".join(deps) + "\n}\n"
else:
    for dep in deps:
        if dep not in s:
            s=s.replace('dependencies {','dependencies {\n    '+dep,1)
s=re.sub(r"versionCode\s+\d+","versionCode 115",s,count=1)
s=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.15'",s,count=1)
p.write_text(s)
