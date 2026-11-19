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
 static final int PICK=20,PICK_DOCUMENT=21,PERMISSION=22;
 static final String[] SCOPES={"全部相簿","相機照片","螢幕截圖"};
 static final int[] LIMITS={1000,3000,5000,10000,Integer.MAX_VALUE};
 final ExecutorService worker=Executors.newSingleThreadExecutor(),reader=Executors.newSingleThreadExecutor();
 final ThreadPoolExecutor thumbs=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(32),new ThreadPoolExecutor.DiscardOldestPolicy());
 final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 final Handler main=new Handler(Looper.getMainLooper());
 IndexStore store; TextView status,count,device; EditText search; Spinner script,scope,limit; Button start,pick,pause,stop,clear,more;
 ProgressBar progress,homeProgress; GridView list; Rows adapter; LinearLayout settingsPanel,homeEmpty,historyBox; TextView homeStatus,stats,emptyTitle; Button homeUpdate,settings,clearQuery; boolean browsing=false; int indexed=0; volatile boolean cancel=false,discard=false,active=false; boolean ready=false,busy=false,planning=false,readBusy=false,readAgain=false,resetScroll=false;
 IndexStore.Job job; int pageSize=100,matches=0,requestSerial=0; String problem="",scanAccess=""; long lastRefresh=0;
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
 void updateHome(){boolean q=!IndexStore.norm(search.getText().toString()).isEmpty();boolean show=q||browsing;boolean has=show&&adapter.getCount()>0;list.setVisibility(has?View.VISIBLE:View.GONE);homeEmpty.setVisibility(has?View.GONE:View.VISIBLE);historyBox.setVisibility(show?View.GONE:View.VISIBLE);emptyTitle.setText(show?(readBusy?"搜尋中…":"沒有符合的照片"):"搜尋照片中的文字");count.setVisibility(show?View.VISIBLE:View.GONE);more.setVisibility(has&&matches>adapter.getCount()?View.VISIBLE:View.GONE);clearQuery.setVisibility(q||browsing?View.VISIBLE:View.INVISIBLE);homeUpdate.setEnabled(ready&&!busy);homeUpdate.setText(job!=null&&job.pending()>0?"繼續掃描":"更新索引");homeProgress.setVisibility(busy?View.VISIBLE:View.GONE);if(job!=null){homeProgress.setMax(Math.max(1,job.total));homeProgress.setProgress(job.done);}homeStatus.setText(!problem.isEmpty()?problem:!ready?"正在準備索引…":busy?(planning?"正在準備掃描…":"辨識中 "+(job==null?0:job.done)+" / "+(job==null?0:job.total)):job!=null&&job.pending()>0?"已暫停・剩餘 "+job.pending()+" 張":"已建立 "+String.format(Locale.TAIWAN,"%,d",indexed)+" 筆索引");}

 void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);return s;}
 void addButton(LinearLayout r,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),dp(2),dp(3),dp(2));r.addView(b,p);}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  store=new IndexStore(getApplicationContext());
  LinearLayout root=vertical();root.setBackgroundColor(BG);root.setPadding(dp(16),dp(14),dp(16),dp(8));root.setFocusableInTouchMode(true);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("ㄚ喬相片搜尋",25,BLUE);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));settings=button("⚙");settings.setTextSize(24);settings.setContentDescription("設定");header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);space(root,14);
  LinearLayout field=new LinearLayout(this);field.setGravity(Gravity.CENTER_VERTICAL);GradientDrawable fieldBg=bg(Color.WHITE,22);fieldBg.setStroke(dp(1),LINE);field.setBackground(fieldBg);field.setPadding(dp(12),0,dp(6),0);TextView glass=text("⌕",28,BLUE);glass.setGravity(Gravity.CENTER);field.addView(glass,new LinearLayout.LayoutParams(dp(36),dp(54)));search=new EditText(this);search.setSingleLine(true);search.setTextSize(18);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("輸入照片中的文字");search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(4),0,0,0);field.addView(search,new LinearLayout.LayoutParams(0,dp(54),1));clearQuery=button("×");clearQuery.setTextSize(22);clearQuery.setContentDescription("清除搜尋");field.addView(clearQuery,new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(field);space(root,12);
  count=text("",14,MUTED);root.addView(count);space(root,6);
  historyBox=vertical();root.addView(historyBox);renderHistory();
  homeEmpty=vertical();homeEmpty.setGravity(Gravity.CENTER);TextView mark=text("⌕",64,BLUE);mark.setGravity(Gravity.CENTER);homeEmpty.addView(mark);emptyTitle=text("搜尋照片中的文字",18,MUTED);emptyTitle.setGravity(Gravity.CENTER);homeEmpty.addView(emptyTitle);root.addView(homeEmpty,new LinearLayout.LayoutParams(-1,0,1));
  list=new GridView(this);list.setNumColumns(GridView.AUTO_FIT);list.setColumnWidth(dp(145));list.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);list.setHorizontalSpacing(dp(9));list.setVerticalSpacing(dp(9));list.setGravity(Gravity.CENTER);list.setClipToPadding(false);list.setPadding(0,dp(2),0,dp(6));adapter=new Rows();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));list.setVisibility(View.GONE);
  more=button("載入更多結果");root.addView(more,new LinearLayout.LayoutParams(-1,dp(46)));more.setVisibility(View.GONE);
  space(root,8);homeProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);homeProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));root.addView(homeProgress);homeProgress.setVisibility(View.GONE);
  LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);homeStatus=text("正在準備索引…",13,MUTED);footer.addView(homeStatus,new LinearLayout.LayoutParams(0,-2,1));homeUpdate=button("更新索引");primary(homeUpdate);footer.addView(homeUpdate,new LinearLayout.LayoutParams(dp(108),dp(46)));root.addView(footer);
  settingsPanel=vertical();settingsPanel.setPadding(dp(20),dp(10),dp(20),dp(10));section(settingsPanel,"索引更新");
  script=spinner(IndexStore.MODES);settingsPanel.addView(script,new LinearLayout.LayoutParams(-1,dp(48)));
  LinearLayout opts=new LinearLayout(this);scope=spinner(SCOPES);limit=spinner(new String[]{"1,000 張","3,000 張","5,000 張","10,000 張","全部照片"});opts.addView(scope,new LinearLayout.LayoutParams(0,dp(48),1));opts.addView(limit,new LinearLayout.LayoutParams(0,dp(48),1));settingsPanel.addView(opts);
  LinearLayout bar=new LinearLayout(this);start=button("開始掃描");primary(start);pick=button("選擇照片");addButton(bar,start);addButton(bar,pick);settingsPanel.addView(bar);
  LinearLayout control=new LinearLayout(this);pause=button("暫停");stop=button("停止");addButton(control,pause);addButton(control,stop);settingsPanel.addView(control);pause.setVisibility(View.GONE);stop.setVisibility(View.GONE);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));settingsPanel.addView(progress);progress.setVisibility(View.GONE);
  status=text("正在準備索引…",14,MUTED);settingsPanel.addView(status);device=text("",13,MUTED);settingsPanel.addView(device);space(settingsPanel,18);
  section(settingsPanel,"索引紀錄");stats=text("",14,MUTED);settingsPanel.addView(stats);space(settingsPanel,8);LinearLayout manage=new LinearLayout(this);Button browse=button("檢視索引");clear=button("清除紀錄");clear.setTextColor(0xffa13d3d);addButton(manage,browse);addButton(manage,clear);settingsPanel.addView(manage);space(settingsPanel,14);
  Button help=button("ⓘ 使用說明");settingsPanel.addView(help,new LinearLayout.LayoutParams(-1,dp(48)));help.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("使用說明").setMessage("照片只讀，不修改原始檔。文字索引完全離線。掃描時保持 App 在前景；離開後暫停，重新開啟可繼續。已辨識且未變更的照片會跳過。").setPositiveButton("關閉",null).show());space(settingsPanel,10);TextView version=text("版本 007",12,MUTED);version.setGravity(Gravity.CENTER);settingsPanel.addView(version);
  settings.setOnClickListener(v->settingsDialog());homeUpdate.setOnClickListener(v->{if(!ready||busy)return;if(job!=null&&job.pending()>0&&job.scope.equals("手動選取"))beginResume();else requestAccess();});homeStatus.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);settingsDialog();});
  browse.setOnClickListener(v->{search.setText("");browsing=true;pageSize=100;requestSerial++;resetScroll=true;requestPage();if(settingsPanel.getTag() instanceof AlertDialog)((AlertDialog)settingsPanel.getTag()).dismiss();});
  clearQuery.setOnClickListener(v->{browsing=false;search.setText("");pageSize=100;requestSerial++;resetScroll=true;requestPage();hideKeyboard();});
  search.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){rememberQuery();hideKeyboard();requestPage();return true;}return false;});
  setContentView(root);root.requestFocus();getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  if(Build.VERSION.SDK_INT>=30){root.setOnApplyWindowInsetsListener((view,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets keyboard=insets.getInsets(WindowInsets.Type.ime());view.setPadding(dp(16)+bars.left,dp(14)+bars.top,dp(16)+bars.right,dp(8)+Math.max(bars.bottom,keyboard.bottom));return WindowInsets.CONSUMED;});root.post(()->{if(!root.isAttachedToWindow()||isFinishing()||isDestroyed())return;getWindow().setDecorFitsSystemWindows(false);root.requestApplyInsets();});}
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int c){browsing=false;pageSize=100;adapter.items.clear();adapter.notifyDataSetChanged();matches=0;updateHome();requestSerial++;resetScroll=true;main.removeCallbacks(searchDelay);main.postDelayed(searchDelay,180);}public void afterTextChanged(Editable e){}});
  start.setOnClickListener(v->{if(!ready||busy)return;if(job!=null&&job.pending()>0){if(job.scope.equals("手動選取"))beginResume();else requestAccess();}else requestAccess();});
  pick.setOnClickListener(v->{if(ready&&!busy&&(job==null||job.pending()==0))openPhotoPicker();});
  pause.setOnClickListener(v->{cancel=true;pause.setEnabled(false);status.setText("目前這張完成後暫停…");});
  stop.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("停止本批掃描？").setMessage("已完成的文字索引會保留；未辨識的待處理清單會清除。").setNegativeButton("取消",null).setPositiveButton("停止",(d,w)->{if(busy){discard=true;cancel=true;status.setText("正在停止…");}else{runDiscard();}}).show());
  clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("清除全部辨識紀錄？").setMessage("不會刪除任何原始照片，未完成清單也會清除。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{if(!busy){busy=true;updateButtons();worker.execute(()->{String err="";try{store.clear();}catch(Exception e){err=e.toString();}final String msg=err;main.post(()->{busy=false;problem=msg;job=null;requestPage();updateButtons();});});}}).show());
  more.setOnClickListener(v->{pageSize+=100;requestSerial++;requestPage();});
  list.setOnItemClickListener((a,v,p,id)->{if(p<adapter.items.size()){v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);rememberQuery();hideKeyboard();detail(adapter.items.get(p));}});
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
  final int serial=requestSerial;final String q=search.getText().toString();final int n=IndexStore.norm(q).isEmpty()&&!browsing?0:pageSize;
  reader.execute(()->{IndexStore.Page p=null;String err="";try{p=store.search(q,n);}catch(Exception e){err=e.toString();}final IndexStore.Page page=p;final String message=err;main.post(()->{if(isDestroyed())return;readBusy=false;if(page!=null){job=page.job;indexed=page.ok+page.empty+page.failed;stats.setText("累計 "+indexed+" 筆｜有文字 "+page.ok+"\n無文字 "+page.empty+"｜失敗 "+page.failed);if(serial==requestSerial){int pos=resetScroll?0:list.getFirstVisiblePosition();View child=list.getChildAt(0);int top=resetScroll||child==null?0:child.getTop();resetScroll=false;adapter.items=page.rows;adapter.notifyDataSetChanged();if(pos<adapter.getCount()&&(resetScroll||list.getFirstVisiblePosition()!=pos))list.setSelection(pos);matches=page.matches;count.setText((browsing?"全部索引 ":"符合 ")+page.matches+" 筆");more.setVisibility(page.matches>page.rows.size()?View.VISIBLE:View.GONE);}else readAgain=true;}else if(!message.isEmpty()){problem="索引讀取失敗："+message;}updateStatus();updateButtons();if(readAgain){readAgain=false;requestPage();}});});
 }
 void updateButtons(){boolean pending=job!=null&&job.pending()>0;start.setEnabled(ready&&!busy);start.setText(pending?"繼續掃描":"開始掃描");pick.setEnabled(ready&&!busy&&!pending);clear.setEnabled(ready&&!busy);scope.setEnabled(ready&&!busy&&!pending);limit.setEnabled(ready&&!busy&&!pending);script.setEnabled(ready&&!busy&&!pending);pause.setVisibility(busy&&!planning?View.VISIBLE:View.GONE);pause.setEnabled(busy&&!cancel);stop.setVisibility((busy||pending)?View.VISIBLE:View.GONE);updateHome();}
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
 class Rows extends BaseAdapter{
  ArrayList<IndexStore.Result> items=new ArrayList<>();public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
  public View getView(int pos,View convert,ViewGroup parent){Holder h;if(convert==null){LinearLayout card=vertical();card.setBackground(bg(Color.WHITE,14));card.setClipToOutline(true);h=new Holder();h.img=new SquareImage();h.img.setBackgroundColor(PALE);h.img.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(h.img,new LinearLayout.LayoutParams(-1,-2));h.body=text("",13,INK);h.body.setMaxLines(2);h.body.setEllipsize(TextUtils.TruncateAt.END);h.body.setPadding(dp(9),dp(6),dp(9),dp(6));h.body.setGravity(Gravity.TOP);h.body.setLines(2);card.addView(h.body,new LinearLayout.LayoutParams(-1,-2));card.setTag(h);convert=card;}else h=(Holder)convert.getTag();
   IndexStore.Result e=items.get(pos);h.body.setText(e.error.isEmpty()?(e.text.trim().isEmpty()?"未辨識出文字":e.text.replace('\n',' ')):"辨識失敗");loadThumb(h.img,e.uri);return convert;}
 }
 static class Holder{ImageView img;TextView body;}
 void loadThumb(ImageView view,String key){view.setTag(key);Bitmap b=cache.get(key);if(b!=null){view.setImageBitmap(b);return;}view.setImageResource(android.R.drawable.ic_menu_gallery);thumbs.execute(()->{try{Bitmap image=getContentResolver().loadThumbnail(Uri.parse(key),new Size(dp(180),dp(180)),null);cache.put(key,image);main.post(()->{if(key.equals(view.getTag()))view.setImageBitmap(image);});}catch(Exception ignored){}});}
 void sharePhoto(IndexStore.Result e){try{Uri u=Uri.parse(e.uri);Intent i=new Intent(Intent.ACTION_SEND);String type=getContentResolver().getType(u);i.setType(type==null?"image/*":type);i.putExtra(Intent.EXTRA_STREAM,u);i.setClipData(ClipData.newUri(getContentResolver(),e.name,u));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"分享原始照片"));}catch(Exception ex){Toast.makeText(this,"無法分享這張照片",Toast.LENGTH_SHORT).show();}}
 void detail(IndexStore.Result e){Dialog dlg=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);LinearLayout l=vertical();l.setBackgroundColor(BG);l.setPadding(dp(14),dp(10),dp(14),dp(10));LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);Button back=button("‹");back.setTextSize(26);back.setContentDescription("返回");h.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView title=text(e.name,16,INK);title.setGravity(Gravity.CENTER);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);h.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button share=button("分享");h.addView(share,new LinearLayout.LayoutParams(dp(64),dp(48)));l.addView(h);space(l,10);
  ZoomPhoto img=new ZoomPhoto(this);img.setBackground(bg(0xffedf1ed,14));img.setContentDescription("照片預覽，雙指縮放");l.addView(img,new LinearLayout.LayoutParams(-1,0,1));space(l,10);
  LinearLayout action=new LinearLayout(this);action.setGravity(Gravity.CENTER_VERTICAL);TextView label=text("辨識文字",16,BLUE);label.setTypeface(null,Typeface.BOLD);action.addView(label,new LinearLayout.LayoutParams(0,-2,1));Button copy=button("複製全文");action.addView(copy,new LinearLayout.LayoutParams(dp(106),dp(46)));l.addView(action);
  ScrollView sc=new ScrollView(this);TextView raw=text(e.error.isEmpty()?(e.text.isEmpty()?"未辨識出文字":e.text):e.error,16,INK);raw.setPadding(dp(10),dp(8),dp(10),dp(8));raw.setTextIsSelectable(true);raw.setBackground(bg(Color.WHITE,12));sc.addView(raw);l.addView(sc,new LinearLayout.LayoutParams(-1,dp(getResources().getConfiguration().orientation==2?92:150)));
  back.setOnClickListener(v->dlg.dismiss());share.setOnClickListener(v->sharePhoto(e));copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("OCR",e.text));Toast.makeText(this,"已複製文字",Toast.LENGTH_SHORT).show();});
  dlg.setContentView(l);Window w=dlg.getWindow();if(w!=null){w.setStatusBarColor(BG);w.setNavigationBarColor(BG);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);if(Build.VERSION.SDK_INT>=30){l.setOnApplyWindowInsetsListener((v,insets)->{Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(14)+b.left,dp(10)+b.top,dp(14)+b.right,dp(10)+b.bottom);return WindowInsets.CONSUMED;});}}
  dlg.show();if(w!=null){w.setLayout(-1,-1);if(Build.VERSION.SDK_INT>=30){w.setDecorFitsSystemWindows(false);l.requestApplyInsets();}}
  thumbs.execute(()->{try{Bitmap b=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),Uri.parse(e.uri)),(decoder,info,source)->{int wi=info.getSize().getWidth(),he=info.getSize().getHeight();decoder.setTargetSampleSize(Math.max(1,(Math.max(wi,he)+2999)/3000));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});main.post(()->{if(dlg.isShowing())img.setPhoto(b);});}catch(Exception ex){main.post(()->{if(dlg.isShowing())img.failed=true;img.invalidate();});}});
 }
 @Override protected void onStart(){super.onStart();active=true;if(ready)requestPage();}
 @Override protected void onStop(){active=false;if(busy)cancel=true;super.onStop();}
 @Override protected void onDestroy(){active=false;cancel=true;main.removeCallbacks(searchDelay);worker.shutdown();reader.shutdown();thumbs.shutdown();super.onDestroy();}
}
