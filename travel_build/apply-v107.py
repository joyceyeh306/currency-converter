from pathlib import Path
import re

p=Path('/tmp/ajo/ajo_build_min/app/build.gradle')
s=p.read_text()
s=re.sub(r"versionCode\s+\d+", "versionCode 107", s, count=1)
s=re.sub(r"versionName\s+['\"][^'\"]+['\"]", "versionName '1.0.7'", s, count=1)
p.write_text(s)
