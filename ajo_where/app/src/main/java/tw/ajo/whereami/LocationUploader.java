package tw.ajo.whereami;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.BatteryManager;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class LocationUploader {
    private LocationUploader() {}

    public static boolean upload(Context context, Location loc) {
        Context app = context.getApplicationContext();
        if (!Prefs.running(app) || loc == null) return false;

        try {
            long now = System.currentTimeMillis();
            long fixTime = loc.getTime() > 0 ? loc.getTime() : now;

            JSONObject j = new JSONObject();
            j.put("lat", loc.getLatitude());
            j.put("lon", loc.getLongitude());
            j.put("accuracy", loc.hasAccuracy() ? loc.getAccuracy() : JSONObject.NULL);
            j.put("speed", loc.hasSpeed() ? loc.getSpeed() : JSONObject.NULL);
            j.put("battery", batteryPercent(app));
            j.put("time", fixTime);

            URL u = new URL("https://ntfy.sh/" + Prefs.topic(app) + "/ajo-location");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(12_000);
            c.setReadTimeout(12_000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            c.setRequestProperty("Title", "ajo-location");
            c.setRequestProperty("Firebase", "no");

            byte[] body = j.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = c.getOutputStream()) { os.write(body); }

            int code = c.getResponseCode();
            c.disconnect();

            if (code >= 200 && code < 300) {
                Prefs.saveLast(app, now, fixTime, loc.getLatitude(), loc.getLongitude(),
                        loc.hasAccuracy() ? loc.getAccuracy() : 0f);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static int batteryPercent(Context c) {
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return -1;
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
