from pathlib import Path
p=Path("app/src/main/java/tw/ajo/record/SystemSpeech008.java")
s=p.read_text()
def rep(old,new,n=1):
    global s
    assert s.count(old)==n,(p.name,old[:80],s.count(old))
    s=s.replace(old,new)
rep("    private int generation=0;", """    private int generation=0;
    private volatile boolean inputComplete=false;
    private long sessionStarted010=0;
    private boolean watchdogRunning010=false;
    private final Runnable watchdog010=new Runnable(){
        @Override public void run(){
            if(!active)return;
            long age=SystemClock.elapsedRealtime()-sessionStarted010;
            if(!inputComplete&&age>44000&&recognizer!=null){
                final int ticket=generation;
                callback.onStatus("系統辨識自動接續中（約 45 秒一段）");
                try{recognizer.stopListening();}catch(Exception ignored){}
                main.postDelayed(()->{
                    if(!active||inputComplete||ticket!=generation)return;
                    lines.retainPartialOnClose();callback.onText(lines.stable());
                    try{recognizer.cancel();}catch(Exception ignored){}
                    closeSession();nextSession();
                },2100);
                sessionStarted010=SystemClock.elapsedRealtime()+5000;
            }
            main.postDelayed(this,3000);
        }
    };""")
rep("            nextSession();\n        }catch(Exception e)", """            nextSession();
            if(!watchdogRunning010){watchdogRunning010=true;main.postDelayed(watchdog010,3000);}
        }catch(Exception e)""")
rep("            final int ticket=++generation;\n            final ArrayBlockingQueue<byte[]> q", """            final int ticket=++generation;
            sessionStarted010=SystemClock.elapsedRealtime();
            final ArrayBlockingQueue<byte[]> q""")
rep("""                private void restartOnce(){if(finished)return;finished=true;main.postDelayed(()->{
                    if(!active||ticket!=generation)return;
                    try{recognizer.cancel();}catch(Exception ignored){}
                    closeSession();main.postDelayed(()->{if(active)nextSession();},250);
                },100);}""", """                private void restartOnce(){if(finished)return;finished=true;main.postDelayed(()->{
                    if(!active||ticket!=generation)return;
                    lines.retainPartialOnClose();callback.onText(lines.stable());
                    try{recognizer.cancel();}catch(Exception ignored){}
                    closeSession();
                    if(!inputComplete)main.postDelayed(()->{if(active)nextSession();},250);
                },100);}""")
rep("""                @Override public void onError(int error){
                    if(!active||ticket!=generation)return;
                    // A recognizer can time out on an ordinary conversational pause.""", """                @Override public void onError(int error){
                    if(!active||ticket!=generation)return;
                    if(inputComplete){lines.retainPartialOnClose();callback.onText(lines.stable());closeSession();return;}
                    // A recognizer can time out on an ordinary conversational pause.""")
rep("    public void stop(){main.post(()->{\n        active=false;", """    /** Signal EOF for an imported PCM stream. */
    public void finishInput(){main.post(()->{
        inputComplete=true;
        ArrayBlockingQueue<byte[]> q=queue;
        if(q!=null && !q.offer(END))
            new Thread(()->{try{q.put(END);}catch(Exception ignored){}},"ajo-end-pcm").start();
    });}
    public String snapshot(){return lines.text();}
    public boolean isActive(){return active&&!failed;}
    public void stop(){main.post(()->{
        active=false;""")
p.write_text(s)

p=Path("app/src/main/java/tw/ajo/record/MainActivity.java")
s=p.read_text()
def rep(old,new,n=1):
    global s
    assert s.count(old)==n,(p.name,old[:100],s.count(old))
    s=s.replace(old,new)
rep("    private boolean cloudSelected=false;", """    private boolean cloudSelected=false;
    private boolean androidFile010=false;""")
rep('if(file)statusView.setText(cloudSelected?"選擇音檔，使用 OpenAI 付費雲端辨識":"選擇音訊或影片，使用離線 Whisper 辨識");',
    'if(file)statusView.setText(cloudSelected?"選擇音檔，使用 OpenAI 付費雲端辨識":androidFile010?"選擇音檔，測試 Android 系統辨識（可能使用網路）":"選擇音訊或影片，使用離線 Whisper 辨識");')
rep("""            setProgressVisible(true);importProgress.setIndeterminate(true);progressText.setText("準備模型…");
            statusView.setText("等待 Whisper 模型…");
            cancelled.set(false);
            if(cloudSelected)startCloudImport007(uri);
            else withModel(()->{if(!cancelled.get())startImport(uri);else endImport("已取消辨識",false);});""", """            setProgressVisible(true);importProgress.setIndeterminate(true);progressText.setText("準備匯入…");
            statusView.setText("準備音訊…");
            cancelled.set(false);
            if(cloudSelected)startCloudImport007(uri);
            else if(androidFile010)new AlertDialog.Builder(this)
                .setTitle("測試 Android 系統音檔辨識")
                .setMessage("將音訊交給手機的系統語音服務，可能使用網路。部分裝置不支援外部音訊，甚至可能改用麥克風，辨識結果未必來自這份音檔。是否開始？")
                .setNegativeButton("取消",(d,w)->{setProgressVisible(false);statusView.setText("已取消匯入辨識");})
                .setPositiveButton("開始測試",(d,w)->startSystemImport010(uri)).show();
            else withModel(()->{if(!cancelled.get())startImport(uri);else endImport("已取消辨識",false);});""")
