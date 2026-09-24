package tw.ajo.whereami;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;

public final class Prefs {
    private static final String FILE = "ajo_where_prefs";
    private static SharedPreferences p(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    private Prefs() {}

    public static String topic(Context c) {
        String v = p(c).getString("topic", null);
        if (v == null || v.length() < 32) {
            v = newTopic();
            p(c).edit().putString("topic", v).apply();
        }
        return v;
    }

    public static String regenerateTopic(Context c) {
        String v = newTopic();
        p(c).edit().putString("topic", v).apply();
        return v;
    }

    private static String newTopic() {
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder("ajo-");
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    public static int intervalMin(Context c) { return p(c).getInt("interval_min", 3); }
    public static void setIntervalMin(Context c, int v) { p(c).edit().putInt("interval_min", v).apply(); }
    public static boolean running(Context c) { return p(c).getBoolean("running", false); }
    public static void setRunning(Context c, boolean v) { p(c).edit().putBoolean("running", v).apply(); }

    public static void saveLast(Context c, long time, double lat, double lon, float accuracy) {
        p(c).edit()
                .putLong("last_upload", time)
                .putString("last_lat", Double.toString(lat))
                .putString("last_lon", Double.toString(lon))
                .putFloat("last_accuracy", accuracy)
                .apply();
    }

    public static long lastUpload(Context c) { return p(c).getLong("last_upload", 0L); }
    public static String lastLat(Context c) { return p(c).getString("last_lat", ""); }
    public static String lastLon(Context c) { return p(c).getString("last_lon", ""); }
    public static float lastAccuracy(Context c) { return p(c).getFloat("last_accuracy", 0f); }
}
