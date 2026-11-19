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
    private static final int PICK=101, LOCATION=102, LIMIT=100, TEAL=0xff176b67, INK=0xff263c38, BG=0xfff6f2e9, PAPER=0xfffffcf7, LINE=0xffe5ded0, GOLD=0xffac874c, MINT=0xffe4f0e9, MUTED=0xff69746d;
    private static final String[] LABELS={"日期","時間","星期","地點","經緯度","海拔","器材品牌","器材型號","鏡頭型號","焦距","35mm 等效焦距","光圈","快門速度","ISO","曝光補償","白平衡","閃光燈","曝光模式","原圖解析度","原圖畫素","原始檔名","檔案大小","原始格式","拍攝／編輯軟體","攝影者","版權","原始描述","備註","自訂標題","連續編號"};
    private static final String[] COPY_TAGS={"Make","Model","LensMake","LensModel","LensSpecification","DateTime","DateTimeOriginal","DateTimeDigitized","OffsetTime","OffsetTimeOriginal","OffsetTimeDigitized","SubSecTime","SubSecTimeOriginal","SubSecTimeDigitized","ExposureTime","FNumber","PhotographicSensitivity","ISOSpeedRatings","ExposureBiasValue","ExposureProgram","ExposureMode","WhiteBalance","Flash","FocalLength","FocalLengthIn35mmFilm","MeteringMode","LightSource","Artist","Copyright","ImageDescription","Software","ColorSpace","GPSLatitude","GPSLatitudeRef","GPSLongitude","GPSLongitudeRef","GPSAltitude","GPSAltitudeRef","GPSDateStamp","GPSTimeStamp","GPSDOP","GPSMapDatum","GPSImgDirection","GPSImgDirectionRef","GPSHPositioningError","GPSProcessingMethod"};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<Photo> photos=new ArrayList<>();
    private final ArrayList<Integer> chosen=new ArrayList<>(Arrays.asList(0,1,3,27));
    private final Handler main=new Handler(Looper.getMainLooper());
    private int index=0, generation=0;
    private boolean busy=false, binding=false;
    private LinearLayout root, editor, nav, nudges, advanced; private TextView metaBadge; private boolean buttonHaptic=true;
    private TextView status, count, fields;
    private LinearLayout editableFields;
    private final LinkedHashMap<Integer,View> editableRows=new LinkedHashMap<>();
    private ArrayList<Integer> renderedEditableOrder=null;
    private Preview preview;
    private EditText date,time,place,note,title,device;
    private Spinner mode,position,separator;
    private SeekBar size,border,alpha,edge;
    private boolean snap=true,guides=true,haptic=true,autoGeo=false; private int strength=1; private boolean pendingSystem=false;
    private CheckBox shadow,keepExif,stripGps,lookup;
    private int foreground=Color.WHITE, frameColor=Color.WHITE;
    private Button saveOne,saveAll,pickButton,infoButton,prev,next;
    private Bitmap current;
    private Style style=new Style();
    private static class Photo {
        Uri uri; LinkedHashMap<Integer,String> values=new LinkedHashMap<>(); LinkedHashMap<String,String> tags=new LinkedHashMap<>();
        boolean loaded=false; double[] gps; String gpsState="尚未讀取"; String geoState=""; final float[] px={Float.NaN,Float.NaN,Float.NaN,Float.NaN},py=px.clone(); String dateOverride=null,timeOverride=null,placeOverride=null,deviceOverride=null;
        Photo(Uri u){uri=u;}
        String v(int k){return values.getOrDefault(k,"");}
    }
    private static class Style {
        int margin=5; float fallbackX=Float.NaN,fallbackY=Float.NaN; int mode=0,pos=3,color=Color.WHITE,frame=Color.WHITE,font=36,border=15,alpha=100,sep=0;
        boolean shadow=true,keep=true,strip=false,geo=false;
        String note="",title=""; ArrayList<Integer> fields=new ArrayList<>();
        Style copy(){Style s=new Style();s.margin=margin;s.fallbackX=fallbackX;s.fallbackY=fallbackY;s.mode=mode;s.pos=pos;s.color=color;s.frame=frame;s.font=font;s.border=border;s.alpha=alpha;s.sep=sep;s.shadow=shadow;s.keep=keep;s.strip=strip;s.geo=geo;s.note=note;s.title=title;s.fields=new ArrayList<>(fields);return s;}
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(TEAL);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);autoGeo=getPreferences(0).getBoolean("autoGeo",false);snap=getPreferences(0).getBoolean("snap",true);guides=getPreferences(0).getBoolean("guides",true);haptic=getPreferences(0).getBoolean("haptic",true);strength=getPreferences(0).getInt("strength",1);buttonHaptic=getPreferences(0).getBoolean("buttonHaptic",true);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);build();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(INK);return t;}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}
    private android.content.res.ColorStateList tint(int color){return android.content.res.ColorStateList.valueOf(color);}
    private void tick(View v){if(buttonHaptic&&v.isEnabled())v.performHapticFeedback(Build.VERSION.SDK_INT>=34?HapticFeedbackConstants.SEGMENT_TICK:HapticFeedbackConstants.KEYBOARD_TAP);}
    private void tactileTouch(View v){v.setOnTouchListener((view,e)->{if(e.getActionMasked()==MotionEvent.ACTION_UP&&e.getX()>=0&&e.getX()<view.getWidth()&&e.getY()>=0&&e.getY()<view.getHeight())tick(view);return false;});}
    private class TactileDialogBuilder extends AlertDialog.Builder {
        TactileDialogBuilder(Context c){super(c);}
        @Override public AlertDialog show(){AlertDialog dlg=super.show();for(int id:new int[]{-1,-2,-3}){Button b=dlg.getButton(id);if(b!=null){b.setTextColor(TEAL);tactileTouch(b);}}ListView list=dlg.getListView();if(list!=null)tactileTouch(list);return dlg;}
    }
    private void skin(Button b,int fill,int ink,int stroke,int h){android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{-android.R.attr.state_enabled},shape(0xffedeae3,10,0));states.addState(new int[]{},shape(fill,10,stroke));b.setBackground(new android.graphics.drawable.RippleDrawable(tint(0x25176b67),states,shape(Color.WHITE,10,0)));b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffa8aaa3,ink}));b.setMinHeight(dp(h));b.setMinimumHeight(dp(h));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);b.setElevation(0);b.setStateListAnimator(null);}
    private Button button(String label,Runnable f){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setGravity(Gravity.CENTER);skin(b,MINT,TEAL,0,44);b.setOnClickListener(v->{tick(v);f.run();});return b;}
    private void equal(LinearLayout l,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);if(l.getChildCount()>0)p.leftMargin=dp(6);l.addView(v,p);}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}
    private void section(LinearLayout l,String s){TextView t=text(s,16);t.setTextColor(TEAL);t.setTypeface(null,Typeface.BOLD);t.setPadding(0,0,0,dp(8));l.addView(t);}
    private LinearLayout card(LinearLayout parent){LinearLayout l=column();l.setPadding(dp(12),dp(10),dp(12),dp(10));l.setBackground(shape(PAPER,10,LINE));add(parent,l,8);return l;}
    private void leftLabel(LinearLayout r,String label){TextView t=text(label,13);t.setSingleLine(true);r.addView(t,new LinearLayout.LayoutParams(dp(76),-2));}
    private EditText input(LinearLayout l,String label,boolean multi){LinearLayout r=row();leftLabel(r,label);EditText e=new EditText(this);e.setTextSize(14);e.setTextColor(INK);e.setSingleLine(!multi);e.setHint("未填寫");e.setHintTextColor(0xff92988f);if(multi)e.setMinLines(1);e.setBackground(shape(0xfff8f5ef,10,LINE));e.setPadding(dp(10),dp(6),dp(10),dp(6));e.setMinHeight(dp(42));r.addView(e,new LinearLayout.LayoutParams(0,-2,1));add(l,r,5);e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(!binding)changed();}public void afterTextChanged(Editable e){}});return e;}
    private Spinner spinner(LinearLayout l,String label,String[] options){LinearLayout r=row();leftLabel(r,label);Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,options){@Override public View getView(int p,View v,ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(INK);t.setTextSize(14);t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(dp(8),dp(5),dp(4),dp(5));return t;}};
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setBackgroundTintList(tint(GOLD));s.setMinimumHeight(dp(42));r.addView(s,new LinearLayout.LayoutParams(0,dp(44),1));add(l,r,2);tactileTouch(s);s.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int p,long id){if(!binding){if(a==mode){if(p==0){foreground=Color.WHITE;}else{foreground=frameColor==Color.BLACK?Color.WHITE:Color.BLACK;}}changed();}}});return s;}
    private SeekBar slider(LinearLayout l,String label,int max,int start){LinearLayout r=row();leftLabel(r,label);SeekBar s=new SeekBar(this);s.setMax(max);s.setProgress(start);s.setProgressTintList(tint(TEAL));s.setThumbTintList(tint(TEAL));s.setProgressBackgroundTintList(tint(0xffddd6c8));r.addView(s,new LinearLayout.LayoutParams(0,dp(42),1));TextView value=text(Integer.toString(start),13);value.setGravity(Gravity.CENTER);value.setBackground(shape(0xfff0ebe0,10,0));r.addView(value,new LinearLayout.LayoutParams(dp(44),dp(32)));add(l,r,2);s.setContentDescription(label);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean u){value.setText(Integer.toString(p));if(!binding)changed();}});return s;}
    private CheckBox check(LinearLayout l,String label,boolean value){CheckBox c=new CheckBox(this);c.setText(label);c.setTextSize(14);c.setTextColor(INK);c.setButtonTintList(tint(TEAL));c.setChecked(value);c.setMinHeight(dp(44));l.addView(c);c.setOnCheckedChangeListener((b,v)->{if(!binding)changed();});tactileTouch(c);return c;}
    private void resetPosition(){if(busy)return;Photo p=active();if(p==null)return;p.px[style.mode]=p.py[style.mode]=Float.NaN;style.fallbackX=style.fallbackY=Float.NaN;preview.invalidate();}
    private void help(){new TactileDialogBuilder(this).setTitle("操作說明").setMessage("點選相片：優先原生相簿，長按可開啟系統相片選擇器。\n拖曳相片上的文字即可移動，方向鍵可微調；磁吸設定可調整吸附、輔助線及震動。\n字體大小為相片短邊的千分比（最小 10），邊框與安全邊距為短邊百分比。\n輸出為 JPEG，另存至 Pictures/ㄚ喬相片加字。移除 GPS 不會移除已畫在相片上的文字。").setPositiveButton("關閉",null).show();}
    private void build(){
        binding=true;root=column();root.setBackgroundColor(BG);root.setPadding(dp(10),0,dp(10),dp(6));root.setFocusableInTouchMode(true);setContentView(root);
        LinearLayout top=row();top.setPadding(dp(10),dp(6),dp(8),dp(6));GradientDrawable header=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff125752,0xff23766c});header.setCornerRadius(dp(10));top.setBackground(header);
        TextView heading=text("ㄚ喬相片加字",19);heading.setTextColor(Color.WHITE);heading.setTypeface(null,Typeface.BOLD);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));pickButton=button("選取相片",()->pick());skin(pickButton,0xfffbf2dc,0xff254d44,0,44);top.addView(pickButton);pickButton.setOnLongClickListener(v->{if(!busy){tick(v);beginPick(true);}return true;});add(root,top,4);
        LinearLayout statusRow=row();status=text("尚未選取相片",12);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);status.setPadding(dp(9),dp(5),dp(9),dp(5));status.setTextColor(TEAL);status.setBackground(shape(MINT,10,0));statusRow.addView(status,new LinearLayout.LayoutParams(0,-2,1));metaBadge=text("",12);metaBadge.setSingleLine(true);metaBadge.setPadding(dp(8),dp(5),dp(8),dp(5));metaBadge.setBackground(shape(0xfff0e8d8,10,0));metaBadge.setTextColor(0xff775b2e);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-2,-2);mp.leftMargin=dp(6);statusRow.addView(metaBadge,mp);Button helpButton=button("ⓘ",()->help());skin(helpButton,PAPER,TEAL,LINE,36);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(40),dp(40));hp.leftMargin=dp(6);statusRow.addView(helpButton,hp);add(root,statusRow,6);
        LinearLayout previewCard=column();previewCard.setPadding(dp(6),dp(6),dp(6),dp(6));previewCard.setBackground(shape(PAPER,10,LINE));LinearLayout.LayoutParams pc=new LinearLayout.LayoutParams(-1,0,1.18f);pc.topMargin=dp(6);root.addView(previewCard,pc);
        preview=new Preview();preview.setBackground(shape(0xffebe8df,10,0));preview.setClipToOutline(true);previewCard.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        nav=row();prev=button("‹",()->switchPhoto(-1));next=button("›",()->switchPhoto(1));infoButton=button("ⓘ",()->showInfo());Button reset=button("↺",()->resetPosition());prev.setContentDescription("上一張");next.setContentDescription("下一張");infoButton.setContentDescription("原始相片資訊");reset.setContentDescription("重設文字位置");
        for(Button b:new Button[]{prev,next,infoButton,reset}){b.setTextSize(20);skin(b,0xfff1ede4,TEAL,0,44);}nav.addView(prev,new LinearLayout.LayoutParams(dp(44),dp(44)));count=text("未選取相片",14);count.setGravity(Gravity.CENTER);nav.addView(count,new LinearLayout.LayoutParams(0,-2,1));for(Button b:new Button[]{next,infoButton,reset}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(44),dp(44));p.leftMargin=dp(5);nav.addView(b,p);}add(previewCard,nav,5);
        nudges=row();for(String a:new String[]{"←","↑","↓","→"}){final int x=a.equals("←")?-1:a.equals("→")?1:0,y=a.equals("↑")?-1:a.equals("↓")?1:0;Button b=button(a,()->nudge(x,y));b.setTextSize(19);b.setContentDescription(a.equals("←")?"向左微調":a.equals("→")?"向右微調":a.equals("↑")?"向上微調":"向下微調");equal(nudges,b);}Button magnet=button("磁吸",()->snapSettings());skin(magnet,0xffefe6d3,0xff735c32,0,44);equal(nudges,magnet);add(root,nudges,6);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);editor=column();editor.setPadding(0,0,0,dp(6));scroll.addView(editor);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1f);sp.topMargin=dp(2);root.addView(scroll,sp);
        LinearLayout contentCard=card(editor);LinearLayout titleRow=row();TextView contentTitle=text("加字內容",16);contentTitle.setTextColor(TEAL);contentTitle.setTypeface(null,Typeface.BOLD);titleRow.addView(contentTitle,new LinearLayout.LayoutParams(0,-2,1));Button select=button("選擇資訊／排序",()->fieldDialog());titleRow.addView(select);contentCard.addView(titleRow);fields=text("",12);fields.setTextColor(MUTED);fields.setMaxLines(2);fields.setEllipsize(android.text.TextUtils.TruncateAt.END);add(contentCard,fields,4);
        editableFields=column();contentCard.addView(editableFields);
        date=editableInput(0,"拍攝日期",false);time=editableInput(1,"拍攝時間",false);place=editableInput(3,"拍攝地點",false);device=editableInput(7,"器材名稱",false);note=editableInput(27,"共用備註",true);title=editableInput(28,"共用標題",false);
        LinearLayout layoutCard=card(editor);section(layoutCard,"樣式與位置");mode=spinner(layoutCard,"加字模式",new String[]{"相片內部","外部下邊框","外部上邊框","外部四邊框（下方加字）"});position=spinner(layoutCard,"文字位置",new String[]{"左上","上方置中","右上","左下","下方置中","右下"});position.setSelection(3);separator=spinner(layoutCard,"排列方式",new String[]{"逐項換行","空格","｜","・","／"});
        size=slider(layoutCard,"字體大小",100,36);edge=slider(layoutCard,"安全邊距 %",20,5);border=slider(layoutCard,"邊框厚度 %",50,15);alpha=slider(layoutCard,"不透明度 %",100,100);
        LinearLayout colors=row();equal(colors,button("文字顏色",()->chooseColor(false)));equal(colors,button("邊框顏色",()->chooseColor(true)));add(layoutCard,colors,4);shadow=check(layoutCard,"文字陰影（相片內）",true);
        LinearLayout advancedCard=card(editor);Button toggle=button("進階設定  ▾",()->{if(advanced!=null){boolean open=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(open?View.VISIBLE:View.GONE);((Button)advancedCard.getChildAt(0)).setText(open?"進階設定  ▴":"進階設定  ▾");}});skin(toggle,0xfff0ebe0,TEAL,0,44);advancedCard.addView(toggle);advanced=column();advancedCard.addView(advanced);advanced.setVisibility(View.GONE);
        LinearLayout geo=row();equal(geo,button("查詢拍攝地點",()->lookupCurrent()));equal(geo,button("自動地名設定",()->geoSettings()));add(advanced,geo,6);keepExif=check(advanced,"保留可相容的 EXIF 拍攝資訊",true);stripGps=check(advanced,"移除輸出 EXIF 的 GPS 資訊",false);lookup=check(advanced,"批次時使用網路查詢地名",false);CheckBox bv=check(advanced,"按鈕輕震回饋",buttonHaptic);bv.setOnCheckedChangeListener((b,v)->{buttonHaptic=v;getPreferences(0).edit().putBoolean("buttonHaptic",v).apply();});
        LinearLayout bottom=row();bottom.setPadding(0,dp(5),0,0);saveOne=button("儲存本張",()->export(false));saveAll=button("批次儲存",()->export(true));skin(saveOne,PAPER,TEAL,TEAL,48);skin(saveAll,TEAL,Color.WHITE,0,48);saveOne.setTypeface(null,Typeface.BOLD);saveAll.setTypeface(null,Typeface.BOLD);equal(bottom,saveOne);equal(bottom,saveAll);add(root,bottom,4);
        binding=false;changed();refreshControls();root.requestFocus();
    }
    // Keep each original EditText alive, even when its field is unchecked.
    // Only rebuild visual ordering when the user's field selection/order changes.
    private EditText editableInput(int key,String label,boolean multi){
        EditText e=input(editableFields,label,multi);
        View fieldRow=(View)e.getParent();
        editableRows.put(key,fieldRow);
        fieldRow.setVisibility(View.GONE);
        return e;
    }
    private void syncEditableFields(){
        if(editableFields==null)return;
        ArrayList<Integer> visible=new ArrayList<>();
        for(int key:chosen)if(editableRows.containsKey(key))visible.add(key);
        if(visible.equals(renderedEditableOrder))return;
        for(Map.Entry<Integer,View> e:editableRows.entrySet()){
            View fieldRow=e.getValue();
            boolean show=visible.contains(e.getKey());
            if(!show&&fieldRow.hasFocus())fieldRow.clearFocus();
            fieldRow.setVisibility(show?View.VISIBLE:View.GONE);
        }
        for(int key:visible)editableFields.bringChildToFront(editableRows.get(key));
        editableFields.setVisibility(visible.isEmpty()?View.GONE:View.VISIBLE);
        renderedEditableOrder=visible;
        editableFields.requestLayout();
        editableFields.invalidate();
    }
    private void setBusy(boolean b){busy=b;refreshControls();}
    private void refreshControls(){boolean has=!photos.isEmpty();pickButton.setEnabled(!busy);prev.setEnabled(!busy&&has&&index>0);next.setEnabled(!busy&&has&&index<photos.size()-1);infoButton.setEnabled(!busy&&has);saveOne.setEnabled(!busy&&has&&current!=null);saveAll.setEnabled(!busy&&has&&current!=null);setEnabledTree(editor,!busy&&has);setEnabledTree(nudges,!busy&&has&&current!=null);nav.getChildAt(nav.getChildCount()-1).setEnabled(!busy&&has&&current!=null);Photo item=active();metaBadge.setVisibility(item!=null&&item.loaded&&!busy?View.VISIBLE:View.GONE);if(item!=null&&item.loaded){String a=item.v(7).isEmpty()?"":"器材";String b=item.v(0).isEmpty()?"":"日期";metaBadge.setText((a+b).isEmpty()?"相片資訊":(a.isEmpty()?b:b.isEmpty()?a:a+"・"+b)+" ✓");}count.setText(has?(index+1)+" / "+photos.size()+" 張":"未選取相片");}
    private void setEnabledTree(View v,boolean enabled){v.setEnabled(enabled);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)setEnabledTree(g.getChildAt(i),enabled);}}
    private void pick(){beginPick(false);}
    private boolean hasLocation(){return checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private boolean hasPhotoAccess(){return checkSelfPermission(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT>=34&&checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")==PackageManager.PERMISSION_GRANTED);}
    private void beginPick(boolean system){if(busy)return;pendingSystem=system;if(!hasLocation()||!hasPhotoAccess()){new TactileDialogBuilder(this).setTitle("讀取相片原始位置").setMessage("為讀取拍攝時寫入的 GPS，需要相片及相片位置資訊權限。不會取得手機目前位置。若拒絕仍可選相片加字。") .setNegativeButton("略過",(a,b)->launchPick(system)).setPositiveButton("授權",(a,b)->requestPermissions(Build.VERSION.SDK_INT>=34?new String[]{Manifest.permission.READ_MEDIA_IMAGES,"android.permission.READ_MEDIA_VISUAL_USER_SELECTED",Manifest.permission.ACCESS_MEDIA_LOCATION}:new String[]{Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.ACCESS_MEDIA_LOCATION},LOCATION)).show();}else launchPick(system);}
    private void launchPick(boolean system){if(system){systemPick();return;}if(busy)return;
        Intent nativePick=new Intent(Intent.ACTION_PICK);nativePick.setType("image/*");nativePick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);nativePick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        List<ResolveInfo> candidates=getPackageManager().queryIntentActivities(nativePick,PackageManager.MATCH_DEFAULT_ONLY);
        for(ResolveInfo r:candidates){String p=r.activityInfo.packageName.toLowerCase(Locale.ROOT);if((p.contains("oppo")||p.contains("coloros")||p.contains("oneplus")||p.contains("oplus"))&&(p.contains("gallery")||p.contains("album")||p.contains("photo"))){try{nativePick.setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name));startActivityForResult(nativePick,PICK);return;}catch(Exception ignored){}}}
        systemPick();
    }
    private void systemPick(){try{if(Build.VERSION.SDK_INT>=33){Intent i=new Intent(MediaStore.ACTION_PICK_IMAGES);i.setType("image/*");i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,Math.min(LIMIT,MediaStore.getPickImagesMaxLimit()));i.putExtra("android.provider.extra.REQUEST_LOCATION_METADATA_ACCESS",true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK);}else{Intent i=new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK);}}catch(Exception e){message("本機未提供可用的相片選擇器。未改用檔案管理員。");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK||result!=RESULT_OK||data==null)return;LinkedHashSet<Uri> uris=new LinkedHashSet<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++){Uri u=data.getClipData().getItemAt(i).getUri();if(u!=null)uris.add(u);}if(data.getData()!=null)uris.add(data.getData());if(uris.isEmpty())return;photos.clear();for(Uri u:uris){if(photos.size()>=LIMIT)break;photos.add(new Photo(u));}index=0;style.fallbackX=style.fallbackY=Float.NaN;if(uris.size()>LIMIT)message("每批最多 100 張");if(!getPreferences(0).getBoolean("geoAsked",false))geoSettings(true);else loadCurrent();}
    private void switchPhoto(int delta){if(busy)return;changed();index+=delta;loadCurrent();}
    private Photo active(){return photos.isEmpty()?null:photos.get(index);}
    private void loadCurrent(){
        final Photo p=active();if(p==null)return;final int ticket=++generation;setBusy(true);status.setText("讀取相片資訊…");if(current!=null){current.recycle();current=null;}preview.invalidate();
        worker.execute(()->{Bitmap b=null;String error=null;try{read(p);if(autoGeo&&p.gps!=null&&p.placeOverride==null&&p.v(3).isEmpty()){String loc=geocode(p);if(!loc.isEmpty())p.values.put(3,loc);else p.geoState="地名轉換失敗";}b=decode(p.uri,1800);}catch(Exception|OutOfMemoryError e){error=e.getClass().getSimpleName()+": "+e.getMessage();}final Bitmap image=b;final String fail=error;main.post(()->{if(isFinishing()||ticket!=generation){if(image!=null)image.recycle();return;}current=image;binding=true;date.setText(p.dateOverride==null?p.v(0):p.dateOverride);time.setText(p.timeOverride==null?p.v(1):p.timeOverride);place.setText(p.placeOverride==null?p.v(3):p.placeOverride);device.setText(p.deviceOverride==null?p.v(7):p.deviceOverride);binding=false;status.setText(fail==null?(p.gps==null?"GPS："+p.gpsState:(p.geoState.isEmpty()?"已取得 GPS":"已取得 GPS · "+p.geoState)):("讀取失敗："+fail));setBusy(false);preview.invalidate();});});
    }
    private ExifInterface openExif(Uri u)throws IOException{try(InputStream in=getContentResolver().openInputStream(u)){if(in==null)throw new IOException("無法開啟相片");return new ExifInterface(in);}}
    private ExifInterface originalExif(Uri u){if(!hasLocation())return null;try{return openExif(MediaStore.setRequireOriginal(u));}catch(Exception ignored){return null;}}
    private ExifInterface exif(Photo p,String name,long bytes)throws IOException{
        ExifInterface selected=null;boolean originalRead=false;String originalDate="";
        try{selected=openExif(p.uri);originalDate=tag(selected,"DateTimeOriginal");if(selected.getLatLong()!=null){p.gpsState="已從相片取得";return selected;}}catch(Exception ignored){}
        ExifInterface direct=originalExif(p.uri);
        if(direct!=null){originalRead=true;if(direct.getLatLong()!=null){p.gpsState="已從原始相片取得";return direct;}if(selected==null)selected=direct;}
        if(hasLocation()&&hasPhotoAccess()&&name!=null&&!name.isEmpty()&&bytes>0){
            Uri collection=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;Uri candidate=null;int matches=0;
            try(Cursor c=getContentResolver().query(collection,new String[]{"_id"}, "_display_name=? AND _size=?",new String[]{name,Long.toString(bytes)},null)){
                if(c!=null)while(c.moveToNext()){Uri u=ContentUris.withAppendedId(collection,c.getLong(0));ExifInterface e=originalExif(u);
                    if(e!=null&&!originalDate.isEmpty()&&originalDate.equals(tag(e,"DateTimeOriginal"))){matches++;candidate=u;}
                }
            }catch(Exception ignored){}
            if(matches==1&&candidate!=null){ExifInterface e=originalExif(candidate);if(e!=null){originalRead=true;if(e.getLatLong()!=null){p.gpsState="已從相簿原始檔取得";return e;}if(selected==null)selected=e;}}
        }
        p.gpsState=!hasLocation()?"尚未授權相片位置":!hasPhotoAccess()?"相片存取權限不足":originalRead?"原始資料未含 GPS":"相簿未提供原始位置";
        if(selected!=null)return selected;throw new IOException("無法讀取 EXIF");
    }
    private static void put(Photo p,int n,String s){if(s!=null&&!s.trim().isEmpty())p.values.put(n,s.trim());}
    private static String format(double x){return new DecimalFormat("0.##",DecimalFormatSymbols.getInstance(Locale.US)).format(x);}
    private static String tag(ExifInterface e,String k){String s=e.getAttribute(k);return s==null?"":s;}
    private static String num(ExifInterface e,String key,String prefix,String suffix){double d=e.getAttributeDouble(key,Double.NaN);return Double.isNaN(d)?"":prefix+format(d)+suffix;}
    private void read(Photo p){if(p.loaded)return;String name="",mime=getContentResolver().getType(p.uri);long bytes=-1;
        try(Cursor c=getContentResolver().query(p.uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){name=c.getString(0);if(!c.isNull(1))bytes=c.getLong(1);}}catch(Exception ignored){}put(p,20,name);if(bytes>=0)put(p,21,format(bytes/1048576d)+" MB");put(p,22,mime==null?"":mime.replace("image/","").toUpperCase(Locale.ROOT));
        try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(p.uri)){BitmapFactory.decodeStream(in,null,o);}if(o.outWidth>0&&o.outHeight>0){put(p,18,o.outWidth+" × "+o.outHeight);put(p,19,format((long)o.outWidth*o.outHeight/1000000d)+" MP");}}catch(Exception ignored){}
        try{ExifInterface e=exif(p,name,bytes);for(String k:COPY_TAGS){String v=e.getAttribute(k);if(v!=null)p.tags.put(k,v);}
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
    private void changed(){if(binding||date==null||lookup==null)return;Photo p=active();if(p!=null&&p.loaded){p.dateOverride=override(date,p.v(0));p.timeOverride=override(time,p.v(1));p.placeOverride=override(place,p.v(3));p.deviceOverride=override(device,p.v(7));}int newMode=Math.max(0,mode.getSelectedItemPosition()), newPos=Math.max(0,position.getSelectedItemPosition());if(newMode!=style.mode||newPos!=style.pos){if(p!=null){p.px[newMode]=Float.NaN;p.py[newMode]=Float.NaN;}style.fallbackX=style.fallbackY=Float.NaN;}style.mode=newMode;style.pos=newPos;style.margin=edge==null?5:edge.getProgress();style.sep=Math.max(0,separator.getSelectedItemPosition());style.font=Math.max(10,size.getProgress());style.border=border.getProgress();style.alpha=alpha.getProgress();style.color=foreground;style.frame=frameColor;style.shadow=shadow.isChecked();style.keep=keepExif.isChecked();style.strip=stripGps.isChecked();style.geo=lookup.isChecked()||autoGeo;style.note=note.getText().toString();style.title=title.getText().toString();style.fields=new ArrayList<>(chosen);ArrayList<String> names=new ArrayList<>();for(int f:chosen)names.add(LABELS[f]);fields.setText(String.join(" · ",names));syncEditableFields();preview.invalidate();}
    private String override(EditText e,String original){String v=e.getText().toString();return v.equals(original)?null:v;}
    private void fieldDialog(){boolean[] ticks=new boolean[LABELS.length];for(int k:chosen)ticks[k]=true;new TactileDialogBuilder(this).setTitle("選擇加字資訊").setMultiChoiceItems(LABELS,ticks,(d,i,b)->ticks[i]=b).setNegativeButton("取消",null).setPositiveButton("下一步：排列",(d,w)->{chosen.removeIf(k->!ticks[k]);for(int i=0;i<ticks.length;i++)if(ticks[i]&&!chosen.contains(i))chosen.add(i);changed();orderDialog();}).show();}
    private void orderDialog(){LinearLayout box=column();ScrollView sc=new ScrollView(this);sc.addView(box);Runnable[] refresh=new Runnable[1];refresh[0]=()->{box.removeAllViews();for(int i=0;i<chosen.size();i++){final int j=i;LinearLayout r=row();r.addView(text(LABELS[chosen.get(i)],15),new LinearLayout.LayoutParams(0,-2,1));r.addView(button("↑",()->{if(j>0){Collections.swap(chosen,j,j-1);changed();refresh[0].run();}}));r.addView(button("↓",()->{if(j+1<chosen.size()){Collections.swap(chosen,j,j+1);changed();refresh[0].run();}}));box.addView(r);}};refresh[0].run();new TactileDialogBuilder(this).setTitle("排列顯示順序").setView(sc).setPositiveButton("完成",null).show();}
    private void chooseColor(boolean frame){String[] names={"白色","黑色","米白","金色","深綠","藍色","紅色","自訂 HEX"};int[] colors={Color.WHITE,Color.BLACK,0xfffaf4e8,0xffd5a344,TEAL,0xff387ec8,0xffb6404d};new TactileDialogBuilder(this).setTitle(frame?"邊框顏色":"文字顏色").setItems(names,(d,i)->{if(i<colors.length)applyColor(frame,colors[i]);else{EditText e=new EditText(this);e.setHint("#FFFFFF");new TactileDialogBuilder(this).setTitle("輸入六位 HEX 色碼").setView(e).setNegativeButton("取消",null).setPositiveButton("套用",(a,b)->{try{String s=e.getText().toString().trim();if(!s.startsWith("#"))s="#"+s;if(s.length()!=7)throw new IllegalArgumentException();applyColor(frame,Color.parseColor(s));}catch(Exception ex){message("色碼格式錯誤");}}).show();}}).show();}
    private void applyColor(boolean frame,int color){if(frame){frameColor=color;if(style.mode!=0&&foreground==color)foreground=color==Color.WHITE?Color.BLACK:Color.WHITE;}else foreground=color;changed();}
    private void showInfo(){Photo p=active();if(p==null)return;StringBuilder b=new StringBuilder();for(int i=0;i<27;i++)if(!p.v(i).isEmpty())b.append(LABELS[i]).append("：").append(p.v(i)).append('\n');b.append("\nGPS 讀取：").append(p.gpsState).append("\n地名：").append(p.geoState).append("\nEXIF 原始欄位（已讀取）\n");for(Map.Entry<String,String> e:p.tags.entrySet())b.append(e.getKey()).append("：").append(e.getValue()).append('\n');new TactileDialogBuilder(this).setTitle("原始相片資訊").setMessage(b.toString()).setPositiveButton("關閉",null).setNeutralButton("複製",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("相片資訊",b));}).show();}
    private String geocode(Photo p){if(p.gps==null||!Geocoder.isPresent())return "";try{List<Address> list=new Geocoder(this,Locale.TRADITIONAL_CHINESE).getFromLocation(p.gps[0],p.gps[1],1);if(list!=null&&!list.isEmpty()){Address a=list.get(0);LinkedHashSet<String> parts=new LinkedHashSet<>();for(String s:new String[]{a.getCountryName(),a.getAdminArea(),a.getLocality(),a.getSubLocality()})if(s!=null&&!s.isEmpty())parts.add(s);return String.join("・",parts);}}catch(Exception ignored){}return "";}
    private void geoSettings(){geoSettings(false);}
    private void geoSettings(boolean first){new TactileDialogBuilder(this).setTitle("自動取得拍攝地名").setMessage("是否允許將相片內原始 GPS 座標交給 Android 地理編碼服務（可能連網），自動查詢地名？不會讀取目前位置。").setNegativeButton("不要",(d,w)->setGeo(false,first)).setPositiveButton("允許",(d,w)->setGeo(true,first)).setOnCancelListener(d->setGeo(false,first)).show();}
    private void setGeo(boolean value,boolean first){autoGeo=value;getPreferences(0).edit().putBoolean("autoGeo",value).putBoolean("geoAsked",true).apply();if(first||active()!=null)loadCurrent();else changed();}
    private void lookupCurrent(){Photo p=active();if(p==null||busy)return;if(p.gps==null){new TactileDialogBuilder(this).setTitle("未取得 GPS").setMessage(p.gpsState+"。可重新授權並選取原始相片，或手動填寫地點。").setPositiveButton("關閉",null).setNeutralButton("重新選取相片",(d,w)->beginPick(false)).show();return;}new TactileDialogBuilder(this).setTitle("查詢拍攝地點").setMessage("將相片 GPS 座標交給 Android 地理編碼服務，可能連網。").setNegativeButton("取消",null).setPositiveButton("查詢",(d,w)->{setBusy(true);worker.execute(()->{String loc=geocode(p);main.post(()->{setBusy(false);if(!loc.isEmpty()){p.values.put(3,loc);p.placeOverride=null;binding=true;place.setText(loc);binding=false;changed();}else{p.geoState="地名轉換失敗";message("已取得 GPS，但無法轉換地名。");}});});}).show();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==LOCATION)launchPick(pendingSystem);}
    private String fieldValue(Photo p,int k,Style s,int ordinal){switch(k){case 0:return p.dateOverride==null?p.v(0):p.dateOverride;case 1:return p.timeOverride==null?p.v(1):p.timeOverride;case 3:return p.placeOverride==null?p.v(3):p.placeOverride;case 7:return p.deviceOverride==null?p.v(7):p.deviceOverride;case 27:return s.note;case 28:return s.title;case 29:return String.format(Locale.US,"%03d",ordinal+1);default:return p.v(k);}}
    private String content(Photo p,Style s,int ordinal){String[] separators={"\n","  ","  |  "," ・ "," / "};ArrayList<String> out=new ArrayList<>();for(int k:s.fields){String v=fieldValue(p,k,s,ordinal);if(v!=null&&!v.trim().isEmpty())out.add(v.trim());}return String.join(separators[Math.max(0,Math.min(s.sep,4))],out);}
    private static float clamp(float n,float lo,float hi){return Math.max(lo,Math.min(hi,n));}
    private static class Layout {
        int w,h,left,top,textX,textY,regionX,regionY,regionW,regionH,pad,tw,th;
        android.text.StaticLayout text; android.text.TextPaint paint;
        float spanX(){return Math.max(0,regionW-tw);}
        float spanY(){return Math.max(0,regionH-th);}
    }
    private Layout layout(Bitmap b,Photo p,Style s,int ordinal){
        Layout g=new Layout();int w=b.getWidth(),h=b.getHeight(),shorter=Math.min(w,h);int pad=Math.max(0,Math.round(shorter*s.margin/100f));int frame=Math.max(1,Math.round(shorter*s.border/100f));int margin=s.mode==3?frame:0;int width=w+margin*2;
        boolean outer=s.mode!=0;String contents=content(p,s,ordinal);android.text.TextPaint tp=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);tp.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));tp.setColor(s.color);tp.setAlpha(Math.round(255*s.alpha/100f));float fs=Math.max(2,shorter*s.font/1000f);tp.setTextSize(fs);if(s.shadow&&!outer)tp.setShadowLayer(Math.max(1,fs*.09f),0,fs*.045f,0xb0000000);
        int maxWidth=Math.max(1,width-2*pad);int alignment=s.pos%3;android.text.Layout.Alignment align=alignment==0?android.text.Layout.Alignment.ALIGN_NORMAL:alignment==1?android.text.Layout.Alignment.ALIGN_CENTER:android.text.Layout.Alignment.ALIGN_OPPOSITE;
        int tw=Math.max(1,Math.min(maxWidth,(int)Math.ceil(android.text.Layout.getDesiredWidth(contents,tp))+1));android.text.StaticLayout lines=makeLayout(contents,tp,tw,align);
        if(!outer){while(lines.getHeight()>Math.max(1,h-2*pad)&&tp.getTextSize()>2){tp.setTextSize(Math.max(2,tp.getTextSize()*.85f));tw=Math.max(1,Math.min(maxWidth,(int)Math.ceil(android.text.Layout.getDesiredWidth(contents,tp))+1));lines=makeLayout(contents,tp,tw,align);}}
        int bottom=0,top=0,needed=contents.isEmpty()?0:lines.getHeight()+2*pad;
        if(s.mode==1)bottom=Math.max(frame,needed);if(s.mode==2)top=Math.max(frame,needed);if(s.mode==3){top=frame;bottom=Math.max(frame,needed);}
        g.w=width;g.h=h+top+bottom;g.left=margin;g.top=top;g.text=lines;g.paint=tp;g.tw=tw;g.th=lines.getHeight();g.pad=pad;
        g.regionX=0;g.regionW=width;g.regionY=s.mode==2?0:s.mode==0?0:top+h;g.regionH=s.mode==2?top:s.mode==0?h:bottom;
        float sx=g.spanX(),sy=g.spanY();float lowX=Math.min(pad,sx/2),lowY=Math.min(pad,sy/2);
        float dx=alignment==0?lowX:alignment==1?sx/2:sx-lowX;
        float dy=outer?sy/2:s.pos<3?lowY:sy-lowY;
        float nx=p.px[s.mode],ny=p.py[s.mode];
        if(Float.isNaN(nx))nx=s.fallbackX;if(Float.isNaN(ny))ny=s.fallbackY;
        g.textX=g.regionX+Math.round(Float.isNaN(nx)?dx:clamp(nx,0,1)*sx);g.textY=g.regionY+Math.round(Float.isNaN(ny)?dy:clamp(ny,0,1)*sy);
        return g;
    }
    private android.text.StaticLayout makeLayout(String text,android.text.TextPaint paint,int width,android.text.Layout.Alignment align){return android.text.StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setAlignment(align).setTextDirection(android.text.TextDirectionHeuristics.LTR).setIncludePad(false).setLineSpacing(0,1.12f).build();}
    private void draw(Canvas c,Bitmap b,Layout g,Style s){Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);paint.setColor(s.mode==0?Color.BLACK:s.frame);c.drawRect(0,0,g.w,g.h,paint);c.drawBitmap(b,g.left,g.top,paint);c.save();c.clipRect(g.regionX,g.regionY,g.regionX+g.regionW,g.regionY+g.regionH);c.translate(g.textX,g.textY);g.text.draw(c);c.restore();}
    private void setPosition(Photo p,Layout g,float x,float y){p.px[style.mode]=g.spanX()==0?0:clamp(x/g.spanX(),0,1);p.py[style.mode]=g.spanY()==0?0:clamp(y/g.spanY(),0,1);style.fallbackX=p.px[style.mode];style.fallbackY=p.py[style.mode];preview.invalidate();}
    private void nudge(int dx,int dy){Photo p=active();if(busy||current==null||p==null||content(p,style,index).isEmpty())return;Layout g=layout(current,p,style,index);float step=Math.max(1,Math.min(current.getWidth(),current.getHeight())*.005f);setPosition(p,g,g.textX-g.regionX+dx*step,g.textY-g.regionY+dy*step);}
    private void snapSettings(){LinearLayout box=column();box.setPadding(dp(18),dp(8),dp(18),dp(8));CheckBox a=new CheckBox(this);a.setText("開啟磁吸");a.setChecked(snap);box.addView(a);tactileTouch(a);CheckBox b=new CheckBox(this);b.setText("顯示輔助線");b.setChecked(guides);box.addView(b);tactileTouch(b);CheckBox c=new CheckBox(this);c.setText("吸附時輕震");c.setChecked(haptic);box.addView(c);tactileTouch(c);box.addView(text("磁吸強度",14));Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"弱","中","強"}));sp.setSelection(strength);box.addView(sp);tactileTouch(sp);box.addView(button("恢復本張預設位置",()->{Photo p=active();if(p!=null){p.px[style.mode]=p.py[style.mode]=Float.NaN;}style.fallbackX=style.fallbackY=Float.NaN;preview.invalidate();}));new TactileDialogBuilder(this).setTitle("磁吸設定").setView(box).setNegativeButton("取消",null).setPositiveButton("套用",(d,w)->{snap=a.isChecked();guides=b.isChecked();haptic=c.isChecked();strength=sp.getSelectedItemPosition();getPreferences(0).edit().putBoolean("snap",snap).putBoolean("guides",guides).putBoolean("haptic",haptic).putInt("strength",strength).apply();}).show();}
    private class Preview extends View {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Layout last;float factor=1,ox=0,oy=0,grabX,grabY,guideX=Float.NaN,guideY=Float.NaN;boolean dragging;int prevX=-1,prevY=-1;
        Preview(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setHapticFeedbackEnabled(true);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(0xffebe8df);Photo item=active();if(current==null||item==null){last=null;p.setColor(0xff647673);p.setTextSize(dp(16));p.setTextAlign(Paint.Align.CENTER);canvas.drawText(busy?"讀取中…":"請先選取相片",getWidth()/2f,getHeight()/2f,p);return;}Layout g=MainActivity.this.layout(current,item,style,index);last=g;float availableW=Math.max(1,getWidth()-dp(12)),availableH=Math.max(1,getHeight()-dp(12));factor=Math.min(availableW/g.w,availableH/g.h);ox=(getWidth()-factor*g.w)/2;oy=(getHeight()-factor*g.h)/2;canvas.save();canvas.translate(ox,oy);canvas.scale(factor,factor);MainActivity.this.draw(canvas,current,g,style);
            if(dragging){p.setColor(0xff26dcf6);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1)/factor);p.setPathEffect(new DashPathEffect(new float[]{dp(4)/factor,dp(3)/factor},0));canvas.drawRect(g.textX,g.textY,g.textX+g.tw,g.textY+g.th,p);if(guides){if(!Float.isNaN(guideX))canvas.drawLine(guideX,g.regionY,guideX,g.regionY+g.regionH,p);if(!Float.isNaN(guideY))canvas.drawLine(g.regionX,guideY,g.regionX+g.regionW,guideY,p);}p.setPathEffect(null);p.setStyle(Paint.Style.FILL);}canvas.restore();}
        private int closest(float value,float[] anchors,float threshold){int i=-1;float dist=threshold;for(int j=0;j<anchors.length;j++){float q=Math.abs(value-anchors[j]);if(q<=dist){dist=q;i=j;}}return i;}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){Photo item=active();Layout g=last;if(busy||item==null||g==null||content(item,style,index).isEmpty())return false;float x=(e.getX()-ox)/factor,y=(e.getY()-oy)/factor;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:float hit=dp(12)/factor;if(x<g.textX-hit||x>g.textX+g.tw+hit||y<g.textY-hit||y>g.textY+g.th+hit)return false;grabX=x-g.textX;grabY=y-g.textY;dragging=true;prevX=prevY=-1;getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
                case MotionEvent.ACTION_MOVE:if(!dragging)return false;float tx=clamp(x-grabX-g.regionX,0,g.spanX()),ty=clamp(y-grabY-g.regionY,0,g.spanY());float padx=Math.min(g.pad,g.spanX()/2),pady=Math.min(g.pad,g.spanY()/2);float[] ax={padx,g.spanX()/2,g.spanX()-padx},ay={pady,g.spanY()/2,g.spanY()-pady};float threshold=dp(strength==0?4:strength==1?8:14)/factor;
                    int sx=snap&&g.spanX()>0?closest(tx,ax,threshold):-1,sy=snap&&g.spanY()>0?closest(ty,ay,threshold):-1;guideX=guideY=Float.NaN;if(sx>=0){tx=ax[sx];guideX=g.regionX+tx+(sx==0?0:sx==1?g.tw/2f:g.tw);}if(sy>=0){ty=ay[sy];guideY=g.regionY+ty+(sy==0?0:sy==1?g.th/2f:g.th);}if(haptic&&((sx>=0&&sx!=prevX)||(sy>=0&&sy!=prevY)))performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);prevX=sx;prevY=sy;setPosition(item,g,tx,ty);return true;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:dragging=false;guideX=guideY=Float.NaN;getParent().requestDisallowInterceptTouchEvent(false);invalidate();if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;
                default:return dragging;}
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private void export(boolean all){if(busy||current==null)return;changed();Style config=style.copy();int start=all?0:index,end=all?photos.size():index+1;new TactileDialogBuilder(this).setTitle(all?"批次另存 "+(end-start)+" 張":"另存本張相片").setMessage("會使用原始解析度重新輸出 JPEG（品質 98），不會覆蓋原圖。外框會增加畫布尺寸。"+(config.geo?"\n會使用網路服務查詢尚未填寫的拍攝地名。":"")).setNegativeButton("取消",null).setPositiveButton("開始",(d,w)->doExport(config,start,end)).show();}
    private void doExport(Style cfg,int start,int end){setBusy(true);ArrayList<Photo> jobs=new ArrayList<>(photos.subList(start,end));worker.execute(()->{int ok=0,metaFailures=0;ArrayList<String> errors=new ArrayList<>();for(int j=0;j<jobs.size();j++){Photo p=jobs.get(j);int ordinal=start+j;final int progress=j+1;main.post(()->status.setText("輸出中 "+progress+" / "+jobs.size()));try{read(p);if(cfg.geo&&cfg.fields.contains(3)&&p.placeOverride==null&&p.v(3).isEmpty()){String name=geocode(p);if(!name.isEmpty())p.values.put(3,name);}boolean metadata=savePhoto(p,cfg,ordinal);ok++;if(!metadata)metaFailures++;}catch(Exception|OutOfMemoryError ex){errors.add((ordinal+1)+"："+ex.getClass().getSimpleName()+" "+String.valueOf(ex.getMessage()));}}final int success=ok,meta=metaFailures;main.post(()->{if(isFinishing())return;setBusy(false);status.setText("已儲存 "+success+" / "+jobs.size()+" 張");Photo p=active();if(p!=null&&p.placeOverride==null){binding=true;place.setText(p.v(3));binding=false;}preview.invalidate();String result="已儲存 "+success+" 張至相簿「ㄚ喬相片加字」。";if(meta>0)result+="\n其中 "+meta+" 張有部分或全部 EXIF 未能保留，已儲存加字相片。";if(!errors.isEmpty())result+="\n\n未完成：\n"+String.join("\n",errors);new TactileDialogBuilder(this).setTitle("輸出結果").setMessage(result).setPositiveButton("關閉",null).show();});});}
    private boolean savePhoto(Photo p,Style s,int ordinal)throws Exception{
        Bitmap b=null,out=null;File temp=null;Uri dest=null;boolean complete=false,metadataOk=true;
        try{b=decode(p.uri,0);Layout g=layout(b,p,s,ordinal);long bytes=(long)g.w*g.h*4;long max=Runtime.getRuntime().maxMemory();if(bytes>max*.70)throw new IOException("原圖加邊框後超過可用記憶體；未降低解析度");out=Bitmap.createBitmap(g.w,g.h,Bitmap.Config.ARGB_8888);draw(new Canvas(out),b,g,s);b.recycle();b=null;temp=File.createTempFile("ajo_photo_",".jpg",getCacheDir());try(FileOutputStream os=new FileOutputStream(temp)){if(!out.compress(Bitmap.CompressFormat.JPEG,98,os))throw new IOException("JPEG 編碼失敗");}out.recycle();out=null;
            if(s.keep){try{ExifInterface e=new ExifInterface(temp.getAbsolutePath());for(Map.Entry<String,String> tag:p.tags.entrySet()){String k=tag.getKey();if((s.strip&&k.startsWith("GPS"))||k.equals("ColorSpace"))continue;try{e.setAttribute(k,tag.getValue());}catch(Exception ignored){metadataOk=false;}}e.setAttribute("Orientation","1");e.setAttribute("ImageWidth",String.valueOf(g.w));e.setAttribute("ImageLength",String.valueOf(g.h));e.setAttribute("PixelXDimension",String.valueOf(g.w));e.setAttribute("PixelYDimension",String.valueOf(g.h));e.saveAttributes();}catch(Exception e){metadataOk=false;}}
            ContentValues v=new ContentValues();String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());v.put(MediaStore.Images.Media.DISPLAY_NAME,"AJO_"+stamp+"_"+String.format(Locale.US,"%03d",ordinal+1)+".jpg");v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ㄚ喬相片加字");v.put(MediaStore.Images.Media.IS_PENDING,1);dest=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(dest==null)throw new IOException("無法建立相簿檔案");try(InputStream in=new FileInputStream(temp);OutputStream os=getContentResolver().openOutputStream(dest)){if(os==null)throw new IOException("無法寫入相片");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)os.write(buffer,0,n);}v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);if(getContentResolver().update(dest,v,null,null)<1)throw new IOException("無法完成相簿儲存");complete=true;return metadataOk;
        }finally{if(!complete&&dest!=null)try{getContentResolver().delete(dest,null,null);}catch(Exception ignored){}if(temp!=null)temp.delete();if(b!=null)b.recycle();if(out!=null)out.recycle();}}
    private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){generation++;worker.shutdown();if(current!=null){current.recycle();current=null;}super.onDestroy();}
}