start=s.index("    private void chooseEngine007(){")
end=s.index("    private void editCloudKey007(",start)
s=s[:start]+"""    private void chooseEngine007(){
        if(busy||modelDownloading){toast("請先完成目前辨識");return;}
        String[] choices={"免費：Whisper Small（離線）", "Android 系統辨識（匯入音檔實驗功能）",
                "付費：OpenAI 雲端辨識（附時間標記）", "設定／更換雲端 API 金鑰", "刪除已儲存的 API 金鑰"};
        new AlertDialog.Builder(this).setTitle("匯入音檔的辨識方式")
          .setItems(choices,(dialog,which)->{
            if(which==0){cloudSelected=false;androidFile010=false;updateEngine007();}
            else if(which==1){cloudSelected=false;androidFile010=true;updateEngine007();
                toast("系統辨識匯入音檔需裝置支援，可能使用網路");}
            else if(which==2){cloudSelected=true;androidFile010=false;updateEngine007();if(!CloudKeyStore007.isSaved(this))editCloudKey007(null);}
            else if(which==3)editCloudKey007(null);
            else if(which==4){new AlertDialog.Builder(this).setTitle("刪除雲端金鑰？")
               .setMessage("只刪除此 App 儲存的 API 金鑰，不影響已儲存錄音。")
               .setNegativeButton("取消",null).setPositiveButton("刪除",(d,w)->{CloudKeyStore007.remove(this);toast("雲端金鑰已刪除");}).show();}
          }).setNegativeButton("取消",null).show();
    }
    private void updateEngine007(){
        if(engineButton!=null)engineButton.setText(cloudSelected?"付費 ▾":androidFile010?"Android ▾":"離線 ▾");
        if(importMode)statusView.setText(cloudSelected?"選擇音檔，使用 OpenAI 雲端辨識":androidFile010?"選擇音檔，使用 Android 系統辨識（實驗）":"選擇音檔，使用 Whisper 離線辨識");
        refreshCloudAgain007();
    }
    private void refreshCloudAgain007(){
        if(cloudAgainButton!=null)cloudAgainButton.setVisibility(importMode&&cloudSelected&&currentWav!=null&&currentWav.isFile()?View.VISIBLE:View.GONE);
    }
""" + s[end:]
marker="    private void startImport(Uri uri){"
assert s.count(marker)==1
s=s.replace(marker,"""    /** 010: Android system recognizer for imported audio; does not require Whisper. */
    private void startSystemImport010(Uri uri){
        if(busy)return;
        busy=true;cancelled.set(false);lastProgress=0;importStartMs=SystemClock.elapsedRealtime();
        importedDurationMs=0;timeView.setText("00:00:00");
        importProgress.setIndeterminate(true);progressText.setText("音訊解碼中");
        statusView.setText("Android 系統辨識實驗模式 · 可能使用網路");
        importButton.setText("取消辨識");
        jobs.submit(()->{
            File pcm=null;boolean success=false;String status="辨識已取消";
            try{
                pcm=File.createTempFile("ajo_system_import_",".pcm",getCacheDir());
                File decoded=pcm;
                PcmDecoder.decode(this,uri,decoded,cancelled,ms->{
                    importedDurationMs=ms;
                    ui.post(()->{if(!destroyed&&importMode)timeView.setText(clock(ms));});
                });
                if(cancelled.get())throw new InterruptedException("已取消辨識");
                importedDurationMs=pcm.length()/32;
                ui.post(()->{if(!destroyed){
                    importProgress.setIndeterminate(false);importProgress.setProgress(0);
                    progressText.setText("系統辨識 0%");
                    statusView.setText("正在把音檔送給 Android 辨識（實驗）…");
                }});
                String output=SystemFileTranscriber010.transcribe(this,pcm,cancelled,(percent,message)->{
                    if(percent>=0){int value=Math.min(99,Math.max(0,percent));lastProgress=value;
                        ui.post(()->{if(!destroyed){importProgress.setProgress(value);progressText.setText("系統辨識 "+value+"%");}});
                    }else{ui.post(()->{if(!destroyed&&busy)statusView.setText(message);});}
                });
                if(cancelled.get())throw new InterruptedException("已取消辨識");
                File dest=RecordingStore.newImportedWav(this);
                AudioFiles.makeWav(pcm,dest);
                RecordingStore.saveTranscript(dest,output);
                AudioFiles.saveText(new File(dest.getParentFile(),dest.getName().replace(".wav",".system.txt")),output);
                final File ready=dest;final String result=output;
                ui.post(()->{if(!destroyed){
                    player.release();currentWav=ready;preparePlayback(ready);refreshCloudAgain007();
                    displayTranscript(result);manualOverride=true;
                    statusView.setText("系統辨識完成 · 可播放／匯出 WAV，尚無逐句時間標記");
                }});
                success=true;status="Android 系統辨識完成 · 沒有逐句時間標記";
            }catch(Exception e){status=cancelled.get()?"辨識已取消":"系統辨識無法處理匯入檔案："+e.getMessage();}
            finally{if(pcm!=null)pcm.delete();boolean ok=success;String end=status;
                ui.post(()->{if(!destroyed){endImport(end,ok);if(!ok&&!cancelled.get())showError(end+"。可以改選 Whisper 離線辨識。");}});
            }
        });
    }
""" + marker)
p.write_text(s)
p=Path("app/src/main/java/tw/ajo/record/SystemFileTranscriber010.java")
p.write_bytes(Path("../patches/ajo-record-010/SystemFileTranscriber010.java").read_bytes())
p=Path("app/build.gradle")
s=p.read_text()
assert s.count("versionCode 9")==1 and s.count("versionName '009'")==1
p.write_text(s.replace("versionCode 9","versionCode 10").replace("versionName '009'","versionName '010'"))
print("Record 010 patch applied")
