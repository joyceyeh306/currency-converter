package tw.ajo.stepcounter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Space;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/** One offline, dependency-free Activity. No location/network permission needed. */
public final class MainActivity extends Activity {
    private static final int PURPLE=Color.rgb(123,85,173);
    private static final int INK=Color.rgb(61,52,75);
    private static final int MUTED=Color.rgb(120,109,131);
    private static final int PINK=Color.rgb(255,241,247);
    private static final int CREAM=Color.rgb(255,248,226);
    private static final int LILAC=Color.rgb(242,236,255);
    private static final int WHITE=Color.WHITE;
    private static final int ACTIVITY_REQ=100, NOTICE_REQ=101,EXPORT_REQ=102, IMPORT_REQ=103;

    private LinearLayout container;
    private LinearLayout content;
    private LinearLayout nav;
    private ScrollView scroller;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int tab=0;
    private YearMonth month=YearMonth.now();
    private long lastToday=-1;
    private String lastStatus="";
    private LocalDate lastDate=LocalDate.now();
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if(tab==0) {
                try(StepStore s=new StepStore(MainActivity.this)) {
                    long current=s.day(LocalDate.now());
                    String status=statusText();
                    if(current!=lastToday||!status.equals(lastStatus)||!lastDate.equals(LocalDate.now())) render();
                } catch(Exception ignored) {}
            }
            handler.postDelayed(this,3000);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(255,249,248));
        getWindow().setNavigationBarColor(Color.rgb(255,249,248));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildShell();render();
        if(!hasSensor()) toast("找不到硬體計步器");
        else requestActivityIfNeeded();
    }
    @Override public void onResume() {
        super.onResume();
        if(StepService.enabled(this)&&hasActivityPermission()) StepService.start(this);
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh,1000);
        if(content!=null) render();
    }
    @Override public void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
    private boolean hasSensor() {
        SensorManager s=(SensorManager)getSystemService(SENSOR_SERVICE);
        return s!=null && s.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!=null;
    }
    private boolean hasActivityPermission() {
        return checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED;
    }
    private void requestActivityIfNeeded() {
        if(!hasActivityPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},ACTIVITY_REQ);
        } else {
            if(!StepService.enabled(this)) {
                // First install starts recording; a user-chosen pause is respected.
                boolean opened=getSharedPreferences("ajo",MODE_PRIVATE).getBoolean("initialized",false);
                if(!opened) {
                    getSharedPreferences("ajo",MODE_PRIVATE).edit().putBoolean("initialized",true).apply();
                    StepService.setEnabled(this,true);
                    StepService.start(this);
                }
            }
            requestNotificationsIfNeeded();
        }
    }
    private void requestNotificationsIfNeeded() {
        if(Build.VERSION.SDK_INT>=33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                && !getSharedPreferences("ajo",MODE_PRIVATE).getBoolean("notifications_asked",false)) {
            getSharedPreferences("ajo",MODE_PRIVATE).edit().putBoolean("notifications_asked",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTICE_REQ);
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] results) {
        super.onRequestPermissionsResult(code,perms,results);
        if(code==ACTIVITY_REQ) {
            if(hasActivityPermission()) {
                getSharedPreferences("ajo",MODE_PRIVATE).edit().putBoolean("initialized",true).apply();
                StepService.setEnabled(this,true);
                StepService.start(this);
                requestNotificationsIfNeeded();
            } else toast("未授權身體活動，無法讀取硬體步數");
        }
        render();
    }
    private int dp(float size) { return (int)(getResources().getDisplayMetrics().density*size+0.5f); }
    private GradientDrawable bg(int fill,int radius,int border) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(radius));
        if(border!=0)d.setStroke(dp(1),border);
        return d;
    }
    private TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setIncludeFontPadding(true);
        return t;
    }
    private void add(LinearLayout parent,View child,int w,int h) {
        parent.addView(child,new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)));
    }
    private void gap(LinearLayout parent,int height) {
        Space s=new Space(this);add(parent,s,1,height);
    }
    private LinearLayout row() {
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;
    }
    private LinearLayout col() {
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;
    }
    private LinearLayout card(int color) {
        LinearLayout c=col();
        c.setPadding(dp(18),dp(17),dp(18),dp(17));
        c.setBackground(bg(color,24,0));
        return c;
    }
    private void fullCard(LinearLayout c) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(14);content.addView(c,p);
    }
    private Button btn(String label,int background,boolean accent,Runnable click) {
        Button b=new Button(this);
        b.setText(label);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(accent?WHITE:PURPLE);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setPadding(dp(10),0,dp(10),0);
        b.setMinHeight(0);b.setMinimumHeight(0);
        b.setBackground(bg(background,18,0));
        b.setHapticFeedbackEnabled(true);
        b.setOnClickListener(v->{
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            click.run();
        });
        return b;
    }
    private void buildShell() {
        container=col();container.setBackgroundColor(Color.rgb(255,249,248));
        container.setPadding(dp(18),dp(12),dp(18),0);
        setContentView(container);
        LinearLayout header=row();
        TextView title=text("♡ ㄚ喬計步器",23,PURPLE,true);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        TextView v=text("001",12,MUTED,false);
        header.addView(v);
        add(container,header,-1,-2);
        scroller=new ScrollView(this);scroller.setFillViewport(false);scroller.setVerticalScrollBarEnabled(false);
        content=col();content.setPadding(0,dp(10),0,dp(4));
        scroller.addView(content);
        container.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        nav=row();nav.setPadding(0,dp(8),0,dp(12));
        add(container,nav,-1,-2);
    }
    private void render() {
        if(content==null)return;
        int oldScroll=scroller.getScrollY();
        content.removeAllViews();
        if(tab==0) home();
        else if(tab==1) history();
        else backup();
        nav.removeAllViews();
        String[] names={"今日","歷史紀錄","備份"};
        for(int i=0;i<names.length;i++) {
            final int dest=i;
            Button b=btn(names[i],tab==i?PURPLE:LILAC,tab==i,()->{
                tab=dest;month=YearMonth.now();render();scroller.smoothScrollTo(0,0);
            });
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(47),1);
            if(i>0)params.leftMargin=dp(8);
            nav.addView(b,params);
        }
        if(tab==0) scroller.post(()->scroller.scrollTo(0,Math.min(oldScroll,dp(1000))));
        lastDate=LocalDate.now();
        try(StepStore s=new StepStore(this)) {
            lastToday=s.day(LocalDate.now());lastStatus=statusText();
        }
    }
    private String fmt(long number) { return NumberFormat.getIntegerInstance(Locale.TAIWAN).format(number); }
    private String statusText() {
        if(!hasActivityPermission())return "尚未取得身體活動權限";
        if(!StepService.enabled(this))return "目前已暫停記錄";
        if(StepService.running&&StepService.sensorReady)return "硬體計步器記錄中";
        return "背景紀錄未啟動，請重新啟動";
    }
    private void home() {
        LocalDate today=LocalDate.now();
        long steps,week,mon,updated;
        String warning;
        try(StepStore s=new StepStore(this)) {
            steps=s.day(today);
            week=s.sum(today.minusDays(6),today);
            mon=s.sum(today.withDayOfMonth(1),today);
            updated=s.lastSample(); warning=s.warningDate();
        }
        LinearLayout hero=card(PINK);
        TextView date=text(today.getMonthValue()+"月"+today.getDayOfMonth()+"日 · 今天",15,PURPLE,true);
        date.setGravity(Gravity.CENTER);add(hero,date,-1,34);
        TextView number=text(fmt(steps),64,INK,true);
        number.setGravity(Gravity.CENTER);add(hero,number,-1,-2);
        TextView unit=text("步",18,PURPLE,true);unit.setGravity(Gravity.CENTER);add(hero,unit,-1,30);
        gap(hero,8);
        TextView status=text("● "+statusText(),13,StepService.running&&StepService.sensorReady?PURPLE:MUTED,false);
        status.setGravity(Gravity.CENTER);add(hero,status,-1,34);
        fullCard(hero);

        LinearLayout stats=row();
        LinearLayout left=metric("最近 7 天",fmt(week)+" 步",LILAC);
        LinearLayout right=metric("本月累計",fmt(mon)+" 步",CREAM);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.rightMargin=dp(6);stats.addView(left,lp);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);
        rp.leftMargin=dp(6);stats.addView(right,rp);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
        sp.bottomMargin=dp(14);content.addView(stats,sp);

        LinearLayout graph=card(WHITE);
        TextView label=text("最近七天的小腳印",17,INK,true);
        add(graph,label,-1,34);
        long max=1;
        long[] vals=new long[7];
        for(int i=0;i<7;i++) {
            try(StepStore s=new StepStore(this)) { vals[i]=s.day(today.minusDays(6-i));}
            max=Math.max(max,vals[i]);
        }
        LinearLayout bars=row();bars.setGravity(Gravity.BOTTOM);
        for(int i=0;i<7;i++) {
            LinearLayout column=col();column.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(137),1);
            if(i>0)cp.leftMargin=dp(4);
            bars.addView(column,cp);
            TextView value=text(vals[i]>0?fmt(vals[i]):"·",10,MUTED,false);
            value.setGravity(Gravity.CENTER);add(column,value,-1,23);
            Space spacer=new Space(this);column.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
            View bar=new View(this);
            bar.setBackground(bg(i==6?Color.rgb(238,154,193):Color.rgb(182,155,232),8,0));
            add(column,bar,dp(17),vals[i]==0?5:Math.max(9,(int)(78.0*vals[i]/max)));
            gap(column,4);
            LocalDate day=today.minusDays(6-i);
            TextView dayLabel=text(day.getDayOfMonth()+"",11,MUTED,false);
            dayLabel.setGravity(Gravity.CENTER);add(column,dayLabel,-1,23);
        }
        add(graph,bars,-1,-2);
        fullCard(graph);

        LinearLayout state=card(LILAC);
        TextView caption=text("可靠記錄小提醒",15,PURPLE,true);add(state,caption,-1,31);
        String details="計步資料只保存在這支手機。第一次啟用時先建立硬體基準，安裝前的步數不會自動補入。";
        if(today.toString().equals(warning))
            details+="\n有一筆資料跨越午夜才送達，該段步數可能無法精準切分。";
        TextView t=text(details,13,INK,false);t.setLineSpacing(dp(3),1f);add(state,t,-1,-2);
        gap(state,12);
        Button start=btn(!StepService.enabled(this)?"開始計步":
            StepService.running&&StepService.sensorReady?"暫停記錄":"重新啟動記錄",
            PURPLE,true,()->toggleTracking());
        add(state,start,-1,47);
        fullCard(state);
    }
    private LinearLayout metric(String name,String value,int color) {
        LinearLayout c=card(color);
        TextView a=text(name,13,MUTED,false);add(c,a,-1,25);
        TextView b=text(value,20,INK,true);
        b.setSingleLine(true);b.setTextSize(19);add(c,b,-1,32);
        return c;
    }
    private void toggleTracking() {
        if(!hasActivityPermission()) {
            requestActivityIfNeeded();return;
        }
        if(!hasSensor()){toast("找不到硬體計步器");return;}
        if(StepService.enabled(this)&&StepService.running&&StepService.sensorReady) {
            StepService.setEnabled(this,false);StepService.stop(this);
            toast("計步已暫停");
        } else {
            StepService.setEnabled(this,true);
            StepService.start(this);
            toast("已要求啟用計步");
        }
        render();
    }
    private void history() {
        LocalDate now=LocalDate.now();
        long week;
        try(StepStore s=new StepStore(this)){week=s.sum(now.minusDays(6),now);}
        LinearLayout top=card(LILAC);
        add(top,text("近七天總步數",15,MUTED,false),-1,28);
        add(top,text(fmt(week)+" 步",27,INK,true),-1,40);
        fullCard(top);

        LinearLayout chooser=row();
        Button previous=btn("‹ 上個月",PINK,false,()->{month=month.minusMonths(1);render();});
        chooser.addView(previous,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView center=text(month.getYear()+"年 "+month.getMonthValue()+"月",17,INK,true);
        center.setGravity(Gravity.CENTER);
        chooser.addView(center,new LinearLayout.LayoutParams(0,dp(46),1.35f));
        Button next=btn("下個月 ›",PINK,false,()->{month=month.plusMonths(1);render();});
        next.setEnabled(!month.equals(YearMonth.now()));
        chooser.addView(next,new LinearLayout.LayoutParams(0,dp(46),1));
        LinearLayout.LayoutParams chp=new LinearLayout.LayoutParams(-1,-2);chp.bottomMargin=dp(12);
        content.addView(chooser,chp);

        long total;
        try(StepStore s=new StepStore(this)){
            total=s.sum(month.atDay(1),month.atEndOfMonth());
        }
        LinearLayout summary=card(CREAM);
        TextView title=text("本頁月份累計",14,MUTED,false);add(summary,title,-1,26);
        TextView totalText=text(fmt(total)+" 步",28,INK,true);add(summary,totalText,-1,42);
        fullCard(summary);

        LinearLayout days=card(WHITE);
        add(days,text("每日紀錄",17,INK,true),-1,38);
        boolean any=false;
        try(StepStore s=new StepStore(this)) {
            for(int date=month.lengthOfMonth();date>=1;date--) {
                LocalDate d=month.atDay(date);
                if(d.isAfter(now))continue;
                long count=s.day(d);
                LinearLayout line=row();
                TextView label=text(d.getMonthValue()+"/"+d.getDayOfMonth()+"  週"+weekday(d),14,INK,false);
                line.addView(label,new LinearLayout.LayoutParams(0,dp(46),1));
                TextView value=text(fmt(count)+" 步",15,count>0?PURPLE:MUTED,count>0);
                value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                add(line,value,-2,46);
                add(days,line,-1,-2);
                any=true;
            }
        }
        if(!any)add(days,text("這個月份還沒有紀錄",14,MUTED,false),-1,48);
        fullCard(days);
    }
    private String weekday(LocalDate d) {
        return "一二三四五六日".substring(d.getDayOfWeek().getValue()-1,d.getDayOfWeek().getValue());
    }
    private void backup() {
        LinearLayout info=card(PINK);
        add(info,text("我的步數日記",20,PURPLE,true),-1,44);
        TextView message=text("所有資料只存在手機的 SQLite 資料庫，不需要登入、網路或雲端。建議定期匯出 JSON，另存到妳信任的位置。",14,INK,false);
        message.setLineSpacing(dp(5),1f);add(info,message,-1,-2);
        fullCard(info);

        LinearLayout export=card(WHITE);
        add(export,text("匯出備份",18,INK,true),-1,37);
        add(export,text("包含已記錄的每一天步數，可自行選擇儲存位置。",13,MUTED,false),-1,-2);
        gap(export,13);
        Button ex=btn("匯出 JSON 備份",PURPLE,true,()->exportFile());
        add(export,ex,-1,50);
        fullCard(export);

        LinearLayout restore=card(CREAM);
        add(restore,text("還原歷史紀錄",18,INK,true),-1,37);
        TextView caution=text("還原會取代本機全部每日紀錄。原本的感測器累積基準會清除，下一次硬體讀取只建立新基準，避免重複計步。",13,INK,false);
        caution.setLineSpacing(dp(4),1f);add(restore,caution,-1,-2);
        gap(restore,13);
        Button im=btn("選擇 JSON 檔案還原",PURPLE,true,()->confirmImport());
        add(restore,im,-1,50);
        fullCard(restore);

        LinearLayout note=card(LILAC);
        add(note,text("關於背景計步",16,PURPLE,true),-1,35);
        TextView details=text("系統會顯示「ㄚ喬計步器 正在記錄」的低干擾通知。請允許身體活動權限。若 ColorOS 限制背景執行、強制停止 App、重開機後未允許自啟動，可能漏記步數。",13,INK,false);
        details.setLineSpacing(dp(4),1f);add(note,details,-1,-2);
        fullCard(note);
    }
    private void exportFile() {
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"ㄚ喬計步器_備份_"+LocalDate.now()+".json");
        startActivityForResult(intent,EXPORT_REQ);
    }
    private void confirmImport() {
        new AlertDialog.Builder(this)
            .setTitle("確定要還原嗎？")
            .setMessage("將用備份檔中的每日步數取代目前的全部紀錄。建議先匯出現有資料。")
            .setPositiveButton("選擇檔案",(d,w)->{
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                startActivityForResult(intent,IMPORT_REQ);
            })
            .setNegativeButton("取消",null)
            .show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        try {
            if(request==EXPORT_REQ) {
                String serialized;
                try(StepStore s=new StepStore(this)){serialized=s.backupJson().toString(2);}
                try(OutputStreamWriter writer=new OutputStreamWriter(
                        getContentResolver().openOutputStream(data.getData()),StandardCharsets.UTF_8)) {
                    writer.write(serialized);
                }
                toast("備份完成");
            } else if(request==IMPORT_REQ) {
                StringBuilder sb=new StringBuilder();
                try(InputStream input=getContentResolver().openInputStream(data.getData());
                    BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))) {
                    char[] buffer=new char[4096];int n;int total=0;
                    while((n=reader.read(buffer))!=-1){
                        total+=n;
                        if(total>5_000_000)throw new IllegalStateException("備份檔超過 5 MB");
                        sb.append(buffer,0,n);
                    }
                }
                int count=StepStore.restore(this,sb.toString());
                StepWidget.updateAll(this);
                toast("已還原 "+count+" 天紀錄");
                render();
            }
        } catch(Exception e) {
            new AlertDialog.Builder(this).setTitle("操作未完成")
                .setMessage(e.getMessage()==null?"請檢查檔案":e.getMessage())
                .setPositiveButton("確定",null).show();
        }
    }
    private void toast(String str) { Toast.makeText(this,str,Toast.LENGTH_LONG).show(); }
}
