from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

def rep(old,new,label):
    global s
    if old not in s:
        raise SystemExit('v1.0.14 patch failed: '+label)
    s=s.replace(old,new,1)

old="""function localDateISO(){const d=new Date(),y=d.getFullYear(),m=String(d.getMonth()+1).padStart(2,'0'),da=String(d.getDate()).padStart(2,'0');return `${y}-${m}-${da}`}"""
new="""function localDateISO(){const d=new Date(),y=d.getFullYear(),m=String(d.getMonth()+1).padStart(2,'0'),da=String(d.getDate()).padStart(2,'0');return `${y}-${m}-${da}`}
function applyStartupRoute(){const today=localDateISO(),active=(data.trips||[]).filter(t=>t.start&&t.end&&t.start<=today&&t.end>=today);if(!active.length)return;let t=active.find(x=>x.id===data.currentTripId)||active[0];const d=(t.days||[]).find(x=>x.date===today);data.currentTripId=t.id;route={tab:'trips',tripId:t.id,dayId:d?d.id:null};saveData()}"""
rep(old,new,"startup route function")

rep("render();setTimeout(syncNativeReminders,600);","applyStartupRoute();render();setTimeout(syncNativeReminders,600);","apply startup route")

p.write_text(s)

g=root/'app/build.gradle'
b=g.read_text()
b=re.sub(r"versionCode\s+\d+","versionCode 114",b,count=1)
b=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.14'",b,count=1)
g.write_text(b)
