package tw.ajo.phototext;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int PICK=101, LOCATION=102, LIMIT=100, TEAL=0xff176b67, INK=0xff243332, BG=0xfff4f7f6;
    private static final String[] LABELS={"日期","時間","星期","地點","經緯度","海拔","器材品牌","器材型號","鏡頭型號","焦距","35mm 等效焦距","光圈","快門速度","ISO","曝光補償","白平衡","閃光燈","曝光模式","原圖解析度","原圖畫素","原始檔名","檔案大小","原始格式","拍攝／編輯軟體","攝影者","版權","原始描述","備註","自訂標題","連續編號"};
    private static final String[] COPY_TAGS={"Make","Model","LensMake","LensModel","LensSpecification","DateTime","DateTimeOriginal","DateTimeDigitized","OffsetTime","OffsetTimeOriginal","OffsetTimeDigitized","SubSecTime","SubSecTimeOriginal","SubSecTimeDigitized","ExposureTime","FNumber","PhotographicSensitivity","ISOSpeedRatings","ExposureBiasValue","ExposureProgram","ExposureMode","WhiteBalance","Flash","FocalLength","FocalLengthIn35mmFilm","MeteringMode","LightSource","Artist","Copyright","ImageDescription","Software","ColorSpace","GPSLatitude","GPSLatitudeRef","GPSLongitude","GPSLongitudeRef","GPSAltitude","GPSAltitudeRef","GPSDateStamp","GPSTimeStamp","GPSDOP","GPSMapDatum","GPSImgDirection","GPSImgDirectionRef","GPSHPositioningError","GPSProcessingMethod"};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<Photo> photos=new ArrayList<>();
    private final ArrayList<Integer> chosen=new ArrayList<>(Arrays.asList(0,1,3,27));
    private final Handler main=new Handler(Looper.getMainLooper());
    private int index=0, generation=0;
    private boolean busy=false, binding=false;
    private LinearLayout root, editor, nav;
    private TextView status, count, fields;
    private Preview preview;
    private EditText date,time,place,note,title,device;
    private Spinner mode,position,separator;
    private SeekBar size,border,alpha;
    private CheckBox shadow,keepExif,stripGps,lookup;
    private int foreground=Color.WHITE, frameColor=Color.WHITE;
    private Button saveOne,saveAll,pickButton,infoButton,prev,next;
    private Bitmap current;
    private Style style=new Style();
    private static class Photo {
        Uri uri; LinkedHashMap<Integer,String> values=new LinkedHashMap<>(); LinkedHashMap<String,String> tags=new LinkedHashMap<>();
        boolean loaded=false; double[] gps; String dateOverride=null,timeOverride=null,placeOverride=null,deviceOverride=null;
        Photo(Uri u){uri=u;}
        String v(int k){return values.getOrDefault(k,"");}
    }
    private static class Style {
        int mode=0,pos=3,color=Color.WHITE,frame=Color.WHITE,font=36,border=15,alpha=100,sep=0;
        boolean shadow=true,keep=true,strip=false,geo=false;
        String note="",title=""; ArrayList<Integer> fields=new ArrayList<>();
        Style copy(){Style s=new Style();s.mode=mode;s.pos=pos;s.color=color;s.frame=frame;s.font=font;s.border=border;s.alpha=alpha;s.sep=sep;s.shadow=shadow;s.keep=keep;s.strip=strip;s.geo=geo;s.note=note;s.title=title;s.fields=new ArrayList<>(fields);return s;}
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(TEAL);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);build();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(INK);return t;}
    private Button button(String label,Runnable f){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setOnClickListener(v->f.run());return b;}
    private void equal(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(0,-2,1f));}
    private void section(LinearLayout l,String s){TextView t=text(s,16);t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(12),0,dp(5));l.addView(t);}
    private EditText input(LinearLayout l,String label,boolean multi){l.addView(text(label,13));EditText e=new EditText(this);e.setTextSize(14);e.setSingleLine(!multi);e.setHint("留白則不顯示");if(multi)e.setMinLines(1);l.addView(e,new LinearLayout.LayoutParams(-1,-2));e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(!binding)changed();}public void afterTextChanged(Editable e){}});return e;}
    private Spinner spinner(LinearLayout l,String label,String[] options){l.addView(text(label,13));Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);s.setAdapter(a);l.addView(s);s.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int p,long id){if(!binding){if(a==mode){if(p==0){foreground=Color.WHITE;}else{foreground=frameColor==Color.BLACK?Color.WHITE:Color.BLACK;}}changed();}}});return s;}
    private SeekBar slider(LinearLayout l,String label,int max,int start){TextView t=text(label+"："+start,13);l.addView(t);SeekBar s=new SeekBar(this);s.setMax(max);s.setProgress(start);l.addView(s);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean u){t.setText(label+"："+p);if(!binding)changed();}});return s;}
    private CheckBox check(LinearLayout l,String label,boolean value){CheckBox c=new CheckBox(this);c.setText(label);c.setTextColor(INK);c.setChecked(value);l.addView(c);c.setOnCheckedChangeListener((b,v)->{if(!binding)changed();});return c;}
    private void build(){
        binding=true;root=column();root.setBackgroundColor(BG);root.setPadding(dp(12),dp(8),dp(12),dp(8));setContentView(root);
        LinearLayout top=row();TextView heading=text("ㄚ喬相片加字",22);heading.setTypeface(null,Typeface.BOLD);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));pickButton=button("選取相片",()->pick());top.addView(pickButton);pickButton.setOnLongClickListener(v->{if(!busy)systemPick();return true;});root.addView(top);
        status=text("001  ·  點選原生相簿，長按使用系統相片選擇器",12);root.addView(status);
        preview=new Preview();root.addView(preview,new LinearLayout.LayoutParams(-1,0,1.1f));
        nav=row();prev=button("‹",()->switchPhoto(-1));next=button("›",()->switchPhoto(1));count=text("未選取相片",14);count.setGravity(Gravity.CENTER);nav.addView(prev);nav.addView(count,new LinearLayout.LayoutParams(0,-2,1));nav.addView(next);infoButton=button("ⓘ",()->showInfo());nav.addView(infoButton);root.addView(nav);
        ScrollView scroll=new ScrollView(this);editor=column();editor.setPadding(dp(2),0,dp(2),dp(10));scroll.addView(editor);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        section(editor,"加字內容");Button select=button("選擇資訊／排列順序",()->fieldDialog());editor.addView(select);fields=text("",12);editor.addView(fields);
        date=input(editor,"拍攝日期（本張）",false);time=input(editor,"拍攝時間（本張）",false);place=input(editor,"拍攝地點（本張）",false);device=input(editor,"器材名稱（本張，可覆寫）",false);
        editor.addView(button("查詢本張拍攝地點",()->lookupCurrent()));note=input(editor,"共用備註",true);title=input(editor,"共用標題",false);
        section(editor,"排版與外框");mode=spinner(editor,"加字模式",new String[]{"相片內部","外部下邊框","外部上邊框","外部四邊框（下方加字）"});position=spinner(editor,"文字位置",new String[]{"左上","上方置中","右上","左下","下方置中","右下"});position.setSelection(3);
        separator=spinner(editor,"排列方式",new String[]{"逐項換行","空格","｜","・","／"});
        size=slider(editor,"字體大小（相對短邊千分比，最小 10）",100,36);border=slider(editor,"邊框厚度（相對短邊百分比）",50,15);alpha=slider(editor,"文字不透明度 %",100,100);
        LinearLayout colors=row();equal(colors,button("文字顏色",()->chooseColor(false)));equal(colors,button("邊框顏色",()->chooseColor(true)));editor.addView(colors);shadow=check(editor,"文字陰影（相片內）",true);
        section(editor,"輸出設定");keepExif=check(editor,"保留可相容的 EXIF 原始拍攝資訊",true);stripGps=check(editor,"移除輸出檔案 EXIF 的 GPS 資訊",false);lookup=check(editor,"批次時使用網路查詢地名（分享座標給地理編碼服務）",false);
        TextView caution=text("另存 JPEG 至 Pictures/ㄚ喬相片加字。移除 GPS 不會移除已畫在照片上的文字。",12);editor.addView(caution);
        LinearLayout bottom=row();saveOne=button("儲存本張",()->export(false));saveAll=button("批次儲存",()->export(true));equal(bottom,saveOne);equal(bottom,saveAll);root.addView(bottom);
        binding=false;changed();refreshControls();
    }
    private void setBusy(boolean b){busy=b;refreshControls();}
    private void refreshControls(){boolean has=!photos.isEmpty();pickButton.setEnabled(!busy);prev.setEnabled(!busy&&has&&index>0);next.setEnabled(!busy&&has&&index<photos.size()-1);infoButton.setEnabled(!busy&&has);saveOne.setEnabled(!busy&&has&&current!=null);saveAll.setEnabled(!busy&&has&&current!=null);setEnabledTree(editor,!busy&&has);count.setText(has?(index+1)+" / "+photos.size()+" 張":"未選取相片");}
    private void setEnabledTree(View v,boolean enabled){v.setEnabled(enabled);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)setEnabledTree(g.getChildAt(i),enabled);}}
    private void pick(){
        if(busy)return;
        Intent nativePick=new Intent(Intent.ACTION_PICK);nativePick.setType("image/*");nativePick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);nativePick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        List<ResolveInfo> candidates=getPackageManager().queryIntentActivities(nativePick,PackageManager.MATCH_DEFAULT_ONLY);
        for(ResolveInfo r:candidates){String p=r.activityInfo.packageName.toLowerCase(Locale.ROOT);if((p.contains("oppo")||p.contains("coloros")||p.contains("oneplus")||p.contains("oplus"))&&(p.contains("gallery")||p.contains("album")||p.contains("photo"))){try{nativePick.setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name));startActivityForResult(nativePick,PICK);return;}catch(Exception ignored){}}}
        systemPick();
    }
    private void systemPick(){try{if(Build.VERSION.SDK_INT>=33){Intent i=new Intent(MediaStore.ACTION_PICK_IMAGES);i.setType("image/*");i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,Math.min(LIMIT,MediaStore.getPickImagesMaxLimit()));i.putExtra("android.provider.extra.REQUEST_LOCATION_METADATA_ACCESS",true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK);}else{Intent i=new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK);}}catch(Exception e){message("本機未提供可用的相片選擇器。未改用檔案管理員。");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK||result!=RESULT_OK||data==null)return;LinkedHashSet<Uri> uris=new LinkedHashSet<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++){Uri u=data.getClipData().getItemAt(i).getUri();if(u!=null)uris.add(u);}if(data.getData()!=null)uris.add(data.getData());if(uris.isEmpty())return;photos.clear();for(Uri u:uris){if(photos.size()>=LIMIT)break;photos.add(new Photo(u));}index=0;if(uris.size()>LIMIT)message("001 版每批最多 100 張");loadCurrent();}
    private void switchPhoto(int delta){if(busy)return;changed();index+=delta;loadCurrent();}
    private Photo active(){return photos.isEmpty()?null:photos.get(index);}
    private void loadCurrent(){
        final Photo p=active();if(p==null)return;final int ticket=++generation;setBusy(true);status.setText("讀取相片資訊…");if(current!=null){current.recycle();current=null;}preview.invalidate();
        worker.execute(()->{Bitmap b=null;String error=null;try{read(p);b=decode(p.uri,1800);}catch(Exception|OutOfMemoryError e){error=e.getClass().getSimpleName()+": "+e.getMessage();}final Bitmap image=b;final String fail=error;main.post(()->{if(isFinishing()||ticket!=generation){if(image!=null)image.recycle();return;}current=image;binding=true;date.setText(p.dateOverride==null?p.v(0):p.dateOverride);time.setText(p.timeOverride==null?p.v(1):p.timeOverride);place.setText(p.placeOverride==null?p.v(3):p.placeOverride);device.setText(p.deviceOverride==null?p.v(7):p.deviceOverride);binding=false;status.setText(fail==null?(p.gps==null?"已讀取可用資訊 · 未取得 GPS":"已讀取可用資訊 · 含 GPS"):("讀取失敗："+fail));setBusy(false);preview.invalidate();});});
    }
    private Uri metadataUri(Uri uri){if(checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)==PackageManager.PERMISSION_GRANTED&&"media".equals(uri.getAuthority())){try{return MediaStore.setRequireOriginal(uri);}catch(Exception ignored){}}return uri;}
    private ExifInterface exif(Uri uri)throws IOException{try(InputStream in=getContentResolver().openInputStream(metadataUri(uri))){if(in==null)throw new IOException("無法開啟相片");return new ExifInterface(in);}catch(Exception e){try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("無法開啟相片");return new ExifInterface(in);}}}
    private static void put(Photo p,int n,String s){if(s!=null&&!s.trim().isEmpty())p.values.put(n,s.trim());}
    private static String format(double x){return new DecimalFormat("0.##",DecimalFormatSymbols.getInstance(Locale.US)).format(x);}
    private static String tag(ExifInterface e,String k){String s=e.getAttribute(k);return s==null?"":s;}
    private static String num(ExifInterface e,String key,String prefix,String suffix){double d=e.getAttributeDouble(key,Double.NaN);return Double.isNaN(d)?"":prefix+format(d)+suffix;}
    private void read(Photo p){if(p.loaded)return;String name="",mime=getContentResolver().getType(p.uri);long bytes=-1;
        try(Cursor c=getContentResolver().query(p.uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){name=c.getString(0);if(!c.isNull(1))bytes=c.getLong(1);}}catch(Exception ignored){}put(p,20,name);if(bytes>=0)put(p,21,format(bytes/1048576d)+" MB");put(p,22,mime==null?"":mime.replace("image/","").toUpperCase(Locale.ROOT));
        try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(p.uri)){BitmapFactory.decodeStream(in,null,o);}if(o.outWidth>0&&o.outHeight>0){put(p,18,o.outWidth+" × "+o.outHeight);put(p,19,format((long)o.outWidth*o.outHeight/1000000d)+" MP");}}catch(Exception ignored){}
        try{ExifInterface e=exif(p.uri);for(String k:COPY_TAGS){String v=e.getAttribute(k);if(v!=null)p.tags.put(k,v);}
            String d=tag(e,"DateTimeOriginal");if(d.length()>=19){put(p,0,d.substring(0,10).replace(':','/'));put(p,1,d.substring(11,19));try{SimpleDateFormat df=new SimpleDateFormat("yyyy:MM:dd",Locale.US);df.setLenient(false);Date day=df.parse(d.substring(0,10));put(p,2,new SimpleDateFormat("EEEE",Locale.TRADITIONAL_CHINESE).format(day));}catch(Exception ignored){}}
            p.gps=e.getLatLong();if(p.gps!=null){put(p,4,String.format(Locale.US,"%.5f, %.5f",p.gps[0],p.gps[1]));double h=e.getAltitude(Double.NaN);if(!Double.isNaN(h))put(p,5,format(h)+" m");}
            put(p,6,tag(e,"Make"));put(p,7,tag(e,"Model"));put(p,8,tag(e,"LensModel"));put(p,9,num(e,"FocalLength",""," mm"));put(p,10,num(e,"FocalLengthIn35mmFilm",""," mm"));put(p,11,num(e,"FNumber","f/",""));double shutter=e.getAttributeDouble("ExposureTime",Double.NaN);if(shutter>0)put(p,12,shutter<1?"1/"+format(1/shutter)+" s":format(shutter)+" s");
            String iso=tag(e,"PhotographicSensitivity");if(iso.isEmpty())iso=tag(e,"ISOSpeedRatings");if(!iso.isEmpty())put(p,13,"ISO "+iso);put(p,14,num(e,"ExposureBiasValue",""," EV"));
            String white=tag(e,"WhiteBalance");if(!white.isEmpty())put(p,15,white.equals("0")?"自動":white.equals("1")?"手動":white);
            int flash=e.getAttributeInt("Flash",-1);if(flash>=0)put(p,16,(flash&1)==1?"閃光燈已閃光":"閃光燈未閃光");String exp=tag(e,"ExposureMode");if(!exp.isEmpty())put(p,17,exp.equals("0")?"自動曝光":exp.equals("1")?"手動曝光":exp.equals("2")?"自動包圍曝光":exp);
            put(p,23,tag(e,"Software"));put(p,24,tag(e,"Artist"));put(p,25,tag(e,"Copyright"));put(p,26,tag(e,"ImageDescription"));
        }catch(Exception ignored){}p.loaded=true;
    }
    private Bitmap decode(Uri uri,int max)throws IOException{return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,src)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);if(max==0&&(long)info.getSize().getWidth()*info.getSize().getHeight()*4>Runtime.getRuntime().maxMemory()*.55)throw new IllegalArgumentException("原始相片超過可用記憶體，不縮小輸出");if(max>0){int w=info.getSize().getWidth(),h=info.getSize().getHeight();float f=Math.min(1f,max/(float)Math.max(w,h));if(f<1)decoder.setTargetSize(Math.max(1,Math.round(w*f)),Math.max(1,Math.round(h*f)));}});}
    private void changed(){if(binding||date==null||lookup==null)return;Photo p=active();if(p!=null&&p.loaded){p.dateOverride=override(date,p.v(0));p.timeOverride=override(time,p.v(1));p.placeOverride=override(place,p.v(3));p.deviceOverride=override(device,p.v(7));}style.mode=Math.max(0,mode.getSelectedItemPosition());style.pos=Math.max(0,position.getSelectedItemPosition());style.sep=Math.max(0,separator.getSelectedItemPosition());style.font=Math.max(10,size.getProgress());style.border=border.getProgress();style.alpha=alpha.getProgress();style.color=foreground;style.frame=frameColor;style.shadow=shadow.isChecked();style.keep=keepExif.isChecked();style.strip=stripGps.isChecked();style.geo=lookup.isChecked();style.note=note.getText().toString();style.title=title.getText().toString();style.fields=new ArrayList<>(chosen);ArrayList<String> names=new ArrayList<>();for(int f:chosen)names.add(LABELS[f]);fields.setText(String.join(" · ",names));preview.invalidate();}
    private String override(EditText e,String original){String v=e.getText().toString();return v.equals(original)?null:v;}
    private void fieldDialog(){boolean[] ticks=new boolean[LABELS.length];for(int k:chosen)ticks[k]=true;new AlertDialog.Builder(this).setTitle("選擇加字資訊").setMultiChoiceItems(LABELS,ticks,(d,i,b)->ticks[i]=b).setNegativeButton("取消",null).setPositiveButton("下一步：排列",(d,w)->{chosen.removeIf(k->!ticks[k]);for(int i=0;i<ticks.length;i++)if(ticks[i]&&!chosen.contains(i))chosen.add(i);changed();orderDialog();}).show();}
    private void orderDialog(){LinearLayout box=column();ScrollView sc=new ScrollView(this);sc.addView(box);Runnable[] refresh=new Runnable[1];refresh[0]=()->{box.removeAllViews();for(int i=0;i<chosen.size();i++){final int j=i;LinearLayout r=row();r.addView(text(LABELS[chosen.get(i)],15),new LinearLayout.LayoutParams(0,-2,1));r.addView(button("↑",()->{if(j>0){Collections.swap(chosen,j,j-1);changed();refresh[0].run();}}));r.addView(button("↓",()->{if(j+1<chosen.size()){Collections.swap(chosen,j,j+1);changed();refresh[0].run();}}));box.addView(r);}};refresh[0].run();new AlertDialog.Builder(this).setTitle("排列顯示順序").setView(sc).setPositiveButton("完成",null).show();}
    private void chooseColor(boolean frame){String[] names={"白色","黑色","米白","金色","深綠","藍色","紅色","自訂 HEX"};int[] colors={Color.WHITE,Color.BLACK,0xfffaf4e8,0xffd5a344,TEAL,0xff387ec8,0xffb6404d};new AlertDialog.Builder(this).setTitle(frame?"邊框顏色":"文字顏色").setItems(names,(d,i)->{if(i<colors.length)applyColor(frame,colors[i]);else{EditText e=new EditText(this);e.setHint("#FFFFFF");new AlertDialog.Builder(this).setTitle("輸入六位 HEX 色碼").setView(e).setNegativeButton("取消",null).setPositiveButton("套用",(a,b)->{try{String s=e.getText().toString().trim();if(!s.startsWith("#"))s="#"+s;if(s.length()!=7)throw new IllegalArgumentException();applyColor(frame,Color.parseColor(s));}catch(Exception ex){message("色碼格式錯誤");}}).show();}}).show();}
    private void applyColor(boolean frame,int color){if(frame){frameColor=color;if(style.mode!=0&&foreground==color)foreground=color==Color.WHITE?Color.BLACK:Color.WHITE;}else foreground=color;changed();}
    private void showInfo(){Photo p=active();if(p==null)return;StringBuilder b=new StringBuilder();for(int i=0;i<27;i++)if(!p.v(i).isEmpty())b.append(LABELS[i]).append("：").append(p.v(i)).append('\n');b.append("\nEXIF 原始欄位（已讀取）\n");for(Map.Entry<String,String> e:p.tags.entrySet())b.append(e.getKey()).append("：").append(e.getValue()).append('\n');new AlertDialog.Builder(this).setTitle("原始相片資訊").setMessage(b.toString()).setPositiveButton("關閉",null).setNeutralButton("複製",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("相片資訊",b));}).show();}
    private String geocode(Photo p){if(p.gps==null||!Geocoder.isPresent())return "";try{List<Address> list=new Geocoder(this,Locale.TRADITIONAL_CHINESE).getFromLocation(p.gps[0],p.gps[1],1);if(list!=null&&!list.isEmpty()){Address a=list.get(0);LinkedHashSet<String> parts=new LinkedHashSet<>();for(String s:new String[]{a.getCountryName(),a.getAdminArea(),a.getLocality(),a.getSubLocality()})if(s!=null&&!s.isEmpty())parts.add(s);return String.join("・",parts);}}catch(Exception ignored){}return "";}
    private void lookupCurrent(){Photo p=active();if(p==null||busy)return;if(p.gps==null){new AlertDialog.Builder(this).setTitle("未取得 GPS").setMessage("原圖可能沒有 GPS，或相簿未提供位置中繼資料。可直接輸入地點；也可以改用系統相片選擇器。 ").setPositiveButton("關閉",null).setNeutralButton("重新選取相片",(d,w)->{if(checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_MEDIA_LOCATION},LOCATION);else systemPick();}).show();return;}new AlertDialog.Builder(this).setTitle("查詢拍攝地點").setMessage("將原圖座標交給 Android 地理編碼服務查詢地名，可能使用網路。是否繼續？").setNegativeButton("取消",null).setPositiveButton("查詢",(d,w)->{setBusy(true);worker.execute(()->{String s=geocode(p);main.post(()->{setBusy(false);if(!s.isEmpty()){p.values.put(3,s);place.setText(s);changed();}else message("無法轉換地點，請手動填寫。 ");});});}).show();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==LOCATION)systemPick();}
    private String fieldValue(Photo p,int k,Style s,int ordinal){switch(k){case 0:return p.dateOverride==null?p.v(0):p.dateOverride;case 1:return p.timeOverride==null?p.v(1):p.timeOverride;case 3:return p.placeOverride==null?p.v(3):p.placeOverride;case 7:return p.deviceOverride==null?p.v(7):p.deviceOverride;case 27:return s.note;case 28:return s.title;case 29:return String.format(Locale.US,"%03d",ordinal+1);default:return p.v(k);}}
    private String content(Photo p,Style s,int ordinal){String[] separators={"\n","  ","  |  "," ・ "," / "};ArrayList<String> out=new ArrayList<>();for(int k:s.fields){String v=fieldValue(p,k,s,ordinal);if(v!=null&&!v.trim().isEmpty())out.add(v.trim());}return String.join(separators[Math.max(0,Math.min(s.sep,4))],out);}
    private static class Layout {
        int w,h,left,top,textX,textY; android.text.StaticLayout text; android.text.TextPaint paint;
    }
    private Layout layout(Bitmap b,Photo p,Style s,int ordinal){
        Layout g=new Layout();int w=b.getWidth(),h=b.getHeight(),shorter=Math.min(w,h);int pad=Math.max(1,Math.round(shorter*.025f));int frame=Math.max(1,Math.round(shorter*s.border/100f));int margin=s.mode==3?frame:0;int width=w+margin*2;
        boolean outer=s.mode!=0;String contents=content(p,s,ordinal);android.text.TextPaint tp=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);tp.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));tp.setColor(s.color);tp.setAlpha(Math.round(255*s.alpha/100f));float fs=Math.max(2,shorter*s.font/1000f);tp.setTextSize(fs);if(s.shadow&&!outer)tp.setShadowLayer(Math.max(1,fs*.09f),0,fs*.045f,0xb0000000);
        int textWidth=Math.max(1,width-2*pad);int alignment=s.pos%3;android.text.Layout.Alignment align=alignment==0?android.text.Layout.Alignment.ALIGN_NORMAL:alignment==1?android.text.Layout.Alignment.ALIGN_CENTER:android.text.Layout.Alignment.ALIGN_OPPOSITE;
        android.text.StaticLayout lines=makeLayout(contents,tp,textWidth,align);if(!outer){while(lines.getHeight()>h-2*pad&&tp.getTextSize()>2){tp.setTextSize(Math.max(2,tp.getTextSize()*.85f));lines=makeLayout(contents,tp,textWidth,align);}}
        int bottom=0,top=0;if(s.mode==1)bottom=Math.max(frame,contents.isEmpty()?0:lines.getHeight()+2*pad);if(s.mode==2)top=Math.max(frame,contents.isEmpty()?0:lines.getHeight()+2*pad);if(s.mode==3){top=frame;bottom=Math.max(frame,contents.isEmpty()?0:lines.getHeight()+2*pad);}
        g.w=width;g.h=h+top+bottom;g.left=margin;g.top=top;g.textX=pad;g.text=lines;g.paint=tp;
        if(s.mode==2)g.textY=Math.max(0,(top-lines.getHeight())/2);else if(s.mode==1||s.mode==3)g.textY=top+h+Math.max(0,(bottom-lines.getHeight())/2);else g.textY=s.pos<3?pad:Math.max(pad,h-pad-lines.getHeight());return g;
    }
    private android.text.StaticLayout makeLayout(String text,android.text.TextPaint paint,int width,android.text.Layout.Alignment align){return android.text.StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setAlignment(align).setTextDirection(android.text.TextDirectionHeuristics.LTR).setIncludePad(false).setLineSpacing(0,1.12f).build();}
    private void draw(Canvas c,Bitmap b,Layout g,Style s){Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);paint.setColor(s.mode==0?Color.BLACK:s.frame);c.drawRect(0,0,g.w,g.h,paint);c.drawBitmap(b,g.left,g.top,paint);c.save();c.clipRect(0,0,g.w,g.h);c.translate(g.textX,g.textY);g.text.draw(c);c.restore();}
    private class Preview extends View {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Preview(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(0xffe0e7e5);Photo item=active();if(current==null||item==null){p.setColor(0xff647673);p.setTextSize(dp(16));p.setTextAlign(Paint.Align.CENTER);canvas.drawText(busy?"讀取中…":"請先選取相片",getWidth()/2f,getHeight()/2f,p);return;}Layout g=MainActivity.this.layout(current,item,style,index);float availableW=Math.max(1,getWidth()-dp(12)),availableH=Math.max(1,getHeight()-dp(12));float factor=Math.min(availableW/g.w,availableH/g.h);canvas.save();canvas.translate((getWidth()-factor*g.w)/2,(getHeight()-factor*g.h)/2);canvas.scale(factor,factor);MainActivity.this.draw(canvas,current,g,style);canvas.restore();}
    }
    private void export(boolean all){if(busy||current==null)return;changed();Style config=style.copy();int start=all?0:index,end=all?photos.size():index+1;new AlertDialog.Builder(this).setTitle(all?"批次另存 "+(end-start)+" 張":"另存本張相片").setMessage("會使用原始解析度重新輸出 JPEG（品質 98），不會覆蓋原圖。外框會增加畫布尺寸。"+(config.geo?"\n會使用網路服務查詢尚未填寫的拍攝地名。":"")).setNegativeButton("取消",null).setPositiveButton("開始",(d,w)->doExport(config,start,end)).show();}
    private void doExport(Style cfg,int start,int end){setBusy(true);ArrayList<Photo> jobs=new ArrayList<>(photos.subList(start,end));worker.execute(()->{int ok=0,metaFailures=0;ArrayList<String> errors=new ArrayList<>();for(int j=0;j<jobs.size();j++){Photo p=jobs.get(j);int ordinal=start+j;final int progress=j+1;main.post(()->status.setText("輸出中 "+progress+" / "+jobs.size()));try{read(p);if(cfg.geo&&cfg.fields.contains(3)&&p.placeOverride==null&&p.v(3).isEmpty()){String name=geocode(p);if(!name.isEmpty())p.values.put(3,name);}boolean metadata=savePhoto(p,cfg,ordinal);ok++;if(!metadata)metaFailures++;}catch(Exception|OutOfMemoryError ex){errors.add((ordinal+1)+"："+ex.getClass().getSimpleName()+" "+String.valueOf(ex.getMessage()));}}final int success=ok,meta=metaFailures;main.post(()->{if(isFinishing())return;setBusy(false);status.setText("已儲存 "+success+" / "+jobs.size()+" 張");Photo p=active();if(p!=null&&p.placeOverride==null){binding=true;place.setText(p.v(3));binding=false;}preview.invalidate();String result="已儲存 "+success+" 張至相簿「ㄚ喬相片加字」。";if(meta>0)result+="\n其中 "+meta+" 張有部分或全部 EXIF 未能保留，已儲存加字相片。";if(!errors.isEmpty())result+="\n\n未完成：\n"+String.join("\n",errors);new AlertDialog.Builder(this).setTitle("輸出結果").setMessage(result).setPositiveButton("關閉",null).show();});});}
    private boolean savePhoto(Photo p,Style s,int ordinal)throws Exception{
        Bitmap b=null,out=null;File temp=null;Uri dest=null;boolean complete=false,metadataOk=true;
        try{b=decode(p.uri,0);Layout g=layout(b,p,s,ordinal);long bytes=(long)g.w*g.h*4;long max=Runtime.getRuntime().maxMemory();if(bytes>max*.70)throw new IOException("原圖加邊框後超過可用記憶體；未降低解析度");out=Bitmap.createBitmap(g.w,g.h,Bitmap.Config.ARGB_8888);draw(new Canvas(out),b,g,s);b.recycle();b=null;temp=File.createTempFile("ajo_photo_",".jpg",getCacheDir());try(FileOutputStream os=new FileOutputStream(temp)){if(!out.compress(Bitmap.CompressFormat.JPEG,98,os))throw new IOException("JPEG 編碼失敗");}out.recycle();out=null;
            if(s.keep){try{ExifInterface e=new ExifInterface(temp.getAbsolutePath());for(Map.Entry<String,String> tag:p.tags.entrySet()){String k=tag.getKey();if((s.strip&&k.startsWith("GPS"))||k.equals("ColorSpace"))continue;try{e.setAttribute(k,tag.getValue());}catch(Exception ignored){metadataOk=false;}}e.setAttribute("Orientation","1");e.setAttribute("ImageWidth",String.valueOf(g.w));e.setAttribute("ImageLength",String.valueOf(g.h));e.setAttribute("PixelXDimension",String.valueOf(g.w));e.setAttribute("PixelYDimension",String.valueOf(g.h));e.saveAttributes();}catch(Exception e){metadataOk=false;}}
            ContentValues v=new ContentValues();String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());v.put(MediaStore.Images.Media.DISPLAY_NAME,"AJO_"+stamp+"_"+String.format(Locale.US,"%03d",ordinal+1)+".jpg");v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ㄚ喬相片加字");v.put(MediaStore.Images.Media.IS_PENDING,1);dest=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(dest==null)throw new IOException("無法建立相簿檔案");try(InputStream in=new FileInputStream(temp);OutputStream os=getContentResolver().openOutputStream(dest)){if(os==null)throw new IOException("無法寫入相片");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)os.write(buffer,0,n);}v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);if(getContentResolver().update(dest,v,null,null)<1)throw new IOException("無法完成相簿儲存");complete=true;return metadataOk;
        }finally{if(!complete&&dest!=null)try{getContentResolver().delete(dest,null,null);}catch(Exception ignored){}if(temp!=null)temp.delete();if(b!=null)b.recycle();if(out!=null)out.recycle();}}
    private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){generation++;worker.shutdown();if(current!=null){current.recycle();current=null;}super.onDestroy();}
}
