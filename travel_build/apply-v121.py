from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

old=".fulltext .words{text-align:center;font-weight:400;line-height:1.18;word-break:break-word;max-width:88vh;max-height:82vw;white-space:pre-wrap}"
new=".fulltext .words{text-align:center;font-weight:400;line-height:1.08;word-break:normal;overflow-wrap:anywhere;width:100%;max-width:none;max-height:none;white-space:pre-wrap}"
if old not in s:
    raise SystemExit('v1.0.21 patch failed: words css')
s=s.replace(old,new,1)

old="""function fitFullText(){const w=$('#fullWords'),txt=w.textContent;const logicalW=innerHeight,logicalH=innerWidth;let px=txt.length<20?74:txt.length<50?58:txt.length<100?44:34;w.style.fontSize=px+'px';requestAnimationFrame(()=>{while((w.scrollHeight>logicalH*.78||w.scrollWidth>logicalW*.88)&&px>22){px-=2;w.style.fontSize=px+'px'}})}"""
new="""function fitFullText(){const w=$('#fullWords'),sheet=w?.closest('.landscape-sheet');if(!w||!sheet)return;const cs=getComputedStyle(sheet),availW=Math.max(1,sheet.clientWidth-parseFloat(cs.paddingLeft)-parseFloat(cs.paddingRight)),availH=Math.max(1,sheet.clientHeight-parseFloat(cs.paddingTop)-parseFloat(cs.paddingBottom));w.style.width=availW+'px';w.style.maxWidth='none';w.style.maxHeight='none';let lo=12,hi=Math.max(140,Math.min(420,Math.floor(Math.max(availW,availH)*.62))),best=lo;const fits=px=>{w.style.fontSize=px+'px';return w.scrollWidth<=availW+1&&w.scrollHeight<=availH+1};while(lo<=hi){const mid=Math.floor((lo+hi)/2);if(fits(mid)){best=mid;lo=mid+1}else hi=mid-1}w.style.fontSize=best+'px'}"""
if old not in s:
    raise SystemExit('v1.0.21 patch failed: fitFullText')
s=s.replace(old,new,1)

# Refit after entering/leaving fullscreen as well as normal viewport resize.
old="addEventListener('resize',()=>{if($('#fullText').classList.contains('show'))fitFullText()});"
new="addEventListener('resize',()=>{if($('#fullText').classList.contains('show'))fitFullText()});document.addEventListener('fullscreenchange',()=>{if($('#fullText').classList.contains('show'))requestAnimationFrame(fitFullText)});"
if old not in s:
    raise SystemExit('v1.0.21 patch failed: resize handler')
s=s.replace(old,new,1)

p.write_text(s)

p=root/'app/build.gradle'
g=p.read_text()
g=re.sub(r"versionCode\s+\d+","versionCode 121",g,count=1)
g=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.21'",g,count=1)
p.write_text(g)
