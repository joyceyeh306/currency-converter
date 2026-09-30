from pathlib import Path

root=Path('/tmp/ajo/ajo_build_min')
p=root/'gradle.properties'
s=p.read_text() if p.exists() else ''
lines=[x for x in s.splitlines() if not x.startswith('android.useAndroidX=') and not x.startswith('android.enableJetifier=')]
lines += ['android.useAndroidX=true','android.enableJetifier=true']
p.write_text('\n'.join(lines)+'\n')
