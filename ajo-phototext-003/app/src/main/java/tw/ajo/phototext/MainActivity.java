package tw.ajo.phototext;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.graphics.Insets;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Size;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.Normalizer;
import java.io.*;

public class MainActivity extends Activity {
 final int BLUE=0xff075b75, BG=0xfff1f6f8, INK=0xff142c36, MUTED=0xff526975;
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 final ArrayList<Entry> entries=new ArrayList<>();
 LinearLayout results; TextView status,count; EditText search; Spinner script; Button pick,clear,stop;
 ProgressBar progress; volatile boolean cancel; boolean busy=false; int total=0,done=0; long batchStarted=0,batchRecognitionMs=0;
 static final int PICK=20;
 static final int PICK_DOCUMENT=21;
 static final String[] MODES={"繁中＋英文（預設）","英文＋數字"};
 static class Entry {
  String uri,name,mode,text,error; long ms;
  Entry(String u,String n,String m,String t,String e,long s){uri=u;name=n;mode=m;text=t;error=e;ms=s;}
  JSONObject json() throws JSONException {return new JSONObject().put("uri",uri).put("name",name).put("mode",mode).put("text",text).put("error",error).put("ms",ms);}
 }
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
 TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
 GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);return b;}
 void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);getWindow().getInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
  load();
  LinearLayout root=vertical();root.setBackgroundColor(BG);root.setPadding(dp(16),dp(14),dp(16),dp(8));
  TextView heading=text("ㄚ喬文字搜尋測試",23,BLUE);heading.setTypeface(null,Typeface.BOLD);root.addView(heading);
  root.addView(text("004  •  離線 OCR 辨識",13,MUTED));space(root,8);
  script=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,MODES);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);script.setAdapter(a);root.addView(script,new LinearLayout.LayoutParams(-1,dp(44)));
  LinearLayout bar=new LinearLayout(this);pick=button("＋ 選擇照片");pick.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));pick.setTextColor(Color.WHITE);clear=button("清除紀錄");stop=button("停止");
  bar.addView(pick,new LinearLayout.LayoutParams(0,dp(48),1));bar.addView(clear);bar.addView(stop);root.addView(bar);stop.setVisibility(View.GONE);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setVisibility(View.GONE);root.addView(progress);
  status=text("只辨識妳選取的照片，不修改原圖。",13,MUTED);root.addView(status);space(root,8);
  search=new EditText(this);search.setSingleLine(true);search.setTextSize(17);search.setHint("搜尋照片中的文字");search.setPadding(dp(12),dp(8),dp(12),dp(8));search.setBackground(bg(Color.WHITE,12));root.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
  count=text("",13,MUTED);root.addView(count);space(root,4);
  ScrollView sc=new ScrollView(this);results=vertical();sc.addView(results);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  if(Build.VERSION.SDK_INT>=30){root.setOnApplyWindowInsetsListener((view,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets keyboard=insets.getInsets(WindowInsets.Type.ime());view.setPadding(dp(16)+bars.left,dp(14)+bars.top,dp(16)+bars.right,dp(8)+Math.max(bars.bottom,keyboard.bottom));return insets;});root.requestApplyInsets();}
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int c){render();}public void afterTextChanged(Editable e){}});
  pick.setOnClickListener(v->openPhotoPicker());
  clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("清除全部辨識紀錄？").setMessage("不會刪除任何原始照片。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{for(Entry e:entries){try{getContentResolver().releasePersistableUriPermission(Uri.parse(e.uri),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}entries.clear();save();render();}).show());
  stop.setOnClickListener(v->{cancel=true;status.setText("目前這張完成後停止");});render();
 }
 void openPhotoPicker(){
  if(Build.VERSION.SDK_INT>=33){try{Intent i=new Intent(MediaStore.ACTION_PICK_IMAGES);i.setType("image/*");i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,MediaStore.getPickImagesMaxLimit());i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK);return;}catch(ActivityNotFoundException|IllegalArgumentException ignored){}}
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_DOCUMENT);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);if((request!=PICK&&request!=PICK_DOCUMENT)||result!=RESULT_OK||data==null||busy)return;
  LinkedHashSet<Uri> uris=new LinkedHashSet<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
  int mode=script.getSelectedItemPosition();ArrayList<Uri> work=new ArrayList<>();for(Uri u:uris){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}work.add(u);}if(work.isEmpty())return;
  busy=true;cancel=false;done=0;batchRecognitionMs=0;batchStarted=SystemClock.elapsedRealtime();total=work.size();pick.setEnabled(false);clear.setEnabled(false);script.setEnabled(false);stop.setVisibility(View.VISIBLE);progress.setVisibility(View.VISIBLE);progress.setMax(total);progress.setProgress(0);status.setText("辨識中 0 / "+total);
  worker.execute(()->scan(work,mode));
 }
 TextRecognizer recognizer(int mode){switch(mode){case 1:return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);default:return TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());}}
 String name(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "照片";}
 void scan(ArrayList<Uri> work,int mode){
  TextRecognizer r=null;try{r=recognizer(mode);for(Uri u:work){if(cancel)break;String n=name(u),t="",err="";long start=SystemClock.elapsedRealtime();try{InputImage im=InputImage.fromFilePath(this,u);t=Tasks.await(r.process(im),90,TimeUnit.SECONDS).getText();}catch(Exception ex){err=ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage());}long ms=SystemClock.elapsedRealtime()-start;Entry e=new Entry(u.toString(),n,MODES[mode],t,err,ms);runOnUiThread(()->{entries.removeIf(x->x.uri.equals(e.uri)&&x.mode.equals(e.mode));entries.add(0,e);done++;batchRecognitionMs+=e.ms;progress.setProgress(done);status.setText("辨識中 "+done+" / "+total+" 張\n"+batchTiming(SystemClock.elapsedRealtime()-batchStarted));save();render();});}}catch(Exception ex){final String msg=String.valueOf(ex.getMessage());runOnUiThread(()->new AlertDialog.Builder(this).setMessage("模型初始化失敗："+msg).setPositiveButton("確定",null).show());}finally{if(r!=null)r.close();runOnUiThread(()->{busy=false;pick.setEnabled(true);clear.setEnabled(true);script.setEnabled(true);stop.setVisibility(View.GONE);progress.setVisibility(View.GONE);status.setText((cancel?"已停止":"本批結束")+" "+done+" / "+total+" 張\n"+batchTiming(SystemClock.elapsedRealtime()-batchStarted));});}
 }
 String batchTiming(long elapsed){return String.format(Locale.TAIWAN,"總耗時 %.2f 秒｜平均辨識 %.2f 秒/張",elapsed/1000.0,done==0?0:batchRecognitionMs/(1000.0*done));}
 static String normalized(String x){return Normalizer.normalize(x,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+","");}
 void render(){
  String q=normalized(search.getText().toString());ArrayList<Entry> shown=new ArrayList<>();int ok=0,errors=0,empty=0;for(Entry e:entries){if(!e.error.isEmpty())errors++;else if(e.text.trim().isEmpty())empty++;else ok++;if(q.isEmpty()||normalized(e.text).contains(q))shown.add(e);}
  count.setText("符合 "+shown.size()+" 筆 ｜ 有文字 "+ok+" ｜ 無文字 "+empty+" ｜ 失敗 "+errors);results.removeAllViews();
  if(shown.isEmpty()){TextView hint=text(entries.isEmpty()?"先選幾張手機截圖開始測試。":"沒有符合的文字。",16,MUTED);hint.setPadding(dp(8),dp(24),dp(8),dp(16));results.addView(hint);return;}
  for(Entry e:shown){LinearLayout card=vertical();card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(bg(Color.WHITE,12));
   LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(thumb,new LinearLayout.LayoutParams(dp(76),dp(76)));
   try{if(Build.VERSION.SDK_INT>=29)thumb.setImageBitmap(getContentResolver().loadThumbnail(Uri.parse(e.uri),new Size(dp(96),dp(96)),null));else thumb.setImageResource(android.R.drawable.ic_menu_gallery);}catch(Exception ex){thumb.setImageResource(android.R.drawable.ic_menu_gallery);}
   LinearLayout info=vertical();info.setPadding(dp(12),0,0,0);TextView title=text(e.name,15,INK);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(2);info.addView(title);
   info.addView(text(e.mode+" · "+String.format(Locale.TAIWAN,"%.2f 秒",e.ms/1000.0),12,MUTED));TextView preview=text(e.error.isEmpty()?(e.text.trim().isEmpty()?"未辨識出文字":e.text):"辨識失敗："+e.error,14,INK);preview.setMaxLines(3);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(preview);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);card.setOnClickListener(v->detail(e));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);results.addView(card,p);
  }
 }
 void detail(Entry e){ScrollView sc=new ScrollView(this);LinearLayout l=vertical();l.setPadding(dp(18),dp(8),dp(18),dp(12));ImageView img=new ImageView(this);img.setAdjustViewBounds(true);img.setMaxHeight(dp(360));img.setScaleType(ImageView.ScaleType.FIT_CENTER);try{if(Build.VERSION.SDK_INT>=28){Bitmap b=android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(getContentResolver(),Uri.parse(e.uri)),(decoder,info,source)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();int sample=Math.max(1,(Math.max(w,h)+1499)/1500);decoder.setTargetSampleSize(sample);});img.setImageBitmap(b);}else img.setImageURI(Uri.parse(e.uri));}catch(Exception ex){img.setImageResource(android.R.drawable.ic_menu_gallery);}l.addView(img,new LinearLayout.LayoutParams(-1,-2));l.addView(text(e.mode+"　"+String.format(Locale.TAIWAN,"%.2f 秒",e.ms/1000.0),13,MUTED));TextView raw=text(e.error.isEmpty()?(e.text.isEmpty()?"未辨識出文字":e.text):e.error,17,INK);raw.setTextIsSelectable(true);l.addView(raw);sc.addView(l);new AlertDialog.Builder(this).setTitle(e.name).setView(sc).setNegativeButton("關閉",null).setPositiveButton("複製文字",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("OCR",e.text));}).show();}
 void load(){try(FileInputStream in=openFileInput("index.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);JSONArray a=new JSONArray(out.toString("UTF-8"));for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);entries.add(new Entry(j.getString("uri"),j.getString("name"),j.getString("mode"),j.getString("text"),j.getString("error"),j.getLong("ms")));}}catch(Exception ignored){}}
 void save(){android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"index.json"));FileOutputStream out=null;try{JSONArray a=new JSONArray();for(Entry e:entries)a.put(e.json());out=file.startWrite();out.write(a.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(Exception ex){if(out!=null)file.failWrite(out);Toast.makeText(this,"紀錄儲存失敗",Toast.LENGTH_SHORT).show();}}
 @Override protected void onDestroy(){cancel=true;worker.shutdown();super.onDestroy();}
}
