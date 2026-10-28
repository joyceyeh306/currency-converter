from pathlib import Path
p=Path('app/src/main/java/tw/ajo/record')
r=p/'RecordingService.java'
s=r.read_text()
old_doc='''/** Microphone capture in a foreground service; local, offline Whisper pass every ~12 seconds.
 * The live text is provisional, and the full recording is rechecked after Stop.
 */'''
new_doc='''/** Foreground microphone capture with pause-aware provisional offline Whisper.
 * The live text remains provisional; a full-recording beam-5 pass is kept intact.
 */'''
assert old_doc in s
s=s.replace(old_doc,new_doc)
a=s.index('    private void liveRecognizeLoop(){')
b=s.index('            if(captureFinished!=null)captureFinished.await(20,TimeUnit.SECONDS);',a)
replacement='''    private void liveRecognizeLoop(){
        long cursor=0;
        try{
            // A 6–16 second variable window allows a natural pause to end the phrase.
            // When stopped, skip provisional backlog and immediately process the full file.
            while(recording){
                long available=saved.get();
                long boundary=SpeechChunker003.nextEnd(lastPcm,cursor,available,false);
                if(boundary<=cursor){Thread.sleep(300);continue;}
                File portion=AudioFiles.clip(lastPcm,cursor,boundary-cursor,getCacheDir());
                try{
                    if(!SpeechChunker003.definitelySilent(portion)){
                        status="錄音中 · 依停頓分段辨識";
                        WhisperNative.Result result=WhisperNative.run(model,portion,"zh",4,cancel,p->{});
                        String text=TranscriptText.traditional(AudioFiles.texts(result.captions));
                        if(!text.isBlank())transcript=transcript.isBlank()?text:transcript+"\\n"+text;
                    }
                    if(recording)status="錄音中 · 已顯示暫存文字";
                }finally{portion.delete();}
                cursor=boundary;
            }
'''
s=s[:a]+replacement+s[b:]
r.write_text(s)
f=p/'AudioFiles.java'
s=f.read_text()
old='''        try(FileInputStream in=new FileInputStream(source);FileOutputStream fo=new FileOutputStream(out)){
            in.skipNBytes(start);byte[] b=new byte[32768];long remaining=count;
'''
new='''        try(RandomAccessFile in=new RandomAccessFile(source,"r");FileOutputStream fo=new FileOutputStream(out)){
            if(start<0||count<=0||start%2!=0||count%2!=0||start>in.length()||count>in.length()-start)
                throw new EOFException("錄音區間不足或未對齊 PCM 樣本");
            in.seek(start);byte[] b=new byte[32768];long remaining=count;
'''
assert s.count(old)==1
f.write_text(s.replace(old,new))
(p/'SpeechChunker003.java').write_text(Path('patches/ajo-record-003/SpeechChunker003.java').read_text())
f=Path('app/build.gradle')
s=f.read_text()
assert 'versionCode 2' in s and "versionName '002'" in s
f.write_text(s.replace('versionCode 2','versionCode 3').replace("versionName '002'","versionName '003'"))
assert 'CHUNK=12L' not in r.read_text()
print('record003 upgrade applied')
