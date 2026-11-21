package tw.ajo.phototext;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.util.Size;
import android.util.LruCache;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 final int BLUE=0xff086f72,BG=0xfffffcf6,INK=0xff203836,MUTED=0xff526b66,PALE=0xffe5f3ee,LINE=0xffdce8e3;
 static final int PICK=20,PICK_DOCUMENT=21,PERMISSION=22,GEO_PERMISSION=23;
 static final String[] SCOPES={"全部相簿","相機照片","螢幕截圖"};
 static final int[] LIMITS={1000,3000,5000,10000,Integer.MAX_VALUE};
 final ExecutorService worker=Executors.newSingleThreadExecutor(),reader=Executors.newSingleThreadExecutor(),geoWorker=Executors.newSingleThreadExecutor();
 final ThreadPoolExecutor thumbs=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(32),new ThreadPoolExecutor.DiscardOldestPolicy());
 final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 final Handler main=new Handler(Looper.getMainLooper());
 IndexStore store; GeoStore geoStore; GeoStore.Stats geoStats=new GeoStore.Stats(); boolean placeMode=false,geoBusy=false; volatile boolean geoCancel=false; int geoRadius=100,geoDone=0,geoTotal=0; String geoError=""; Button modeText,modePlace; TextView status,count,device; EditText search; Spinner script,scope,limit; Button start,pick,pause,stop,clear,more;
 ProgressBar progress,homeProgress; GridView list; Rows adapter; LinearLayout settingsPanel,homeEmpty,historyBox; TextView homeStatus,stats,emptyTitle; Button homeUpdate,settings,clearQuery; boolean browsing=false; int indexed=0; boolean anyTerms=false; boolean fuzzyEnabled=true; SearchSpec filters=SearchSpec.defaults("",false); Button filterButton,andButton,orButton; LinearLayout termBar; TextView filterSummary; boolean metadataDirty=true; Switch autoSwitch; boolean autoRequested=false,autoRun=false,observerRegistered=false; TextView autoInfo; ArrayList<String[]> albumChoices=new ArrayList<>(); volatile boolean cancel=false,discard=false,active=false; boolean ready=false,busy=false,planning=false,readBusy=false,readAgain=false,resetScroll=false;
 IndexStore.Job job; int pageSize=100,matches=0; volatile int requestSerial=0; String problem="",scanAccess=""; long lastRefresh=0;
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 GradientDrawable bg(int color,int rad){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(rad));return d;}
 Button button(String s){Button b=new Button(this){@Override public boolean performClick(){if(isEnabled())performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);return super.performClick();}};b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);b.setTextColor(BLUE);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22086f72),bg(PALE,18),null));return b;}
 void primary(Button b){b.setTextColor(Color.WHITE);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),bg(BLUE,18),null));}
 void hideKeyboard(){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();}
 void section(LinearLayout l,String s){TextView t=text(s,16,BLUE);t.setTypeface(null,Typeface.BOLD);l.addView(t);space(l,8);}
 void settingsDialog(){hideKeyboard();if(settingsPanel.getParent()!=null)((ViewGroup)settingsPanel.getParent()).removeView(settingsPanel);ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.addView(settingsPanel);AlertDialog dlg=new AlertDialog.Builder(this).setTitle("設定・索引管理").setView(sc).setPositiveButton("完成",null).create();dlg.show();dlg.getButton(-1).setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);dlg.dismiss();});settingsPanel.setTag(dlg);}
 void rememberQuery(){String q=search.getText().toString().trim();if(q.isEmpty())return;ArrayList<String> h=history();h.remove(q);h.add(0,q);while(h.size()>6)h.remove(h.size()-1);getPreferences(MODE_PRIVATE).edit().putString("recent",new org.json.JSONArray(h).toString()).apply();renderHistory();}
 ArrayList<String> history(){ArrayList<String> h=new ArrayList<>();try{org.json.JSONArray a=new org.json.JSONArray(getPreferences(MODE_PRIVATE).getString("recent","[]"));for(int i=0;i<a.length();i++)h.add(a.getString(i));}catch(Exception ignored){}return h;}
 void renderHistory(){if(historyBox==null)return;historyBox.removeAllViews();ArrayList<String> h=history();if(h.isEmpty())return;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView t=text("最近搜尋",14,MUTED);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));Button clear=button("清除");row.addView(clear,new LinearLayout.LayoutParams(dp(62),dp(42)));clear.setOnClickListener(v->{getPreferences(MODE_PRIVATE).edit().remove("recent").apply();renderHistory();});historyBox.addView(row);for(int i=0;i<h.size();i+=2){LinearLayout r=new LinearLayout(this);for(int k=i;k<Math.min(i+2,h.size());k++){String q=h.get(k);Button chip=button(q);chip.setSingleLine(true);chip.setEllipsize(TextUtils.TruncateAt.END);addButton(r,chip);chip.setOnClickListener(v->{search.setText(q);search.setSelection(q.length());rememberQuery();hideKeyboard();});}if(i+1==h.size())r.addView(new View(this),new LinearLayout.LayoutParams(0,dp(46),1));historyBox.addView(r);space(historyBox,6);}}
 void updateHome(){if(placeMode){updatePlaceHome();return;}boolean q=!IndexStore.norm(search.getText().toString()).isEmpty();boolean show=q||browsing||filters.filtered();boolean has=show&&adapter.getCount()>0;list.setVisibility(has?View.VISIBLE:View.GONE);homeEmpty.setVisibility(has?View.GONE:View.VISIBLE);historyBox.setVisibility(show?View.GONE:View.VISIBLE);emptyTitle.setText(show?(readBusy?"搜尋中…":"沒有符合的照片"):"搜尋照片中的文字");count.setVisibility(show?View.VISIBLE:View.GONE);more.setVisibility(has&&matches>adapter.getCount()?View.VISIBLE:View.GONE);clearQuery.setVisibility(q||browsing?View.VISIBLE:View.INVISIBLE);termBar.setVisibility(FuzzyText.terms(search.getText().toString(),MatchText::norm,fuzzyEnabled).size()>1?View.VISIBLE:View.GONE);filterSummary.setVisibility(filters.changed()||!fuzzyEnabled?View.VISIBLE:View.GONE);filterSummary.setText(filterDescription()+(!fuzzyEnabled?(filters.changed()?"・":"")+"精確搜尋":""));filterButton.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22086f72),bg(filters.changed()?0xffbfded2:PALE,18),null));homeUpdate.setEnabled(ready&&!busy);homeUpdate.setText(job!=null&&job.pending()>0?"繼續掃描":"更新索引");homeProgress.setVisibility(busy?View.VISIBLE:View.GONE);if(job!=null){homeProgress.setMax(Math.max(1,job.total));homeProgress.setProgress(job.done);}homeStatus.setText(!problem.isEmpty()?problem:!ready?"正在準備索引…":busy?(planning?"正在準備掃描…":"辨識中 "+(job==null?0:job.done)+" / "+(job==null?0:job.total)):job!=null&&job.pending()>0?"已暫停・剩餘 "+job.pending()+" 張":"已建立 "+String.format(Locale.TAIWAN,"%,d",indexed)+" 筆索引");}

 void updateTermButtons(){if(anyTerms){primary(orButton);andButton.setTextColor(BLUE);andButton.setBackground(bg(PALE,18));}else{primary(andButton);orButton.setTextColor(BLUE);orButton.setBackground(bg(PALE,18));}}
 void newSearch(){pageSize=100;requestSerial++;resetScroll=true;adapter.items.clear();adapter.notifyDataSetChanged();matches=0;updateHome();requestPage();}
 String albumName(String id){if(id.equals("camera"))return "相機照片";if(id.equals("screens"))return "螢幕截圖";if(id.startsWith("album:")){for(String[] a:albumChoices)if(id.substring(6).equals(a[0]))return a[1];return "指定相簿";}return "全部相簿";}
 String dateText(long ms){return new java.text.SimpleDateFormat("yyyy/MM/dd",Locale.TAIWAN).format(new Date(ms));}
 long dayStart(long ms){Calendar c=Calendar.getInstance();c.setTimeInMillis(ms);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
 long shift(long ms,int unit,int amount){Calendar c=Calendar.getInstance();c.setTimeInMillis(ms);c.add(unit,amount);return c.getTimeInMillis();}
 String filterDescription(){ArrayList<String> a=new ArrayList<>();if(!filters.album.isEmpty())a.add(albumName(filters.album));if(filters.dateMode==1)a.add("最近一個月");else if(filters.dateMode==2)a.add("最近一年");else if(filters.dateMode==3)a.add(dateText(filters.from)+"－"+dateText(shift(filters.to,Calendar.DATE,-1)));if(filters.older)a.add("最舊優先");return String.join("・",a);}
 void datePicker(Button b,long[] dates,int n){Calendar c=Calendar.getInstance();c.setTimeInMillis(dates[n]);new DatePickerDialog(this,(view,y,m,d)->{Calendar chosen=Calendar.getInstance();chosen.clear();chosen.set(y,m,d);dates[n]=chosen.getTimeInMillis();b.setText(dateText(dates[n]));},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
 void filterDialog(){hideKeyboard();LinearLayout l=vertical();l.setPadding(dp(20),dp(12),dp(20),dp(12));Switch fuzzyToggle=new Switch(this);fuzzyToggle.setText("模糊搜尋與數字容錯");fuzzyToggle.setTextColor(INK);fuzzyToggle.setChecked(fuzzyEnabled);l.addView(fuzzyToggle,new LinearLayout.LayoutParams(-1,dp(48)));Button fuzzyInfo=button("ⓘ 模糊搜尋說明");l.addView(fuzzyInfo,new LinearLayout.LayoutParams(-1,dp(44)));fuzzyInfo.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("模糊搜尋").setMessage("精確符合優先。單字維持精確；常見相似字允許一處差異，四字以上純中文或英文字允許一處增漏或錯字。四位以上數字可忽略日期及電話分隔符號，並容許一個常見字母誤辨，不任意替換數字。近似文字以橘底標示。關閉可回到原本精確搜尋。").setPositiveButton("關閉",null).show());space(l,12);section(l,"相簿");
  ArrayList<String> keys=new ArrayList<>(Arrays.asList("","camera","screens"));ArrayList<String> names=new ArrayList<>(Arrays.asList("全部相簿","相機照片","螢幕截圖"));for(String[] a:albumChoices){keys.add("album:"+a[0]);names.add(a[1]);}
  if(!keys.contains(filters.album)){keys.add(filters.album);names.add("指定相簿（目前無法讀取）");}
  Spinner albums=spinner(names.toArray(new String[0]));albums.setSelection(Math.max(0,keys.indexOf(filters.album)));l.addView(albums,new LinearLayout.LayoutParams(-1,dp(48)));space(l,12);
  section(l,"日期");Spinner date=spinner(new String[]{"不限日期","最近一個月","最近一年","自訂日期區間"});date.setSelection(filters.dateMode);l.addView(date,new LinearLayout.LayoutParams(-1,dp(48)));
  long today=dayStart(System.currentTimeMillis());long[] days={filters.dateMode==3?filters.from:shift(today,Calendar.MONTH,-1),filters.dateMode==3?shift(filters.to,Calendar.DATE,-1):today};
  LinearLayout dateRow=new LinearLayout(this);Button from=button(dateText(days[0])),to=button(dateText(days[1]));addButton(dateRow,from);addButton(dateRow,to);l.addView(dateRow);from.setOnClickListener(v->datePicker(from,days,0));to.setOnClickListener(v->datePicker(to,days,1));
  dateRow.setVisibility(filters.dateMode==3?View.VISIBLE:View.GONE);date.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int i,long id){dateRow.setVisibility(i==3?View.VISIBLE:View.GONE);}public void onNothingSelected(AdapterView<?> p){}});
  space(l,12);section(l,"排列順序");Spinner sort=spinner(new String[]{"最新照片優先","最舊照片優先"});sort.setSelection(filters.older?1:0);l.addView(sort,new LinearLayout.LayoutParams(-1,dp(48)));
  Button info=button("ⓘ 日期說明");l.addView(info,new LinearLayout.LayoutParams(-1,dp(44)));info.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("日期說明").setMessage("優先使用照片拍攝日期；沒有拍攝日期時使用檔案修改日期或加入相簿日期。無法取得日期的照片不列入日期篩選，排序時放在最後。手動選取的照片可能無法取得相簿名稱。").setPositiveButton("關閉",null).show());
  ScrollView sc=new ScrollView(this);sc.addView(l);AlertDialog dlg=new AlertDialog.Builder(this).setTitle("搜尋篩選").setView(sc).setNegativeButton("取消",null).setNeutralButton("重設",null).setPositiveButton("套用",null).create();dlg.show();dlg.getButton(-3).setOnClickListener(v->{filters=SearchSpec.defaults("",anyTerms);fuzzyEnabled=true;getPreferences(MODE_PRIVATE).edit().putBoolean("fuzzy012",true).apply();dlg.dismiss();newSearch();});dlg.getButton(-1).setOnClickListener(v->{int mode=date.getSelectedItemPosition();long f=0,t=0;if(mode==1){f=shift(today,Calendar.MONTH,-1);t=shift(today,Calendar.DATE,1);}else if(mode==2){f=shift(today,Calendar.YEAR,-1);t=shift(today,Calendar.DATE,1);}else if(mode==3){if(days[0]>days[1]){Toast.makeText(this,"起始日期不可晚於結束日期",Toast.LENGTH_SHORT).show();return;}f=days[0];t=shift(days[1],Calendar.DATE,1);}filters=new SearchSpec("",anyTerms,keys.get(albums.getSelectedItemPosition()),f,t,sort.getSelectedItemPosition()==1,mode);fuzzyEnabled=fuzzyToggle.isChecked();getPreferences(MODE_PRIVATE).edit().putBoolean("fuzzy012",fuzzyEnabled).apply();dlg.dismiss();newSearch();});
 }
 boolean autoEnabled(){return getPreferences(MODE_PRIVATE).getBoolean("auto009",true);}
 void setAutoEnabled(boolean enabled){getPreferences(MODE_PRIVATE).edit().putBoolean("auto009",enabled).apply();if(autoSwitch!=null&&autoSwitch.isChecked()!=enabled)autoSwitch.setChecked(enabled);if(!enabled){autoRequested=false;main.removeCallbacks(autoTask);if(autoRun&&busy)cancel=true;}else scheduleAuto();updateAutoInfo();}
 void updateAutoInfo(){if(autoInfo==null)return;autoInfo.setText(!autoEnabled()?"自動同步已關閉":!fullAccess()?"需要完整照片權限":autoRun&&busy?"正在自動同步":"開啟 App 時自動檢查");}
 final Runnable autoTask=()->tryAuto();
 final android.database.ContentObserver mediaObserver=new android.database.ContentObserver(main){@Override public void onChange(boolean self,Uri uri){scheduleAuto();scheduleGeoRefresh();}@Override public void onChange(boolean self){scheduleAuto();scheduleGeoRefresh();}};
 void scheduleAuto(){if(!autoEnabled())return;autoRequested=true;main.removeCallbacks(autoTask);if(active)main.postDelayed(autoTask,1200);}
 void registerMediaObserver(){if(observerRegistered)return;try{getContentResolver().registerContentObserver(Uri.parse("content://media"),true,mediaObserver);observerRegistered=true;}catch(Exception ignored){}}
 void unregisterMediaObserver(){if(observerRegistered){try{getContentResolver().unregisterContentObserver(mediaObserver);}catch(Exception ignored){}observerRegistered=false;}}
 void tryAuto(){
  if(!active||!ready||!autoEnabled()||!fullAccess()||!autoRequested||busy||readBusy)return;
  if(job!=null&&job.pending()>0&&!job.scope.startsWith("自動同步"))return;
  autoRequested=false;autoRun=true;busy=true;planning=true;cancel=false;discard=false;problem="";updateButtons();status.setText("正在檢查照片異動…");
  worker.execute(()->{
   try{
    IndexStore.Job persisted=store.job();
    if(persisted!=null&&persisted.pending()>0){
     if(persisted.scope.startsWith("自動同步")&&!cancel&&active){scanJob();}else finishRun("");
     return;
    }
    MediaSync.Snapshot snapshot=MediaSync.read(getApplicationContext(),()->cancel||!active);
    if(cancel||!active||!fullAccess()){finishAutoWithoutScan("");return;}
    if(snapshot.complete.isEmpty()){finishAutoWithoutScan("暫時無法讀取相簿，下次開啟會再檢查");return;}
    SyncPlan plan=SyncPlan.build(snapshot.items,store.syncKnown(),snapshot.complete);
    ArrayList<String> removed=new ArrayList<>();
    for(String u:plan.missing){if(cancel||!active)break;if(MediaSync.confirmedMissing(getApplicationContext(),u,snapshot.complete))removed.add(u);}
    if(cancel||!active||!fullAccess()){finishAutoWithoutScan("");return;}
    store.applySync(snapshot,removed);
    if(!removed.isEmpty()){for(String u:removed)cache.remove(u);}
    ArrayList<IndexStore.Photo> pending=new ArrayList<>();
    for(SyncPlan.Work w:plan.work){pending.add(new IndexStore.Photo(w.item.uri,w.item.name,w.item.stamp,w.mode));cache.remove(w.item.uri);}
    if(pending.isEmpty()){finishAutoWithoutScan("");return;}
    store.createJob(pending,plan.skipped,"自動同步（全部相簿）");scanJob();
   }catch(Exception e){finishAutoWithoutScan("自動同步中斷："+e.getMessage());}
  });
 }
 void finishAutoWithoutScan(String message){finishRunInternal(message,false);}
 void updateModeButtons(){
  if(modeText==null||modePlace==null)return;
  if(placeMode){primary(modePlace);modeText.setTextColor(BLUE);modeText.setBackground(bg(PALE,18));}else{primary(modeText);modePlace.setTextColor(BLUE);modePlace.setBackground(bg(PALE,18));}
 }
 void switchMode(boolean geo){
  if(placeMode==geo)return;placeMode=geo;search.setText("");browsing=false;adapter.terms.clear();search.setHint(geo?"搜尋國家或城市，例如：日本、仙台":"輸入照片中的文字");filterButton.setContentDescription(geo?"調整地點搜尋範圍":"文字搜尋篩選");updateModeButtons();
  pageSize=100;requestSerial++;resetScroll=true;adapter.items.clear();adapter.notifyDataSetChanged();matches=0;updateHome();requestPage();
  if(geo){if(!geoStore.permission(this))askGeoPermission();else scheduleGeoRefresh();}
 }
 void askGeoPermission(){
  if(geoStore.permission(this)){scheduleGeoRefresh();return;}
  new AlertDialog.Builder(this).setTitle("授權照片拍攝位置").setMessage("地點搜尋需要完整照片存取權限，並額外讀取照片內原本儲存的 GPS 座標。只讀取，不會修改照片，也不會追蹤手機目前的位置。").setNegativeButton("取消",null).setPositiveButton("授權",(d,w)->{
   ArrayList<String> p=new ArrayList<>();if(!fullAccess()){p.add(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE);if(Build.VERSION.SDK_INT>=34)p.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);}
   if(checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
   if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),GEO_PERMISSION);else scheduleGeoRefresh();
  }).show();
 }
 void placeRadiusDialog(){
  final int[] distances={30,50,100,200,300,500};
  String[] labels=new String[distances.length];int selected=2;for(int i=0;i<distances.length;i++){labels[i]="城市周邊 "+distances[i]+" 公里";if(distances[i]==geoRadius)selected=i;}
  final int[] choice={selected};new AlertDialog.Builder(this).setTitle("地點搜尋範圍").setSingleChoiceItems(labels,selected,(d,w)->choice[0]=w)
   .setMessage("搜尋「日本」等國家名稱時使用全國範圍，不受城市距離限制。")
   .setNegativeButton("取消",null).setPositiveButton("套用",(d,w)->{geoRadius=distances[choice[0]];getPreferences(MODE_PRIVATE).edit().putInt("geo_radius014",geoRadius).apply();newSearch();}).show();
 }
 void updatePlaceHome(){
  boolean q=!search.getText().toString().trim().isEmpty(),has=q&&adapter.getCount()>0;
  list.setVisibility(has?View.VISIBLE:View.GONE);homeEmpty.setVisibility(has?View.GONE:View.VISIBLE);historyBox.setVisibility(View.GONE);
  count.setVisibility(q?View.VISIBLE:View.GONE);clearQuery.setVisibility(q?View.VISIBLE:View.INVISIBLE);
  emptyTitle.setText(q?(geoBusy?"正在讀取照片定位…":geoStore.permission(this)?"沒有符合這個地點的 GPS 照片":"請先授權照片 GPS 存取"):"輸入中文國家或城市名稱");
  more.setVisibility(has&&matches>adapter.getCount()?View.VISIBLE:View.GONE);
  termBar.setVisibility(View.GONE);filterSummary.setVisibility(View.VISIBLE);
  filterSummary.setText("城市範圍 "+geoRadius+" 公里・國家搜尋不限距離");
  filterButton.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22086f72),bg(PALE,18),null));
  homeProgress.setVisibility(geoBusy?View.VISIBLE:View.GONE);if(geoBusy){homeProgress.setMax(Math.max(1,geoTotal));homeProgress.setProgress(geoDone);}
  homeUpdate.setText(geoBusy?"建立中":"更新地點");homeUpdate.setEnabled(!geoBusy);
  homeStatus.setText(geoBusy?"地點掃描 "+geoDone+" / "+geoTotal+" 張":!geoError.isEmpty()?geoError:!geoStore.permission(this)?"需完整照片與 GPS 權限":"有 GPS "+geoStats.gps+" 張｜無 GPS "+geoStats.missing+" 張"+(geoStats.error>0?"｜無法讀取 "+geoStats.error:""));
 }
 final Runnable geoTask=()->startGeoScan();
 void scheduleGeoRefresh(){if(!placeMode||!active||!ready||geoBusy||!geoStore.permission(this))return;main.removeCallbacks(geoTask);main.postDelayed(geoTask,1300);}
 void startGeoScan(){
  if(geoBusy||!active||!ready)return;
  if(!geoStore.permission(this)){geoError="需要完整相簿及 GPS 權限";updateHome();return;}
  geoBusy=true;geoCancel=false;geoError="";geoDone=0;geoTotal=Math.max(1,geoStats.total);updateHome();
  geoWorker.execute(()->{
   String error="";GeoStore.Stats stats=null;
   try{stats=geoStore.scan(getApplicationContext(),()->geoCancel||!active,(done,total,changed)->main.post(()->{if(!isDestroyed()){geoDone=done;geoTotal=total;updateHome();}}));}
   catch(Exception ex){error="地點掃描失敗："+ex.getMessage();}
   final String message=error;final GeoStore.Stats latest=stats;
   main.post(()->{if(isDestroyed())return;geoBusy=false;geoError=message;if(latest!=null)geoStats=latest;else geoStats=geoStore.stats();updateHome();if(placeMode)requestPage();});
  });
 }
 void requestGeoPage(){
  if(readBusy){readAgain=true;return;}readBusy=true;
  final int serial=requestSerial,radius=geoRadius,limit=pageSize;final String q=search.getText().toString().trim();
  reader.execute(()->{
   GeoStore.Page result=null;GeoPlaces.Area area=null;String message="";
   try{if(!q.isEmpty()){area=GeoPlaces.resolve(q,radius,getApplicationContext());if(area==null)message="無法辨識地名，請試其他中文或英文城市名稱";else result=geoStore.find(area,limit,()->serial!=requestSerial||Thread.currentThread().isInterrupted());}}
   catch(Exception ex){message="地點搜尋失敗："+ex.getMessage();}
   final GeoStore.Page p=result;final GeoPlaces.Area target=area;final String err=message;
   main.post(()->{
    if(isDestroyed())return;readBusy=false;
    if(serial==requestSerial&&placeMode){ArrayList<IndexStore.Result> found=new ArrayList<>();
     if(p!=null){geoStats=p.stats;for(GeoStore.Record row:p.rows){
      String note=target.country?target.title+"境內":"距離"+target.title+"約 "+Math.round(GeoMath.km(target.lat,target.lon,row.lat,row.lon))+" 公里";
      if(row.date>0)note+="・"+dateText(row.date);
      found.add(new IndexStore.Result(row.uri,row.name,note));
     }matches=p.matches;}else matches=0;
     adapter.items=found;adapter.terms.clear();adapter.fuzzy=false;adapter.notifyDataSetChanged();if(resetScroll){list.setSelection(0);resetScroll=false;}
     count.setText(p==null?(err.isEmpty()?"":err):"地點符合 "+p.matches+" 張（有 GPS）");updateHome();
    }else readAgain=true;
    if(readAgain){readAgain=false;requestPage();}
   });
  });
 }
 void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);return s;}
 void addButton(LinearLayout r,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),dp(2),dp(3),dp(2));r.addView(b,p);}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);fuzzyEnabled=getPreferences(MODE_PRIVATE).getBoolean("fuzzy012",true);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  store=new IndexStore(getApplicationContext());geoStore=new GeoStore(getApplicationContext());geoStats=geoStore.stats();geoRadius=getPreferences(MODE_PRIVATE).getInt("geo_radius014",100);
  LinearLayout root=vertical();root.setBackgroundColor(BG);root.setPadding(dp(16),dp(14),dp(16),dp(8));root.setFocusableInTouchMode(true);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("ㄚ喬相片搜尋",25,BLUE);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));settings=button("⚙");settings.setTextSize(24);settings.setContentDescription("設定");header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);space(root,10);LinearLayout modeRow=new LinearLayout(this);modeText=button("文字搜尋");modePlace=button("地點搜尋");addButton(modeRow,modeText);addButton(modeRow,modePlace);root.addView(modeRow);updateModeButtons();modeText.setOnClickListener(v->switchMode(false));modePlace.setOnClickListener(v->switchMode(true));space(root,8);
  LinearLayout field=new LinearLayout(this);field.setGravity(Gravity.CENTER_VERTICAL);GradientDrawable fieldBg=bg(Color.WHITE,22);fieldBg.setStroke(dp(1),LINE);field.setBackground(fieldBg);field.setPadding(dp(12),0,dp(6),0);TextView glass=text("⌕",28,BLUE);glass.setGravity(Gravity.CENTER);field.addView(glass,new LinearLayout.LayoutParams(dp(36),dp(54)));search=new EditText(this);search.setSingleLine(true);search.setTextSize(18);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("輸入照片中的文字");search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(4),0,0,0);field.addView(search,new LinearLayout.LayoutParams(0,dp(54),1));clearQuery=button("×");clearQuery.setTextSize(22);clearQuery.setContentDescription("清除搜尋");field.addView(clearQuery,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);searchRow.addView(field,new LinearLayout.LayoutParams(0,dp(54),1));filterButton=button("");filterButton.setContentDescription("搜尋篩選");filterButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_filter008,0,0,0);filterButton.setGravity(Gravity.CENTER);filterButton.setPadding(dp(12),0,0,0);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(48),dp(48));fp.leftMargin=dp(8);searchRow.addView(filterButton,fp);root.addView(searchRow);space(root,8);
  termBar=new LinearLayout(this);andButton=button("全部符合");orButton=button("任一符合");addButton(termBar,andButton);addButton(termBar,orButton);root.addView(termBar);termBar.setVisibility(View.GONE);updateTermButtons();andButton.setOnClickListener(v->{anyTerms=false;updateTermButtons();newSearch();});orButton.setOnClickListener(v->{anyTerms=true;updateTermButtons();newSearch();});
  filterSummary=text("",13,MUTED);filterSummary.setSingleLine(true);filterSummary.setEllipsize(TextUtils.TruncateAt.END);root.addView(filterSummary);filterSummary.setVisibility(View.GONE);filterButton.setOnClickListener(v->{if(placeMode)placeRadiusDialog();else filterDialog();});space(root,4);
  count=text("",14,MUTED);root.addView(count);space(root,6);
  historyBox=vertical();root.addView(historyBox);renderHistory();
  homeEmpty=vertical();homeEmpty.setGravity(Gravity.CENTER);TextView mark=text("⌕",64,BLUE);mark.setGravity(Gravity.CENTER);homeEmpty.addView(mark);emptyTitle=text("搜尋照片中的文字",18,MUTED);emptyTitle.setGravity(Gravity.CENTER);homeEmpty.addView(emptyTitle);root.addView(homeEmpty,new LinearLayout.LayoutParams(-1,0,1));
  list=new GridView(this);list.setNumColumns(GridView.AUTO_FIT);list.setColumnWidth(dp(145));list.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);list.setHorizontalSpacing(dp(10));list.setVerticalSpacing(dp(10));list.setGravity(Gravity.CENTER);list.setClipToPadding(false);list.setPadding(dp(2),dp(4),dp(2),dp(8));adapter=new Rows();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));list.setVisibility(View.GONE);
  more=button("載入更多結果");root.addView(more,new LinearLayout.LayoutParams(-1,dp(46)));more.setVisibility(View.GONE);
  space(root,8);homeProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);homeProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));root.addView(homeProgress);homeProgress.setVisibility(View.GONE);
  LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);homeStatus=text("正在準備索引…",13,MUTED);footer.addView(homeStatus,new LinearLayout.LayoutParams(0,-2,1));homeUpdate=button("更新索引");primary(homeUpdate);footer.addView(homeUpdate,new LinearLayout.LayoutParams(dp(108),dp(46)));root.addView(footer);
  settingsPanel=vertical();settingsPanel.setPadding(dp(20),dp(10),dp(20),dp(10));section(settingsPanel,"索引更新");autoSwitch=new Switch(this);autoSwitch.setText("自動同步照片");autoSwitch.setTextColor(INK);autoSwitch.setTextSize(16);autoSwitch.setChecked(autoEnabled());settingsPanel.addView(autoSwitch,new LinearLayout.LayoutParams(-1,dp(48)));autoInfo=text("",13,MUTED);settingsPanel.addView(autoInfo);autoSwitch.setOnCheckedChangeListener((toggle,on)->setAutoEnabled(on));updateAutoInfo();space(settingsPanel,8);
  script=spinner(IndexStore.MODES);settingsPanel.addView(script,new LinearLayout.LayoutParams(-1,dp(48)));
  LinearLayout opts=new LinearLayout(this);scope=spinner(SCOPES);limit=spinner(new String[]{"1,000 張","3,000 張","5,000 張","10,000 張","全部照片"});opts.addView(scope,new LinearLayout.LayoutParams(0,dp(48),1));opts.addView(limit,new LinearLayout.LayoutParams(0,dp(48),1));settingsPanel.addView(opts);
  LinearLayout bar=new LinearLayout(this);start=button("開始掃描");primary(start);pick=button("選擇照片");addButton(bar,start);addButton(bar,pick);settingsPanel.addView(bar);
  LinearLayout control=new LinearLayout(this);pause=button("暫停");stop=button("停止");addButton(control,pause);addButton(control,stop);settingsPanel.addView(control);pause.setVisibility(View.GONE);stop.setVisibility(View.GONE);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));settingsPanel.addView(progress);progress.setVisibility(View.GONE);
  status=text("正在準備索引…",14,MUTED);settingsPanel.addView(status);device=text("",13,MUTED);settingsPanel.addView(device);space(settingsPanel,18);
  section(settingsPanel,"索引紀錄");stats=text("",14,MUTED);settingsPanel.addView(stats);space(settingsPanel,8);LinearLayout manage=new LinearLayout(this);Button browse=button("檢視索引");clear=button("清除紀錄");clear.setTextColor(0xffa13d3d);addButton(manage,browse);addButton(manage,clear);settingsPanel.addView(manage);space(settingsPanel,14);
  Button help=button("ⓘ 使用說明");settingsPanel.addView(help,new LinearLayout.LayoutParams(-1,dp(48)));help.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("使用說明").setMessage("照片只讀，不修改原始檔。文字索引完全離線。自動同步需完整照片權限，僅在 App 前景執行。已辨識且未變更的照片會跳過。暫停或停止自動掃描會關閉自動同步，可在設定重新開啟。清除索引也會關閉自動同步。").setPositiveButton("關閉",null).show());space(settingsPanel,10);TextView version=text("版本 014",12,MUTED);version.setGravity(Gravity.CENTER);settingsPanel.addView(version);
  settings.setOnClickListener(v->settingsDialog());homeUpdate.setOnClickListener(v->{if(placeMode){if(!geoStore.permission(this))askGeoPermission();else startGeoScan();return;}if(!ready||busy)return;main.removeCallbacks(autoTask);if(job!=null&&job.pending()>0&&job.scope.equals("手動選取"))beginResume();else requestAccess();});homeStatus.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);if(placeMode){if(!geoStore.permission(this))askGeoPermission();else placeRadiusDialog();}else settingsDialog();});
  browse.setOnClickListener(v->{search.setText("");browsing=true;pageSize=100;requestSerial++;resetScroll=true;requestPage();if(settingsPanel.getTag() instanceof AlertDialog)((AlertDialog)settingsPanel.getTag()).dismiss();});
  clearQuery.setOnClickListener(v->{browsing=false;search.setText("");pageSize=100;requestSerial++;resetScroll=true;requestPage();hideKeyboard();});
  search.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){rememberQuery();hideKeyboard();requestPage();return true;}return false;});
  setContentView(root);root.requestFocus();getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  if(Build.VERSION.SDK_INT>=30){root.setOnApplyWindowInsetsListener((view,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets keyboard=insets.getInsets(WindowInsets.Type.ime());view.setPadding(dp(16)+bars.left,dp(14)+bars.top,dp(16)+bars.right,dp(8)+Math.max(bars.bottom,keyboard.bottom));return WindowInsets.CONSUMED;});root.post(()->{if(!root.isAttachedToWindow()||isFinishing()||isDestroyed())return;getWindow().setDecorFitsSystemWindows(false);root.requestApplyInsets();});}
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int c){browsing=false;pageSize=100;adapter.items.clear();adapter.notifyDataSetChanged();matches=0;updateHome();requestSerial++;resetScroll=true;main.removeCallbacks(searchDelay);main.postDelayed(searchDelay,180);}public void afterTextChanged(Editable e){}});
  start.setOnClickListener(v->{if(!ready||busy)return;if(job!=null&&job.pending()>0){if(job.scope.equals("手動選取"))beginResume();else requestAccess();}else requestAccess();});
  pick.setOnClickListener(v->{if(ready&&!busy&&(job==null||job.pending()==0))openPhotoPicker();});
  pause.setOnClickListener(v->{if(autoRun||(job!=null&&job.scope.startsWith("自動同步")))setAutoEnabled(false);cancel=true;pause.setEnabled(false);status.setText("目前這張完成後暫停…");});
  stop.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("停止本批掃描？").setMessage("已完成的文字索引會保留；未辨識的待處理清單會清除。").setNegativeButton("取消",null).setPositiveButton("停止",(d,w)->{if(autoRun||(job!=null&&job.scope.startsWith("自動同步")))setAutoEnabled(false);if(busy){discard=true;cancel=true;status.setText("正在停止…");}else{runDiscard();}}).show());
  clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("清除全部辨識紀錄？").setMessage("不會刪除任何原始照片，未完成清單也會清除。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{if(!busy){setAutoEnabled(false);busy=true;updateButtons();worker.execute(()->{String err="";try{store.clear();}catch(Exception e){err=e.toString();}final String msg=err;main.post(()->{busy=false;problem=msg;job=null;requestPage();updateButtons();});});}}).show());
  more.setOnClickListener(v->{pageSize+=100;requestSerial++;requestPage();});
  list.setOnItemClickListener((a,v,p,id)->{if(p<adapter.items.size()){v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);rememberQuery();hideKeyboard();detail(adapter.items.get(p));}});
  updateButtons();
  worker.execute(()->{String err="";try{store.migrate(getApplicationContext());}catch(Exception e){err="舊索引轉移失敗："+e.getMessage();}final String message=err;final IndexStore.Job initialJob=err.isEmpty()?store.job():null;main.post(()->{if(isDestroyed())return;ready=message.isEmpty();problem=message;job=ready?initialJob:null;requestPage();updateButtons();updateStatus();scheduleAuto();});});
 }
 final Runnable searchDelay=()->requestPage();
 boolean fullAccess(){return Build.VERSION.SDK_INT>=33?checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED:checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
 boolean partialAccess(){return Build.VERSION.SDK_INT>=34&&checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED;}
 void requestAccess(){
  if(fullAccess()){scanAccess="完整相簿";startOrResume();return;}
  if(Build.VERSION.SDK_INT>=34){requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},PERMISSION);}
  else if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},PERMISSION);
  else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION);
 }
 @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(req==GEO_PERMISSION){if(geoStore.permission(this))scheduleGeoRefresh();else{geoError="地點搜尋需要完整照片及媒體位置權限";updateHome();}return;}if(req!=PERMISSION||busy)return;if(fullAccess()){scanAccess="完整相簿";startOrResume();}else if(partialAccess()){new AlertDialog.Builder(this).setTitle("僅允許部分照片").setMessage("只能掃描已授權的照片，可能不足 1,000 張。若要測試完整相簿，請選擇允許所有照片。").setNegativeButton("取消",null).setPositiveButton("掃描已授權照片",(d,w)->{scanAccess="部分授權";startOrResume();}).show();}else{status.setText("未取得照片讀取權限，可使用手動選擇照片。");}}
 void startOrResume(){if(job!=null&&job.pending()>0)beginResume();else beginMedia();}
 void launchSetup(){autoRun=false;main.removeCallbacks(autoTask);busy=true;planning=true;cancel=false;discard=false;problem="";updateButtons();status.setText("正在建立待辨識清單…");getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
 void beginMedia(){if(busy||!ready)return;final int mode=script.getSelectedItemPosition(),which=scope.getSelectedItemPosition(),max=LIMITS[limit.getSelectedItemPosition()];final String label=SCOPES[which]+"（"+scanAccess+"）";launchSetup();worker.execute(()->{
  try{HashMap<String,String> known=store.scanned(mode);ArrayList<IndexStore.Photo> photos=new ArrayList<>();int skipped=0;
   Uri collection=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
   String[] cols={"_id","_display_name","date_modified","_size","relative_path","bucket_display_name","volume_name"};
   try(Cursor c=getContentResolver().query(collection,cols,null,null,"date_added DESC, _id DESC")){
    if(c==null)throw new IllegalStateException("無法讀取相簿");
    while(c.moveToNext()&&photos.size()<max){String path=(Objects.toString(c.getString(4),"")+"/"+Objects.toString(c.getString(5),"")).toLowerCase(Locale.ROOT);
     boolean screenshot=path.contains("screenshot")||path.contains("螢幕截圖")||path.contains("截圖")||path.contains("屏幕截图");
     boolean camera=path.contains("/camera")||path.startsWith("dcim/camera")||path.endsWith("/相機");
     if(which==1&&(!camera||screenshot))continue;if(which==2&&!screenshot)continue;
     String volume=c.getString(6);Uri u=ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(volume==null?MediaStore.VOLUME_EXTERNAL:volume),c.getLong(0));
     String stamp=c.getLong(2)+":"+c.getLong(3);String key=u.toString();if(stamp.equals(known.get(key))){skipped++;continue;}
     photos.add(new IndexStore.Photo(key,Objects.toString(c.getString(1),"照片"),stamp,mode));
    }
   }
   store.createJob(photos,skipped,label);scanJob();
  }catch(Exception ex){finishRun("掃描清單建立失敗："+ex.getMessage());}
 });}
 void beginResume(){if(busy||!ready)return;launchSetup();worker.execute(()->scanJob());}
 void openPhotoPicker(){if(Build.VERSION.SDK_INT>=33){try{Intent i=new Intent(MediaStore.ACTION_PICK_IMAGES);i.setType("image/*");i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,MediaStore.getPickImagesMaxLimit());i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK);return;}catch(ActivityNotFoundException|IllegalArgumentException ignored){}}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_DOCUMENT);}
 @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if((req!=PICK&&req!=PICK_DOCUMENT)||res!=RESULT_OK||data==null||busy||!ready)return;
  LinkedHashSet<Uri> uris=new LinkedHashSet<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++){Uri u=data.getClipData().getItemAt(i).getUri();if(u!=null)uris.add(u);}}else if(data.getData()!=null)uris.add(data.getData());if(uris.isEmpty())return;
  for(Uri u:uris)try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
  final int mode=script.getSelectedItemPosition();launchSetup();worker.execute(()->{try{HashMap<String,String> known=store.scanned(mode);ArrayList<IndexStore.Photo> photos=new ArrayList<>();int skipped=0;for(Uri u:uris){String key=u.toString();if(known.containsKey(key)){skipped++;continue;}photos.add(new IndexStore.Photo(key,name(u),"",mode));}store.createJob(photos,skipped,"手動選取");scanJob();}catch(Exception ex){finishRun("清單建立失敗："+ex.getMessage());}});
 }
 String name(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return Objects.toString(c.getString(0),"照片");}catch(Exception ignored){}return "照片";}
 TextRecognizer recognizer(int mode){return mode==1?TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS):TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());}
 void scanJob(){
  String failure="";TextRecognizer r=null;int model=-1;long mark=SystemClock.elapsedRealtime();main.post(()->{planning=false;requestPage();updateButtons();});
  try{
   while(!cancel&&active){IndexStore.Photo p=store.next();if(p==null)break;
    if(model!=p.mode){if(r!=null)r.close();r=recognizer(p.mode);model=p.mode;}
    String body="",error="";long started=SystemClock.elapsedRealtime();
    try{InputImage im=InputImage.fromFilePath(this,Uri.parse(p.uri));body=Tasks.await(r.process(im),90,TimeUnit.SECONDS).getText();}catch(Exception e){error=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
    long now=SystemClock.elapsedRealtime();store.finish(p,body,error,now-started,now-mark);mark=now;
    if(now-lastRefresh>=1200){lastRefresh=now;main.post(()->requestPage());}
   }
  }catch(Exception e){failure="掃描中斷："+e.getMessage();}finally{if(r!=null)try{r.close();}catch(Exception ignored){}}
  if(discard)try{store.discard();}catch(Exception ex){failure="停止失敗："+ex.getMessage();}
  finishRun(failure);
 }
 void finishRun(String message){finishRunInternal(message,true);}
 void finishRunInternal(String message,boolean recheck){
  IndexStore.Job current=null;try{current=store.job();}catch(Exception ignored){}final IndexStore.Job actual=current;
  main.post(()->{if(isDestroyed())return;job=actual;busy=false;planning=false;autoRun=false;problem=message;metadataDirty=true;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);requestPage();updateButtons();updateStatus();if(recheck&&message.isEmpty()&&(job==null||job.pending()==0))scheduleAuto();else if(autoRequested&&active&&message.isEmpty()){main.removeCallbacks(autoTask);main.postDelayed(autoTask,1200);}});
 }
 void runDiscard(){busy=true;updateButtons();worker.execute(()->{String error="";try{store.discard();}catch(Exception e){error=e.toString();}finishRun(error);});}
 void requestPage(){
  if(isDestroyed()||store==null||!ready)return;if(placeMode){requestGeoPage();return;}if(readBusy){readAgain=true;return;}readBusy=true;
  final int serial=requestSerial;final boolean useFuzzy=fuzzyEnabled;final String q=search.getText().toString();final SearchSpec spec=filters.withQuery(q,anyTerms);final int n=IndexStore.norm(q).isEmpty()&&!browsing&&!spec.filtered()?0:pageSize;final boolean refresh=metadataDirty;metadataDirty=false;
  reader.execute(()->{IndexStore.Page p=null;ArrayList<String[]> albums=null;String err="";try{if(refresh){store.refreshMetadata(getApplicationContext());albums=store.albums();}p=store.search(spec,n,useFuzzy,()->serial!=requestSerial||Thread.currentThread().isInterrupted());}catch(Exception e){err=e.toString();}final IndexStore.Page page=p;final ArrayList<String[]> availableAlbums=albums;final String message=err;main.post(()->{if(isDestroyed())return;readBusy=false;if(availableAlbums!=null)albumChoices=availableAlbums;if(page!=null){job=page.job;indexed=page.ok+page.empty+page.failed;stats.setText("累計 "+indexed+" 筆｜有文字 "+page.ok+"\n無文字 "+page.empty+"｜失敗 "+page.failed);if(serial==requestSerial){boolean toTop=resetScroll;int pos=toTop?0:list.getFirstVisiblePosition();resetScroll=false;adapter.items=page.rows;adapter.query=q;adapter.terms=FuzzyText.terms(q,HanFold::fold,useFuzzy);adapter.fuzzy=useFuzzy;adapter.notifyDataSetChanged();if(pos<adapter.getCount()&&(toTop||list.getFirstVisiblePosition()!=pos))list.setSelection(pos);matches=page.matches;count.setText((browsing?"全部索引 ":"符合 ")+page.matches+" 筆"+(page.nearMatches>0?"｜精確 "+(page.matches-page.nearMatches)+"・近似 "+page.nearMatches:""));more.setVisibility(page.matches>page.rows.size()?View.VISIBLE:View.GONE);}else readAgain=true;}else if(!message.isEmpty()){problem="索引讀取失敗："+message;}updateStatus();updateButtons();if(readAgain){readAgain=false;requestPage();}else if(autoRequested&&active&&!busy){main.removeCallbacks(autoTask);main.postDelayed(autoTask,600);}});});
 }
 void updateButtons(){boolean pending=job!=null&&job.pending()>0;start.setEnabled(ready&&!busy);start.setText(pending?"繼續掃描":"開始掃描");pick.setEnabled(ready&&!busy&&!pending);clear.setEnabled(ready&&!busy);scope.setEnabled(ready&&!busy&&!pending);limit.setEnabled(ready&&!busy&&!pending);script.setEnabled(ready&&!busy&&!pending);pause.setVisibility(busy&&!planning?View.VISIBLE:View.GONE);pause.setEnabled(busy&&!cancel);stop.setVisibility((busy||pending)?View.VISIBLE:View.GONE);updateAutoInfo();updateHome();}
 void updateStatus(){
  if(planning){status.setText("正在建立待辨識清單…");updateHome();return;}
  if(job==null){progress.setVisibility(View.GONE);status.setText(!problem.isEmpty()?problem:ready?"可直接掃描相簿或選取照片。":"正在準備索引…");}
  else{progress.setVisibility(View.VISIBLE);progress.setMax(Math.max(1,job.total));progress.setProgress(job.done);String state=busy?(cancel?"正在暫停":"辨識中"):(job.pending()>0?"已暫停": "本批結束");double percent=job.total==0?100:100.0*job.done/job.total;double avg=job.done==0?0:job.ms/(1000.0*job.done);
   String eta=job.pending()>0&&job.done>0?"｜剩餘約 "+duration(job.elapsed*job.pending()/job.done):"";
   status.setText(String.format(Locale.TAIWAN,"%s %d / %d 張（%.1f%%）｜跳過 %d\n有字 %d｜無字 %d｜失敗 %d\n總耗時 %s｜平均 %.2f 秒/張%s",state,job.done,job.total,percent,job.skipped,job.withText,job.empty,job.failed,duration(job.elapsed),avg,eta)+(!problem.isEmpty()?"\n"+problem:""));
  }
  try{Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(b!=null){int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,100),temp=b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);device.setText((level>=0&&scale>0?"電量 "+(100*level/scale)+"%":"")+(temp!=Integer.MIN_VALUE?String.format(Locale.TAIWAN,"｜電池 %.1f°C",temp/10.0):""));}}catch(Exception ignored){}updateHome();
 }
 String duration(long ms){long s=ms/1000;return s>=3600?(s/3600)+"時 "+(s%3600/60)+"分 "+s%60+"秒":s>=60?s/60+"分 "+s%60+"秒":String.format(Locale.TAIWAN,"%.1f秒",ms/1000.0);}
 class SquareImage extends ImageView{SquareImage(){super(MainActivity.this);}@Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);super.onMeasure(w,MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY));}}
 GradientDrawable outline(int fill,int radius,int stroke){GradientDrawable d=bg(fill,radius);d.setStroke(dp(1),stroke);return d;}
 CharSequence highlighted(MatchText.Segment segment){SpannableString s=new SpannableString(segment.text);for(MatchText.Range r:segment.hits){s.setSpan(new android.text.style.BackgroundColorSpan(r.approximate?0xffffbc85:0xffffde59),r.start,r.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);s.setSpan(new android.text.style.ForegroundColorSpan(0xff20302b),r.start,r.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),r.start,r.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}return s;}
 void scrollToMatch(ScrollView sc,TextView raw,int offset){if(offset<0)return;sc.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){if(raw.getLayout()==null||sc.getHeight()==0)return;sc.getViewTreeObserver().removeOnGlobalLayoutListener(this);sc.post(()->{android.text.Layout layout=raw.getLayout();if(layout==null)return;int line=layout.getLineForOffset(Math.min(offset,raw.length()));int y=raw.getTop()+raw.getTotalPaddingTop()+layout.getLineTop(Math.max(0,line-1));sc.scrollTo(0,Math.max(0,y));});}});}

 class Rows extends BaseAdapter{
  ArrayList<IndexStore.Result> items=new ArrayList<>();String query="";boolean fuzzy=true;ArrayList<String> terms=new ArrayList<>();public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
  public View getView(int pos,View convert,ViewGroup parent){Holder h;if(convert==null){LinearLayout card=vertical();card.setBackground(outline(Color.WHITE,14,0xffcbd8d3));card.setElevation(dp(1));card.setClipToOutline(true);card.setPadding(dp(6),dp(6),dp(6),dp(6));h=new Holder();h.img=new SquareImage();h.img.setBackground(bg(PALE,10));h.img.setClipToOutline(true);h.img.setForeground(outline(Color.TRANSPARENT,10,0xffaabdb6));h.img.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(h.img,new LinearLayout.LayoutParams(-1,-2));space(card,6);h.body=text("",13,INK);h.body.setMaxLines(2);h.body.setEllipsize(TextUtils.TruncateAt.END);h.body.setPadding(dp(3),0,dp(3),dp(2));h.body.setGravity(Gravity.TOP);h.body.setLines(2);card.addView(h.body,new LinearLayout.LayoutParams(-1,-2));card.setTag(h);convert=card;}else h=(Holder)convert.getTag();
   IndexStore.Result e=items.get(pos);h.body.setText(e.geo?e.text:e.error.isEmpty()?(e.text.trim().isEmpty()?"未辨識出文字":highlighted(FuzzyText.snippet(e.text,terms,HanFold::fold,fuzzy))):"辨識失敗");h.img.setContentDescription(e.approximate?"近似符合":"搜尋結果");loadThumb(h.img,e.uri);return convert;}
 }
 static class Holder{ImageView img;TextView body;}
 void loadThumb(ImageView view,String key){view.setTag(key);Bitmap b=cache.get(key);if(b!=null){view.setImageBitmap(b);return;}view.setImageResource(android.R.drawable.ic_menu_gallery);thumbs.execute(()->{try{Bitmap image=getContentResolver().loadThumbnail(Uri.parse(key),new Size(dp(180),dp(180)),null);cache.put(key,image);main.post(()->{if(key.equals(view.getTag()))view.setImageBitmap(image);});}catch(Exception ignored){}});}
 void sharePhoto(IndexStore.Result e){try{Uri u=Uri.parse(e.uri);Intent i=new Intent(Intent.ACTION_SEND);String type=getContentResolver().getType(u);i.setType(type==null?"image/*":type);i.putExtra(Intent.EXTRA_STREAM,u);i.setClipData(ClipData.newUri(getContentResolver(),e.name,u));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"分享原始照片"));}catch(Exception ex){Toast.makeText(this,"無法分享這張照片",Toast.LENGTH_SHORT).show();}}
 void detail(IndexStore.Result e){Dialog dlg=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);LinearLayout l=vertical();l.setBackgroundColor(BG);l.setPadding(dp(14),dp(10),dp(14),dp(10));LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);Button back=button("‹");back.setTextSize(26);back.setContentDescription("返回");h.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView title=text(e.name,16,INK);title.setGravity(Gravity.CENTER);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);h.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button share=button("分享");h.addView(share,new LinearLayout.LayoutParams(dp(64),dp(48)));l.addView(h);space(l,10);
  ZoomPhoto img=new ZoomPhoto(this);img.setBackground(bg(0xffedf1ed,14));img.setContentDescription("照片預覽，雙指縮放");l.addView(img,new LinearLayout.LayoutParams(-1,0,1));space(l,10);
  LinearLayout action=new LinearLayout(this);action.setGravity(Gravity.CENTER_VERTICAL);TextView label=text(e.geo?"地點資訊":"辨識文字",16,BLUE);label.setTypeface(null,Typeface.BOLD);action.addView(label,new LinearLayout.LayoutParams(0,-2,1));Button copy=button(e.geo?"複製資訊":"複製全文");action.addView(copy,new LinearLayout.LayoutParams(dp(106),dp(46)));l.addView(action);
  ScrollView sc=new ScrollView(this);MatchText.Segment full=FuzzyText.full(e.error.isEmpty()?(e.text.isEmpty()?"未辨識出文字":e.text):e.error,e.error.isEmpty()&&!e.geo?adapter.terms:java.util.Collections.emptyList(),HanFold::fold,adapter.fuzzy&&!e.geo);TextView raw=text("",16,INK);raw.setText(highlighted(full));raw.setPadding(dp(10),dp(8),dp(10),dp(8));raw.setTextIsSelectable(true);raw.setBackground(bg(Color.WHITE,12));sc.addView(raw);l.addView(sc,new LinearLayout.LayoutParams(-1,dp(getResources().getConfiguration().orientation==2?92:150)));
  back.setOnClickListener(v->dlg.dismiss());share.setOnClickListener(v->sharePhoto(e));copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("OCR",e.text));Toast.makeText(this,"已複製文字",Toast.LENGTH_SHORT).show();});
  dlg.setContentView(l);Window w=dlg.getWindow();if(w!=null){w.setStatusBarColor(BG);w.setNavigationBarColor(BG);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);if(Build.VERSION.SDK_INT>=30){l.setOnApplyWindowInsetsListener((v,insets)->{Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(14)+b.left,dp(10)+b.top,dp(14)+b.right,dp(10)+b.bottom);return WindowInsets.CONSUMED;});}}
  dlg.show();if(w!=null){w.setLayout(-1,-1);if(Build.VERSION.SDK_INT>=30){w.setDecorFitsSystemWindows(false);l.requestApplyInsets();}}if(!full.hits.isEmpty())scrollToMatch(sc,raw,full.hits.get(0).start);
  thumbs.execute(()->{try{Bitmap b=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),Uri.parse(e.uri)),(decoder,info,source)->{int wi=info.getSize().getWidth(),he=info.getSize().getHeight();decoder.setTargetSampleSize(Math.max(1,(Math.max(wi,he)+2999)/3000));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});main.post(()->{if(dlg.isShowing())img.setPhoto(b);});}catch(Exception ex){main.post(()->{if(dlg.isShowing())img.failed=true;img.invalidate();});}});
 }
 @Override protected void onStart(){super.onStart();active=true;registerMediaObserver();if(ready){metadataDirty=true;requestPage();}scheduleAuto();scheduleGeoRefresh();}
 @Override protected void onStop(){active=false;geoCancel=true;main.removeCallbacks(geoTask);main.removeCallbacks(autoTask);unregisterMediaObserver();if(busy)cancel=true;super.onStop();}
 @Override protected void onDestroy(){active=false;cancel=true;unregisterMediaObserver();main.removeCallbacks(autoTask);main.removeCallbacks(searchDelay);main.removeCallbacks(geoTask);worker.shutdown();reader.shutdown();geoWorker.shutdown();thumbs.shutdown();super.onDestroy();}
}
