package tw.ajo.stepcounter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/** All counts and sensor baselines are saved in the SAME SQLite transaction. */
public final class StepStore extends SQLiteOpenHelper {
    private static final String DATABASE = "ajo_steps.db";
    private static final int VERSION = 1;
    private final Context context;

    public StepStore(Context c) {
        super(c.getApplicationContext(), DATABASE, null, VERSION);
        context = c.getApplicationContext();
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE days(day TEXT PRIMARY KEY NOT NULL, steps INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE state(k TEXT PRIMARY KEY NOT NULL, v TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("No migration defined: "+oldVersion+" to "+newVersion);
    }
    private static String getState(SQLiteDatabase db, String key) {
        try (Cursor c=db.rawQuery("SELECT v FROM state WHERE k=?",new String[]{key})) {
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    private static void putState(SQLiteDatabase db, String key, String value) {
        ContentValues cv=new ContentValues();
        cv.put("k",key); cv.put("v",value);
        db.insertWithOnConflict("state",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }
    private static long parseLong(String s,long fallback) {
        try { return Long.parseLong(s); } catch(Exception e) { return fallback; }
    }
    private String bootToken() {
        try {
            int count=Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1);
            if(count>=0) return "b:"+count;
        } catch(Exception ignored) {}
        // Fallback for OEMs hiding BOOT_COUNT. Larger clock jumps intentionally rebase.
        long approx=System.currentTimeMillis()-SystemClock.elapsedRealtime();
        return "t:"+Math.round((double)approx/600000.0);
    }
    /** Timestamp belongs to the LAST step of the aggregated event, not delivery. */
    public static synchronized long record(Context context,long counter,long eventElapsedNanos) {
        if(counter<0) return 0;
        StepStore store=new StepStore(context);
        SQLiteDatabase db=store.getWritableDatabase();
        long now=System.currentTimeMillis();
        long ageNanos=SystemClock.elapsedRealtimeNanos()-eventElapsedNanos;
        long age=eventElapsedNanos>0 && ageNanos>=0
                ? Math.min(ageNanos/1000000L,7L*86400000L):0;
        long eventTime=now-age;
        String day=Instant.ofEpochMilli(eventTime).atZone(ZoneId.systemDefault()).toLocalDate().toString();
        String boot=store.bootToken();
        long total;
        db.beginTransaction();
        try {
            String oldBoot=getState(db,"boot");
            long oldCounter=parseLong(getState(db,"counter"),-1);
            long delta=StepMath.newSteps(oldBoot,oldCounter,boot,counter);
            String oldDay=getState(db,"day");
            db.execSQL("INSERT OR IGNORE INTO days(day,steps) VALUES(?,0)",new Object[]{day});
            if(delta>0) {
                db.execSQL("UPDATE days SET steps=steps+? WHERE day=?",new Object[]{delta,day});
                if(!oldDay.isEmpty()&&!oldDay.equals(day))
                    putState(db,"boundary_warning",day);
            }
            putState(db,"boot",boot);
            putState(db,"counter",Long.toString(counter));
            putState(db,"day",day);
            putState(db,"sample_time",Long.toString(eventTime));
            db.setTransactionSuccessful();
            total=readDay(db,LocalDate.now().toString());
        } finally { db.endTransaction(); store.close(); }
        return total;
    }
    private static long readDay(SQLiteDatabase db,String day) {
        try(Cursor c=db.rawQuery("SELECT steps FROM days WHERE day=?",new String[]{day})) {
            return c.moveToFirst()?c.getLong(0):0;
        }
    }
    public synchronized void ensureToday() {
        SQLiteDatabase db=getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO days(day,steps) VALUES(?,0)",
                new Object[]{LocalDate.now().toString()});
    }
    public synchronized boolean hasDay(LocalDate date) {
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT 1 FROM days WHERE day=?",new String[]{date.toString()})) {
            return c.moveToFirst();
        }
    }
    public synchronized long day(LocalDate date) {
        SQLiteDatabase db=getReadableDatabase();
        return readDay(db,date.toString());
    }
    public synchronized long sum(LocalDate from,LocalDate to) {
        SQLiteDatabase db=getReadableDatabase();
        try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(steps),0) FROM days WHERE day>=? AND day<=?",
                new String[]{from.toString(),to.toString()})) {
            return c.moveToFirst()?c.getLong(0):0;
        }
    }
    public synchronized long lastSample() {
        return parseLong(getState(getReadableDatabase(),"sample_time"),0);
    }
    public synchronized String warningDate() {
        return getState(getReadableDatabase(),"boundary_warning");
    }
    public synchronized JSONObject backupJson() throws JSONException {
        SQLiteDatabase db=getReadableDatabase();
        JSONObject root=new JSONObject();
        root.put("format","ajo-stepcounter");
        root.put("version",1);
        root.put("exported_at",Instant.now().toString());
        JSONArray days=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT day,steps FROM days ORDER BY day ASC",null)) {
            while(c.moveToNext()) {
                JSONObject d=new JSONObject();
                d.put("day",c.getString(0)); d.put("steps",c.getLong(1)); days.put(d);
            }
        }
        root.put("days",days);
        return root;
    }
    /** Import replaces history. Baseline is discarded to prevent double-counting. */
    public static synchronized int restore(Context context,String json) throws JSONException {
        JSONObject root=new JSONObject(json);
        if(!"ajo-stepcounter".equals(root.optString("format"))||root.optInt("version")!=1)
            throw new JSONException("檔案不是ㄚ喬計步器備份");
        JSONArray days=root.getJSONArray("days");
        if(days.length()>30000) throw new JSONException("紀錄筆數過多");
        Map<String,Long> parsed=new HashMap<>();
        for(int i=0;i<days.length();i++) {
            JSONObject d=days.getJSONObject(i);
            String date=d.getString("day");
            if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))
                throw new JSONException("日期格式有誤");
            try {
                if(!LocalDate.parse(date).toString().equals(date))
                    throw new IllegalArgumentException("date");
            } catch(Exception e) { throw new JSONException("日期無效: "+date); }
            long steps=d.getLong("steps");
            if(steps<0||steps>10000000L) throw new JSONException("步數無效: "+date);
            if(parsed.containsKey(date)) throw new JSONException("重複日期: "+date);
            parsed.put(date,steps);
        }
        StepStore store=new StepStore(context);
        SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("days",null,null);
            db.delete("state",null,null);
            for(Map.Entry<String,Long> it:parsed.entrySet()) {
                ContentValues row=new ContentValues();
                row.put("day",it.getKey()); row.put("steps",it.getValue());
                db.insertOrThrow("days",null,row);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction();store.close(); }
        return parsed.size();
    }
}
