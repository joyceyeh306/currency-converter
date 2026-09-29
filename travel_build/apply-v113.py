from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

def rep(old,new,label):
    global s
    if old not in s:
        raise SystemExit('v1.0.13 patch failed: '+label)
    s=s.replace(old,new,1)

# overflow:hidden created a non-scrolling ancestor and broke position:sticky.
# Use clip instead: still prevents horizontal movement, but does not become a scroll container.
rep(
'font-size:calc(16px * var(--fs));width:100%;max-width:100%;overflow-x:hidden}',
'font-size:calc(16px * var(--fs));width:100%;max-width:100%;overflow-x:clip;overscroll-behavior-x:none}',
'root clip'
)
rep(
'padding-bottom:64px;overflow-x:hidden}#screen,.content{width:100%;max-width:100%;min-width:0;overflow-x:hidden}',
'padding-bottom:64px;overflow-x:clip}#screen,.content{width:100%;max-width:100%;min-width:0;overflow-x:clip}',
'app clip'
)

# Explicitly keep vertical page scrolling on the document.
marker='button{cursor:pointer}\n'
if marker not in s:
    raise SystemExit('v1.0.13 patch failed: button marker')
s=s.replace(marker, marker+'body{touch-action:pan-y}\n',1)

p.write_text(s)

g=root/'app/build.gradle'
b=g.read_text()
b=re.sub(r"versionCode\s+\d+","versionCode 113",b,count=1)
b=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.13'",b,count=1)
g.write_text(b)
