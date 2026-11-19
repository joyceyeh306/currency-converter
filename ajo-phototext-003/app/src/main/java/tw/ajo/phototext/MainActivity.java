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
 final int BLUE=0xff075b75,BG=0xfff1f6f8,INK=0xff142c36,MUTED=0xff526975;
 static final int PICK=20,PICK_DOCUMENT=21,PERMISSION=22;
 static final String[] SCOPES={"全部相簿","相機照片","螢幕截圖"};
 static final int[] LIMITS={1000,3000,5000,10000,Integer.MAX_VALUE};
 final ExecutorService worker=Executors.newSingleThreadExecutor(),reader=Executors.newSingleThreadExecutor();
 final ThreadPoolExecutor thumbs=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(32),new ThreadPoolExecutor.DiscardOldestPolicy());
 final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 final Handler main=new Handler(Looper.getMainLooper());
 IndexStore store; TextView status,count,device; EditText search; Spinner script,scope,limit; Button start,pick,pause,stop,clear,more;
 ProgressBar progress; ListView list; Rows adapter; volatile boolean cancel=false,discard=false,active=false; boolean ready=false,busy=false,planning=false,readBusy=false,readAgain=false,resetScroll=false;
 IndexStore.Job job; int pageSize=100,matches=0,requestSerial=0; String problem="",scanAccess=""; long lastRefresh=0;
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 GradientDrawable bg(int color,int rad){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(rad));return d;}
 Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setAllCaps(false);return b;}
 void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);return s;}
 void addButton(LinearLayout r,Button b){r.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  store=new IndexStore(getApplicationContext());
  LinearLayout root=vertical();root.setBackgroundColor(BG);root.setPadding(dp(16),dp(14),dp(16),dp(8));
  TextView title=text("ㄚ喬文字搜尋測試",23,BLUE);title.setTypeface(null,Typeface.BOLD);root.addView(title);root.addView(text("006  •  大量照片索引",13,MUTED));space(root,4);
  script=spinner(IndexStore.MODES);root.addView(script,new LinearLayout.LayoutParams(-1,dp(40)));
  LinearLayout opts=new LinearLayout(this);scope=spinner(SCOPES);limit=spinner(new String[]{"1,000 張","3,000 張","5,000 張","10,000 張","全部照片"});opts.addView(scope,new LinearLayout.LayoutParams(0,dp(42),1));opts.addView(limit,new LinearLayout.LayoutParams(0,dp(42),1));root.addView(opts);
  LinearLayout bar=new LinearLayout(this);start=button("開始掃描");start.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));start.setTextColor(Color.WHITE);pick=button("選擇照片");clear=button("清除紀錄");addButton(bar,start);addButton(bar,pick);addButton(bar,clear);root.addView(bar);
  LinearLayout control=new LinearLayout(this);pause=button("暫停");stop=button("停止");addButton(control,pause);addButton(control,stop);root.addView(control);pause.setVisibility(View.GONE);stop.setVisibility(View.GONE);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);root.addView(progress);progress.setVisibility(View.GONE);
  status=text("正在準備索引…",13,MUTED);root.addView(status);
  device=text("",12,MUTED);root.addView(device);space(root,5);
  search=new EditText(this);search.setSingleLine(true);search.setTextSize(17);search.setHint("搜尋照片中的文字");search.setPadding(dp(12),dp(8),dp(12),dp(8));search.setBackground(bg(Color.WHITE,12));root.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
  count=text("",13,MUTED);root.addView(count);
  list=new ListView(this);list.setDivider(null);list.setCacheColorHint(Color.TRANSPARENT);list.setPadding(0,dp(4),0,0);list.setClipToPadding(false);adapter=new Rows();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  more=button("載入更多結果");root.addView(more,new LinearLayout.LayoutParams(-1,dp(40)));more.setVisibility(View.GONE);
  setContentView(root);
  if(Build.VERSION.SDK_INT>=30){root.setOnApplyWindowInsetsListener((view,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets keyboard=insets.getInsets(WindowInsets.Type.ime());view.setPadding(dp(16)+bars.left,dp(14)+bars.top,dp(16)+bars.right,dp(8)+Math.max(bars.bottom,keyboard.bottom));return WindowInsets.CONSUMED;});root.post(()->{if(!root.isAttachedToWindow()||isFinishing()||isDestroyed())return;getWindow().setDecorFitsSystemWindows(false);root.requestApplyInsets();});}
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int c){pageSize=100;requestSerial++;resetScroll=true;main.removeCallbacks(searchDelay);main.postDelayed(searchDelay,180);}public void afterTextChanged(Editable e){}});
  start.setOnClickListener(v->{if(!ready||busy)return;if(job!=null&&job.pending()>0){if(job.scope.equals("手動選取"))beginResume();else requestAccess();}else requestAccess();});
  pick.setOnClickListener(v->{if(ready&&!busy&&(job==null||job.pending()==0))openPhotoPicker();});
  pause.setOnClickListener(v->{cancel=true;pause.setEnabled(false);status.setText("目前這張完成後暫停…");});
  stop.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("停止本批掃描？").setMessage("已完成的文字索引會保留；未辨識的待處理清單會清除。").setNegativeButton("取消",null).setPositiveButton("停止",(d,w)->{if(busy){discard=true;cancel=true;status.setText("正在停止…");}else{runDiscard();}}).show());
  clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("清除全部辨識紀錄？").setMessage("不會刪除任何原始照片，未完成清單也會清除。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{if(!busy){busy=true;updateButtons();worker.execute(()->{String err="";try{store.clear();}catch(Exception e){err=e.toString();}final String msg=err;main.post(()->{busy=false;problem=msg;job=null;requestPage();updateButtons();});});}}).show());
  more.setOnClickListener(v->{pageSize+=100;requestSerial++;requestPage();});
  list.setOnItemClickListener((a,v,p,id)->{if(p<adapter.items.size())detail(adapter.items.get(p));});
  updateButtons();
  worker.execute(()->{String err="";try{store.migrate(getApplicationContext());}catch(Exception e){err="舊索引轉移失敗："+e.getMessage();}final String message=err;final IndexStore.Job initialJob=err.isEmpty()?store.job():null;main.post(()->{if(isDestroyed())return;ready=message.isEmpty();problem=message;job=ready?initialJob:null;requestPage();updateButtons();updateStatus();});});
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
 @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(req!=PERMISSION||busy)return;if(fullAccess()){scanAccess="完整相簿";startOrResume();}else if(partialAccess()){new AlertDialog.Builder(this).setTitle("僅允許部分照片").setMessage("只能掃描已授權的照片，可能不足 1,000 張。若要測試完整相簿，請選擇允許所有照片。").setNegativeButton("取消",null).setPositiveButton("掃描已授權照片",(d,w)->{scanAccess="部分授權";startOrResume();}).show();}else{status.setText("未取得照片讀取權限，可使用手動選擇照片。");}}
 void startOrResume(){if(job!=null&&job.pending()>0)beginResume();else beginMedia();}
 void launchSetup(){busy=true;planning=true;cancel=false;discard=false;problem="";updateButtons();status.setText("正在建立待辨識清單…");getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
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
 void finishRun(String message){main.post(()->{if(isDestroyed())return;busy=false;planning=false;problem=message;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);requestPage();updateButtons();updateStatus();});}
 void runDiscard(){busy=true;updateButtons();worker.execute(()->{String error="";try{store.discard();}catch(Exception e){error=e.toString();}finishRun(error);});}
 void requestPage(){
  if(isDestroyed()||store==null)return;if(readBusy){readAgain=true;return;}readBusy=true;
  final int serial=requestSerial;final String q=search.getText().toString();final int n=pageSize;
  reader.execute(()->{IndexStore.Page p=null;String err="";try{p=store.search(q,n);}catch(Exception e){err=e.toString();}final IndexStore.Page page=p;final String message=err;main.post(()->{if(isDestroyed())return;readBusy=false;if(page!=null){job=page.job;if(serial==requestSerial){int pos=resetScroll?0:list.getFirstVisiblePosition();View child=list.getChildAt(0);int top=resetScroll||child==null?0:child.getTop();resetScroll=false;adapter.items=page.rows;adapter.notifyDataSetChanged();if(pos<adapter.getCount())list.setSelectionFromTop(pos,top);matches=page.matches;count.setText("符合 "+page.matches+" 筆｜有文字 "+page.ok+"｜無文字 "+page.empty+"｜失敗 "+page.failed);more.setVisibility(page.matches>page.rows.size()?View.VISIBLE:View.GONE);}else readAgain=true;}else if(!message.isEmpty()){problem="索引讀取失敗："+message;}updateStatus();updateButtons();if(readAgain){readAgain=false;requestPage();}});});
 }
 void updateButtons(){boolean pending=job!=null&&job.pending()>0;start.setEnabled(ready&&!busy);start.setText(pending?"繼續掃描":"開始掃描");pick.setEnabled(ready&&!busy&&!pending);clear.setEnabled(ready&&!busy);scope.setEnabled(ready&&!busy&&!pending);limit.setEnabled(ready&&!busy&&!pending);script.setEnabled(ready&&!busy&&!pending);pause.setVisibility(busy&&!planning?View.VISIBLE:View.GONE);pause.setEnabled(busy&&!cancel);stop.setVisibility((busy||pending)?View.VISIBLE:View.GONE);}
 void updateStatus(){
  if(planning){status.setText("正在建立待辨識清單…");return;}
  if(job==null){progress.setVisibility(View.GONE);status.setText(!problem.isEmpty()?problem:ready?"可直接掃描相簿或選取照片。":"正在準備索引…");}
  else{progress.setVisibility(View.VISIBLE);progress.setMax(Math.max(1,job.total));progress.setProgress(job.done);String state=busy?(cancel?"正在暫停":"辨識中"):(job.pending()>0?"已暫停": "本批結束");double percent=job.total==0?100:100.0*job.done/job.total;double avg=job.done==0?0:job.ms/(1000.0*job.done);
   String eta=job.pending()>0&&job.done>0?"｜剩餘約 "+duration(job.elapsed*job.pending()/job.done):"";
   status.setText(String.format(Locale.TAIWAN,"%s %d / %d 張（%.1f%%）｜跳過 %d\n有字 %d｜無字 %d｜失敗 %d\n總耗時 %s｜平均 %.2f 秒/張%s",state,job.done,job.total,percent,job.skipped,job.withText,job.empty,job.failed,duration(job.elapsed),avg,eta)+(!problem.isEmpty()?"\n"+problem:""));
  }
  try{Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(b!=null){int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,100),temp=b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);device.setText((level>=0&&scale>0?"電量 "+(100*level/scale)+"%":"")+(temp!=Integer.MIN_VALUE?String.format(Locale.TAIWAN,"｜電池 %.1f°C",temp/10.0):""));}}catch(Exception ignored){}
 }
 String duration(long ms){long s=ms/1000;return s>=3600?(s/3600)+"時 "+(s%3600/60)+"分 "+s%60+"秒":s>=60?s/60+"分 "+s%60+"秒":String.format(Locale.TAIWAN,"%.1f秒",ms/1000.0);}
 class Rows extends BaseAdapter{
  ArrayList<IndexStore.Result> items=new ArrayList<>();public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
  public View getView(int pos,View convert,android.view.ViewGroup parent){Holder h;if(convert==null){LinearLayout outer=vertical();outer.setPadding(0,0,0,dp(8));LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(10),dp(10));row.setBackground(bg(Color.WHITE,12));h=new Holder();h.img=new ImageView(MainActivity.this);h.img.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(h.img,new LinearLayout.LayoutParams(dp(70),dp(70)));LinearLayout info=vertical();info.setPadding(dp(10),0,0,0);h.title=text("",15,INK);h.title.setTypeface(null,Typeface.BOLD);h.title.setMaxLines(2);info.addView(h.title);h.meta=text("",12,MUTED);info.addView(h.meta);h.body=text("",14,INK);h.body.setMaxLines(3);h.body.setEllipsize(TextUtils.TruncateAt.END);info.addView(h.body);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));outer.addView(row);outer.setTag(h);convert=outer;}else h=(Holder)convert.getTag();
   IndexStore.Result e=items.get(pos);h.title.setText(e.name);h.meta.setText(IndexStore.MODES[e.mode]+String.format(Locale.TAIWAN," · %.2f 秒",e.ms/1000.0));h.body.setText(e.error.isEmpty()?(e.text.trim().isEmpty()?"未辨識出文字":e.text):"辨識失敗："+e.error);loadThumb(h.img,e.uri);return convert;}
 }
 static class Holder{ImageView img;TextView title,meta,body;}
 void loadThumb(ImageView view,String key){view.setTag(key);Bitmap b=cache.get(key);if(b!=null){view.setImageBitmap(b);return;}view.setImageResource(android.R.drawable.ic_menu_gallery);thumbs.execute(()->{try{Bitmap image=getContentResolver().loadThumbnail(Uri.parse(key),new Size(dp(96),dp(96)),null);cache.put(key,image);main.post(()->{if(key.equals(view.getTag()))view.setImageBitmap(image);});}catch(Exception ignored){}});}
 void detail(IndexStore.Result e){ScrollView sc=new ScrollView(this);LinearLayout l=vertical();l.setPadding(dp(18),dp(8),dp(18),dp(12));ImageView img=new ImageView(this);img.setAdjustViewBounds(true);img.setMaxHeight(dp(360));img.setScaleType(ImageView.ScaleType.FIT_CENTER);img.setImageResource(android.R.drawable.ic_menu_gallery);l.addView(img,new LinearLayout.LayoutParams(-1,-2));l.addView(text(IndexStore.MODES[e.mode]+String.format(Locale.TAIWAN,"　%.2f 秒",e.ms/1000.0),13,MUTED));TextView raw=text(e.error.isEmpty()?(e.text.isEmpty()?"未辨識出文字":e.text):e.error,17,INK);raw.setTextIsSelectable(true);l.addView(raw);sc.addView(l);new AlertDialog.Builder(this).setTitle(e.name).setView(sc).setNegativeButton("關閉",null).setPositiveButton("複製文字",(d,w)->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("OCR",e.text))).show();
  thumbs.execute(()->{try{Bitmap b=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),Uri.parse(e.uri)),(decoder,info,source)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();decoder.setTargetSampleSize(Math.max(1,(Math.max(w,h)+1499)/1500));});main.post(()->img.setImageBitmap(b));}catch(Exception ignored){}});
 }
 @Override protected void onStart(){super.onStart();active=true;if(ready)requestPage();}
 @Override protected void onStop(){active=false;if(busy)cancel=true;super.onStop();}
 @Override protected void onDestroy(){active=false;cancel=true;main.removeCallbacks(searchDelay);worker.shutdown();reader.shutdown();thumbs.shutdown();super.onDestroy();}
}
