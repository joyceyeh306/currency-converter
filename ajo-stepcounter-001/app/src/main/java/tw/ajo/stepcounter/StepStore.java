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
    private static final int VERSION = 2;
    private final Context context;

    public StepStore(Context c) {
        super(c.getApplicationContext(), DATABASE, null, VERSION);
        context = c.getApplicationContext();
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE days(day TEXT PRIMARY KEY NOT NULL, steps INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE state(k TEXT PRIMARY KEY NOT NULL, v TEXT NOT NULL)");
        createHourlyTable(db);
    }
    private static void createHourlyTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS hourly(" +
                "day TEXT NOT NULL, hour INTEGER NOT NULL, steps INTEGER NOT NULL DEFAULT 0," +
                "PRIMARY KEY(day,hour))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion==1 && newVersion>=2) {
            // Non-destructive migration: the entire 003 'days' and 'state' tables stay intact.
            createHourlyTable(db);
            return;
        }
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
    /** Intentionally paused recording must NOT count intervening steps on restart. */
    public static synchronized void clearBaseline(Context context) {
        StepStore store=new StepStore(context);
        SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("state","k IN (?,?)",new String[]{"boot","counter"});
            db.setTransactionSuccessful();
        } finally { db.endTransaction();store.close(); }
    }
    public synchronized void ensureToday() {
        SQLiteDatabase db=getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO days(day,steps) VALUES(?,0)",
                new Object[]{LocalDate.now().toString()});
    }
    /** 
     * One real STEP_DETECTOR event = one step at the sensor timestamp.
     * This writes an independent per-hour series WITHOUT changing the daily total.
     * Sensor gaps are not interpolated or backfilled.
     */
    public static synchronized void recordDetector(Context context,long eventElapsedNanos) {
        if(eventElapsedNanos<=0) return;
        long lag=SystemClock.elapsedRealtimeNanos()-eventElapsedNanos;
        if(lag<0 || lag>7L*86400000000000L) return;
        long eventTime=System.currentTimeMillis()-(lag/1000000L);
        java.time.ZonedDateTime when=Instant.ofEpochMilli(eventTime).atZone(ZoneId.systemDefault());
        String day=when.toLocalDate().toString();
        int hour=when.getHour();
        try(StepStore store=new StepStore(context)) {
            SQLiteDatabase db=store.getWritableDatabase();
            db.beginTransaction();
            try {
                // Maintain a day row even if the step detector fires before STEP_COUNTER.
                db.execSQL("INSERT OR IGNORE INTO days(day,steps) VALUES(?,0)",
                    new Object[]{day});
                db.execSQL("INSERT OR IGNORE INTO hourly(day,hour,steps) VALUES(?,?,0)",
                    new Object[]{day,hour});
                db.execSQL("UPDATE hourly SET steps=steps+1 WHERE day=? AND hour=?",
                    new Object[]{day,hour});
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
    }
    public synchronized long[] hourly(LocalDate date) {
        long[] h=new long[24];
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT hour,steps FROM hourly WHERE day=? ORDER BY hour",
                new String[]{date.toString()})) {
            while(c.moveToNext()) {
                int at=c.getInt(0);
                if(at>=0&&at<24) h[at]=c.getLong(1);
            }
        }
        return h;
    }
    public synchronized boolean hasHourly(LocalDate date) {
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT 1 FROM hourly WHERE day=? LIMIT 1",
                new String[]{date.toString()})) {
            return c.moveToFirst();
        }
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
        root.put("version",2);
        root.put("exported_at",Instant.now().toString());
        JSONArray days=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT day,steps FROM days ORDER BY day ASC",null)) {
            while(c.moveToNext()) {
                JSONObject d=new JSONObject();
                d.put("day",c.getString(0)); d.put("steps",c.getLong(1)); days.put(d);
            }
        }
        root.put("days",days);
        JSONArray hours=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT day,hour,steps FROM hourly ORDER BY day,hour",null)) {
            while(c.moveToNext()) {
                JSONObject point=new JSONObject();
                point.put("day",c.getString(0));
                point.put("hour",c.getInt(1));
                point.put("steps",c.getLong(2));
                hours.put(point);
            }
        }
        root.put("hourly",hours);
        return root;
    }
    /** Import replaces history. Baseline is discarded to prevent double-counting. */
    public static synchronized int restore(Context context,String json) throws JSONException {
        JSONObject root=new JSONObject(json);
        int backupVersion=root.optInt("version");
        if(!"ajo-stepcounter".equals(root.optString("format"))||(backupVersion!=1 && backupVersion!=2))
            throw new JSONException("檔案不是支援版本的ㄚ喬計步器備份");
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
        Map<String,Long> parsedHours=new HashMap<>();
        if(backupVersion==2) {
            JSONArray data=root.getJSONArray("hourly");
            if(data.length()>720000)throw new JSONException("時段資料筆數過多");
            for(int i=0;i<data.length();i++) {
                JSONObject point=data.getJSONObject(i);
                String date=point.getString("day");
                int hour=point.getInt("hour");
                long steps=point.getLong("steps");
                try {
                    if(!LocalDate.parse(date).toString().equals(date))
                        throw new IllegalArgumentException("Invalid date");
                } catch(Exception invalid) {
                    throw new JSONException("時段日期無效: "+date);
                }
                if(hour<0||hour>23||steps<0||steps>10000000L)
                    throw new JSONException("時段資料無效");
                if(!parsed.containsKey(date))throw new JSONException("時段日期沒有每日資料: "+date);
                String key=date+"|"+hour;
                if(parsedHours.put(key,steps)!=null)throw new JSONException("時段紀錄重複");
            }
        }
        StepStore store=new StepStore(context);
        SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("days",null,null);
            db.delete("state",null,null);
            db.delete("hourly",null,null);
            for(Map.Entry<String,Long> it:parsed.entrySet()) {
                ContentValues row=new ContentValues();
                row.put("day",it.getKey()); row.put("steps",it.getValue());
                db.insertOrThrow("days",null,row);
            }
            for(Map.Entry<String,Long> it:parsedHours.entrySet()) {
                String[] parts=it.getKey().split("\\|");
                ContentValues row=new ContentValues();
                row.put("day",parts[0]);
                row.put("hour",Integer.parseInt(parts[1]));
                row.put("steps",it.getValue());
                db.insertOrThrow("hourly",null,row);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction();store.close(); }
        return parsed.size();
    }
}
