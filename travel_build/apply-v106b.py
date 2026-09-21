from pathlib import Path

p=Path('/tmp/ajo/ajo_build_min/app/src/main/assets/index.html')
s=p.read_text()
old="for(const p of t.passes){p.type=p.type||'pass';p.validFrom=p.validFrom||'';p.validUntil=p.validUntil||''}"
new="""for(const p of t.passes){
      p.type=p.type||'pass';
      if(!p.validFrom&&p.start&&p.startTime)p.validFrom=p.start+'T'+p.startTime;
      if(!p.validUntil&&p.end&&p.endTime)p.validUntil=p.end+'T'+p.endTime;
      if(p.type==='pass'&&p.start&&p.end&&p.endTime){
        const m=String(p.name||'').match(/(\\d+)\\s*(?:日|天|days?)/i);
        if(m){
          const n=Number(m[1]),a=p.start.split('-').map(Number),b=p.end.split('-').map(Number);
          const total=Math.round((Date.UTC(b[0],b[1]-1,b[2])-Date.UTC(a[0],a[1]-1,a[2]))/86400000)+1;
          if(n>0&&total===n+1){
            const d=new Date(Date.UTC(a[0],a[1]-1,a[2]+n-1));
            p.end=d.toISOString().slice(0,10);
          }
        }
      }
      p.validFrom=p.validFrom||'';p.validUntil=p.validUntil||'';
    }"""
if old not in s:
    raise SystemExit('v1.0.6b patch failed: migrate pass marker')
s=s.replace(old,new,1)
p.write_text(s)
