package tw.ajo.photomanager;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.exifinterface.media.ExifInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_PERMISSION = 4101;
    private static final int REQ_WRITE = 4102;
    private WebView webView;
    private JSONArray pendingIds;
    private JSONObject pendingAction;

    private final SimpleDateFormat exifDateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
    private final SimpleDateFormat fileDateMinute = new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US);
    private final SimpleDateFormat fileDateSecond = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US);
    private final Pattern compact14 = Pattern.compile("((?:19|20)\\d{12})");
    private final Pattern compact12 = Pattern.compile("((?:19|20)\\d{10})");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                notifyPermissionState();
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private Uri imagesUri() {
        if (Build.VERSION.SDK_INT >= 29) {
            return MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        }
        return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    }

    private Uri uriForId(long id) {
        return ContentUris.withAppendedId(imagesUri(), id);
    }

    private boolean hasReadPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void notifyPermissionState() {
        runOnUiThread(() -> webView.evaluateJavascript(
                "window.onPermissionChanged && window.onPermissionChanged(" + (hasReadPermission() ? "true" : "false") + ");",
                null));
    }

    private void requestPermissionsNow() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        }
        if (list.isEmpty()) {
            notifyPermissionState();
            return;
        }
        requestPermissions(list.toArray(new String[0]), REQ_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION) notifyPermissionState();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_WRITE) {
            if (resultCode == RESULT_OK) {
                executePendingAction();
            } else {
                sendActionResult(errorResult("已取消系統寫入授權"));
            }
        }
    }

    private JSONObject errorResult(String msg) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", false);
            o.put("message", msg);
        } catch (JSONException ignored) {}
        return o;
    }

    private void sendActionResult(JSONObject result) {
        String quoted = JSONObject.quote(result.toString());
        runOnUiThread(() -> webView.evaluateJavascript(
                "window.onActionResult && window.onActionResult(JSON.parse(" + quoted + "));", null));
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private long exifDateToMillis(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            Date d = exifDateFormat.parse(value);
            return d == null ? 0 : d.getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    private Date parseFilenameDate(String name) {
        if (name == null) return null;
        String base = name;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);

        String digits = base.replaceAll("\\D", "");
        Matcher m14 = compact14.matcher(digits);
        if (m14.find()) {
            try {
                return new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(m14.group(1));
            } catch (ParseException ignored) {}
        }
        Matcher m12 = compact12.matcher(digits);
        if (m12.find()) {
            try {
                return new SimpleDateFormat("yyyyMMddHHmm", Locale.US).parse(m12.group(1));
            } catch (ParseException ignored) {}
        }
        return null;
    }

    private String extensionOf(String name, String mime) {
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) return name.substring(dot);
        }
        if ("image/png".equalsIgnoreCase(mime)) return ".PNG";
        if ("image/webp".equalsIgnoreCase(mime)) return ".WEBP";
        if ("image/heic".equalsIgnoreCase(mime) || "image/heif".equalsIgnoreCase(mime)) return ".HEIC";
        return ".JPG";
    }

    private boolean editableMetadataMime(String mime) {
        if (mime == null) return false;
        mime = mime.toLowerCase(Locale.US);
        return mime.equals("image/jpeg") || mime.equals("image/jpg")
                || mime.equals("image/png") || mime.equals("image/webp");
    }

    private JSONObject mediaRow(long id) {
        JSONObject o = new JSONObject();
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
        };
        try (Cursor c = getContentResolver().query(uriForId(id), projection, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                o.put("id", id);
                o.put("name", safe(c.getString(1)));
                o.put("dateTaken", c.isNull(2) ? 0 : c.getLong(2));
                o.put("dateAdded", c.isNull(3) ? 0 : c.getLong(3) * 1000L);
                o.put("dateModified", c.isNull(4) ? 0 : c.getLong(4) * 1000L);
                o.put("mime", safe(c.getString(5)));
                o.put("size", c.isNull(6) ? 0 : c.getLong(6));
                o.put("width", c.isNull(7) ? 0 : c.getInt(7));
                o.put("height", c.isNull(8) ? 0 : c.getInt(8));
                o.put("uri", uriForId(id).toString());
            }
        } catch (Exception e) {
            try { o.put("error", e.getMessage()); } catch (JSONException ignored) {}
        }
        return o;
    }

    private JSONObject exifInfo(long id) {
        JSONObject o = mediaRow(id);
        Uri uri = uriForId(id);
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) return o;
            ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());
            putExif(o, "dateTimeOriginal", exif, ExifInterface.TAG_DATETIME_ORIGINAL);
            putExif(o, "dateTimeDigitized", exif, ExifInterface.TAG_DATETIME_DIGITIZED);
            putExif(o, "offsetTimeOriginal", exif, ExifInterface.TAG_OFFSET_TIME_ORIGINAL);
            putExif(o, "make", exif, ExifInterface.TAG_MAKE);
            putExif(o, "model", exif, ExifInterface.TAG_MODEL);
            putExif(o, "lensModel", exif, ExifInterface.TAG_LENS_MODEL);
            putExif(o, "focalLength", exif, ExifInterface.TAG_FOCAL_LENGTH);
            putExif(o, "focal35", exif, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM);
            putExif(o, "fNumber", exif, ExifInterface.TAG_F_NUMBER);
            putExif(o, "exposureTime", exif, ExifInterface.TAG_EXPOSURE_TIME);
            putExif(o, "iso", exif, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
            putExif(o, "exposureBias", exif, ExifInterface.TAG_EXPOSURE_BIAS_VALUE);
            putExif(o, "whiteBalance", exif, ExifInterface.TAG_WHITE_BALANCE);
            putExif(o, "flash", exif, ExifInterface.TAG_FLASH);
            putExif(o, "imageDescription", exif, ExifInterface.TAG_IMAGE_DESCRIPTION);
            putExif(o, "artist", exif, ExifInterface.TAG_ARTIST);
            putExif(o, "copyright", exif, ExifInterface.TAG_COPYRIGHT);

            float[] ll = new float[2];
            if (exif.getLatLong(ll)) {
                o.put("hasGps", true);
                o.put("lat", ll[0]);
                o.put("lon", ll[1]);
                double altitude = exif.getAltitude(Double.NaN);
                if (!Double.isNaN(altitude)) o.put("altitude", altitude);
            } else {
                o.put("hasGps", false);
            }
            String mime = o.optString("mime", "");
            o.put("metadataWritable", editableMetadataMime(mime));
            long albumMs = o.optLong("dateTaken", 0);
            java.util.TimeZone tz = java.util.TimeZone.getDefault();
            o.put("deviceTimeZoneId", tz.getID());
            o.put("deviceOffsetMinutes", tz.getOffset(albumMs > 0 ? albumMs : System.currentTimeMillis()) / 60000);
        } catch (Exception e) {
            try { o.put("exifError", e.getMessage()); } catch (JSONException ignored) {}
        }
        return o;
    }

    private void putExif(JSONObject o, String key, ExifInterface exif, String tag) throws JSONException {
        String v = exif.getAttribute(tag);
        if (v != null) o.put(key, v);
    }

    private JSONObject previewField(String label, String before, String after) {
        JSONObject f = new JSONObject();
        try {
            f.put("label", label);
            f.put("before", safe(before));
            f.put("after", safe(after));
        } catch (JSONException ignored) {}
        return f;
    }

    private JSONObject buildPreview(JSONArray ids, JSONObject action) {
        JSONObject result = new JSONObject();
        JSONArray items = new JSONArray();
        int changed = 0, unchanged = 0, skipped = 0;
        String type = action.optString("type", "");

        try {
            Map<String, Integer> duplicates = new HashMap<>();
            Double copyLat = null, copyLon = null;
            if ("gpsCopy".equals(type) && ids.length() > 0) {
                JSONObject src = exifInfo(ids.optLong(0));
                if (src.optBoolean("hasGps")) {
                    copyLat = src.optDouble("lat");
                    copyLon = src.optDouble("lon");
                }
            }

            for (int i = 0; i < ids.length(); i++) {
                long id = ids.optLong(i, -1);
                if (id < 0) continue;
                JSONObject before = exifInfo(id);
                JSONObject p = new JSONObject();
                JSONArray fields = new JSONArray();
                String name = before.optString("name", "");
                String mime = before.optString("mime", "");
                String status = "change";
                String message = "";

                p.put("id", id);
                p.put("name", name);
                p.put("uri", before.optString("uri", ""));

                if ("rename".equals(type)) {
                    String source = action.optString("source", "filename");
                    Date d = null;
                    if ("filename".equals(source)) d = parseFilenameDate(name);
                    if ("exif".equals(source)) {
                        long ms = exifDateToMillis(before.optString("dateTimeOriginal"));
                        if (ms > 0) d = new Date(ms);
                    }
                    if (d == null) {
                        status = "skip";
                        message = "找不到可用時間";
                    } else {
                        boolean seconds = action.optBoolean("seconds", false);
                        String stem = (seconds ? fileDateSecond : fileDateMinute).format(d);
                        String ext = extensionOf(name, mime);
                        String key = stem + ext.toLowerCase(Locale.US);
                        int n = duplicates.containsKey(key) ? duplicates.get(key) : 0;
                        duplicates.put(key, n + 1);
                        String newName = n == 0 ? stem + ext : stem + "-" + String.format(Locale.US, "%02d", n) + ext;
                        fields.put(previewField("檔名", name, newName));
                        if (name.equals(newName)) {
                            status = "same";
                            message = "檔名已經正確";
                        }
                    }
                } else if ("dateFromFilename".equals(type)) {
                    if (!editableMetadataMime(mime)) {
                        status = "skip";
                        message = "此格式只讀，為保護畫質不寫入";
                    } else {
                        Date d = parseFilenameDate(name);
                        if (d == null) {
                            status = "skip";
                            message = "檔名沒有可辨識時間";
                        } else {
                            String newDate = exifDateFormat.format(d);
                            String oldDate = before.optString("dateTimeOriginal", "—");
                            fields.put(previewField("拍攝時間", oldDate, newDate));
                            String offset = action.optString("offset", "");
                            if (!offset.isEmpty()) {
                                String oldOffset = before.optString("offsetTimeOriginal", "—");
                                fields.put(previewField("時區", oldOffset, offset));
                            }
                            if (oldDate.equals(newDate) && (offset.isEmpty() || offset.equals(before.optString("offsetTimeOriginal", "")))) {
                                status = "same";
                                message = "拍攝時間已經正確";
                            }
                        }
                    }
                } else if ("shiftTime".equals(type)) {
                    if (!editableMetadataMime(mime)) {
                        status = "skip";
                        message = "此格式只讀，為保護畫質不寫入";
                    } else {
                        String oldDate = before.optString("dateTimeOriginal", "");
                        long oldMs = exifDateToMillis(oldDate);
                        if (oldMs <= 0) {
                            status = "skip";
                            message = "沒有 EXIF 拍攝時間";
                        } else {
                            long newMs = oldMs + action.optLong("minutes", 0) * 60000L;
                            String newDate = exifDateFormat.format(new Date(newMs));
                            fields.put(previewField("拍攝時間", oldDate, newDate));
                            String offset = action.optString("offset", "");
                            if (!offset.isEmpty()) fields.put(previewField("時區", before.optString("offsetTimeOriginal", "—"), offset));
                            if (oldMs == newMs && (offset.isEmpty() || offset.equals(before.optString("offsetTimeOriginal", "")))) {
                                status = "same";
                                message = "沒有變更";
                            }
                        }
                    }
                } else if ("gpsSet".equals(type)) {
                    if (!editableMetadataMime(mime)) {
                        status = "skip";
                        message = "此格式只讀，為保護畫質不寫入";
                    } else {
                        double lat = action.optDouble("lat");
                        double lon = action.optDouble("lon");
                        String oldGps = before.optBoolean("hasGps")
                                ? String.format(Locale.US, "%.6f, %.6f", before.optDouble("lat"), before.optDouble("lon"))
                                : "無定位";
                        String newGps = String.format(Locale.US, "%.6f, %.6f", lat, lon);
                        fields.put(previewField("GPS", oldGps, newGps));
                        if (oldGps.equals(newGps)) {
                            status = "same";
                            message = "GPS 已經相同";
                        }
                    }
                } else if ("gpsCopy".equals(type)) {
                    if (!editableMetadataMime(mime)) {
                        status = "skip";
                        message = "此格式只讀，為保護畫質不寫入";
                    } else if (copyLat == null || copyLon == null) {
                        status = "skip";
                        message = "第一張照片沒有 GPS";
                    } else if (i == 0) {
                        status = "same";
                        message = "GPS 來源照片，不修改";
                    } else {
                        String oldGps = before.optBoolean("hasGps")
                                ? String.format(Locale.US, "%.6f, %.6f", before.optDouble("lat"), before.optDouble("lon"))
                                : "無定位";
                        String newGps = String.format(Locale.US, "%.6f, %.6f", copyLat, copyLon);
                        fields.put(previewField("GPS", oldGps, newGps));
                        if (oldGps.equals(newGps)) {
                            status = "same";
                            message = "GPS 已經相同";
                        }
                    }
                } else if ("gpsClear".equals(type)) {
                    if (!editableMetadataMime(mime)) {
                        status = "skip";
                        message = "此格式只讀，為保護畫質不寫入";
                    } else if (!before.optBoolean("hasGps")) {
                        status = "same";
                        message = "原本就沒有 GPS";
                    } else {
                        String oldGps = String.format(Locale.US, "%.6f, %.6f", before.optDouble("lat"), before.optDouble("lon"));
                        fields.put(previewField("GPS", oldGps, "清除定位"));
                    }
                } else {
                    status = "skip";
                    message = "尚未支援此預覽操作";
                }

                p.put("status", status);
                p.put("message", message);
                p.put("fields", fields);
                items.put(p);
                if ("change".equals(status)) changed++;
                else if ("same".equals(status)) unchanged++;
                else skipped++;
            }

            result.put("ok", true);
            result.put("type", type);
            result.put("changed", changed);
            result.put("unchanged", unchanged);
            result.put("skipped", skipped);
            result.put("items", items);
        } catch (Exception e) {
            return errorResult("預覽失敗：" + e.getMessage());
        }
        return result;
    }

    private void logHistory(JSONObject entry) {
        try {
            String old = getPreferences(MODE_PRIVATE).getString("history", "[]");
            JSONArray a = new JSONArray(old);
            entry.put("time", System.currentTimeMillis());
            a.put(entry);
            JSONArray trimmed = new JSONArray();
            int start = Math.max(0, a.length() - 50);
            for (int i = start; i < a.length(); i++) trimmed.put(a.get(i));
            getPreferences(MODE_PRIVATE).edit().putString("history", trimmed.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void requestWrite(JSONArray ids, JSONObject action) {
        if (ids == null || ids.length() == 0) {
            sendActionResult(errorResult("尚未選擇照片"));
            return;
        }
        pendingIds = ids;
        pendingAction = action;

        if (Build.VERSION.SDK_INT >= 30) {
            ArrayList<Uri> uris = new ArrayList<>();
            int max = Math.min(ids.length(), 500);
            for (int i = 0; i < max; i++) {
                long id = ids.optLong(i, -1);
                if (id >= 0) uris.add(uriForId(id));
            }
            try {
                PendingIntent pi = MediaStore.createWriteRequest(getContentResolver(), uris);
                startIntentSenderForResult(pi.getIntentSender(), REQ_WRITE, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException | RuntimeException e) {
                sendActionResult(errorResult("無法取得系統寫入授權：" + e.getMessage()));
            }
        } else {
            executePendingAction();
        }
    }

    private void executePendingAction() {
        final JSONArray ids = pendingIds;
        final JSONObject action = pendingAction;
        pendingIds = null;
        pendingAction = null;
        if (ids == null || action == null) {
            sendActionResult(errorResult("沒有待執行的操作"));
            return;
        }

        new Thread(() -> {
            JSONObject result = new JSONObject();
            JSONArray details = new JSONArray();
            int success = 0, skipped = 0, failed = 0;
            String type = action.optString("type", "");

            try {
                if ("rename".equals(type)) {
                    Map<String, Integer> duplicates = new HashMap<>();
                    for (int i = 0; i < ids.length(); i++) {
                        long id = ids.optLong(i, -1);
                        if (id < 0) continue;
                        JSONObject before = exifInfo(id);
                        try {
                            String source = action.optString("source", "filename");
                            Date d = null;
                            if ("filename".equals(source)) d = parseFilenameDate(before.optString("name"));
                            if ("exif".equals(source)) d = new Date(exifDateToMillis(before.optString("dateTimeOriginal")));
                            if (d == null || d.getTime() <= 0) {
                                skipped++;
                                details.put(detail(id, before.optString("name"), "", "略過：找不到可用時間"));
                                continue;
                            }
                            boolean seconds = action.optBoolean("seconds", false);
                            String stem = (seconds ? fileDateSecond : fileDateMinute).format(d);
                            String ext = extensionOf(before.optString("name"), before.optString("mime"));
                            String key = stem + ext.toLowerCase(Locale.US);
                            int n = duplicates.containsKey(key) ? duplicates.get(key) : 0;
                            duplicates.put(key, n + 1);
                            String newName = n == 0 ? stem + ext : stem + "-" + String.format(Locale.US, "%02d", n) + ext;

                            ContentValues cv = new ContentValues();
                            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, newName);
                            int changed = getContentResolver().update(uriForId(id), cv, null, null);
                            JSONObject after = mediaRow(id);
                            String actual = after.optString("name", "");
                            if (changed > 0 && !actual.isEmpty()) {
                                success++;
                                details.put(detail(id, before.optString("name"), actual, "完成"));
                            } else {
                                failed++;
                                details.put(detail(id, before.optString("name"), newName, "失敗"));
                            }
                        } catch (Exception e) {
                            failed++;
                            details.put(detail(id, before.optString("name"), "", "失敗：" + e.getMessage()));
                        }
                    }
                } else if ("dateFromFilename".equals(type) || "shiftTime".equals(type)
                        || "gpsSet".equals(type) || "gpsClear".equals(type) || "gpsCopy".equals(type)) {

                    Double copyLat = null, copyLon = null;
                    if ("gpsCopy".equals(type) && ids.length() > 0) {
                        JSONObject src = exifInfo(ids.optLong(0));
                        if (src.optBoolean("hasGps")) {
                            copyLat = src.optDouble("lat");
                            copyLon = src.optDouble("lon");
                        }
                    }

                    for (int i = 0; i < ids.length(); i++) {
                        long id = ids.optLong(i, -1);
                        if (id < 0) continue;
                        JSONObject before = exifInfo(id);
                        String mime = before.optString("mime", "");
                        String name = before.optString("name", "");
                        if (!editableMetadataMime(mime)) {
                            skipped++;
                            details.put(detail(id, name, "", "略過：此格式只讀，為保護畫質不寫入"));
                            continue;
                        }

                        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uriForId(id), "rw")) {
                            if (pfd == null) throw new IOException("無法開啟照片");
                            ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());

                            if ("dateFromFilename".equals(type)) {
                                Date d = parseFilenameDate(name);
                                if (d == null) {
                                    skipped++;
                                    details.put(detail(id, name, "", "略過：檔名沒有可辨識時間"));
                                    continue;
                                }
                                String value = exifDateFormat.format(d);
                                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, value);
                                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, value);
                                String offset = action.optString("offset", "");
                                if (!offset.isEmpty()) exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset);
                            }

                            if ("shiftTime".equals(type)) {
                                String old = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
                                long oldMs = exifDateToMillis(old);
                                if (oldMs <= 0) {
                                    skipped++;
                                    details.put(detail(id, name, "", "略過：沒有 EXIF 拍攝時間"));
                                    continue;
                                }
                                long newMs = oldMs + action.optLong("minutes", 0) * 60000L;
                                String value = exifDateFormat.format(new Date(newMs));
                                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, value);
                                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, value);
                                String offset = action.optString("offset", "");
                                if (!offset.isEmpty()) exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset);
                            }

                            if ("gpsSet".equals(type)) {
                                double lat = action.getDouble("lat");
                                double lon = action.getDouble("lon");
                                exif.setLatLong(lat, lon);
                            }

                            if ("gpsCopy".equals(type)) {
                                if (copyLat == null || copyLon == null) {
                                    skipped++;
                                    details.put(detail(id, name, "", "略過：第一張照片沒有 GPS"));
                                    continue;
                                }
                                if (i == 0) {
                                    skipped++;
                                    details.put(detail(id, name, "", "來源照片：GPS 保持不變"));
                                    continue;
                                }
                                exif.setLatLong(copyLat, copyLon);
                            }

                            if ("gpsClear".equals(type)) {
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null);
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null);
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null);
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null);
                                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null);
                                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null);
                            }

                            exif.saveAttributes();
                        }

                        JSONObject after = exifInfo(id);
                        boolean verified = verifyAction(type, action, before, after);
                        if (verified) {
                            success++;
                            details.put(detail(id, name, name, "完成並重新讀取驗證"));
                        } else {
                            failed++;
                            details.put(detail(id, name, name, "已寫入，但驗證未通過"));
                        }
                    }
                } else {
                    result = errorResult("尚未支援的操作：" + type);
                    sendActionResult(result);
                    return;
                }

                result.put("ok", failed == 0);
                result.put("type", type);
                result.put("success", success);
                result.put("skipped", skipped);
                result.put("failed", failed);
                result.put("details", details);
                result.put("message", "完成 " + success + " 張；略過 " + skipped + " 張；失敗 " + failed + " 張");

                JSONObject hist = new JSONObject();
                hist.put("type", type);
                hist.put("success", success);
                hist.put("skipped", skipped);
                hist.put("failed", failed);
                hist.put("summary", result.optString("message"));
                logHistory(hist);
            } catch (Exception e) {
                result = errorResult("操作失敗：" + e.getMessage());
            }
            sendActionResult(result);
        }).start();
    }

    private JSONObject detail(long id, String before, String after, String status) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("before", safe(before));
            o.put("after", safe(after));
            o.put("status", safe(status));
        } catch (JSONException ignored) {}
        return o;
    }

    private boolean verifyAction(String type, JSONObject action, JSONObject before, JSONObject after) {
        if ("dateFromFilename".equals(type)) {
            Date d = parseFilenameDate(before.optString("name"));
            if (d == null) return false;
            long actual = exifDateToMillis(after.optString("dateTimeOriginal"));
            return Math.abs(actual - d.getTime()) < 1000L;
        }
        if ("shiftTime".equals(type)) {
            long b = exifDateToMillis(before.optString("dateTimeOriginal"));
            long a = exifDateToMillis(after.optString("dateTimeOriginal"));
            return b > 0 && a == b + action.optLong("minutes", 0) * 60000L;
        }
        if ("gpsSet".equals(type)) {
            if (!after.optBoolean("hasGps")) return false;
            return Math.abs(after.optDouble("lat") - action.optDouble("lat")) < 0.00001
                    && Math.abs(after.optDouble("lon") - action.optDouble("lon")) < 0.00001;
        }
        if ("gpsCopy".equals(type)) return after.optBoolean("hasGps");
        if ("gpsClear".equals(type)) return !after.optBoolean("hasGps");
        return true;
    }

    public class Bridge {
        @JavascriptInterface
        public void requestPermissions() {
            runOnUiThread(MainActivity.this::requestPermissionsNow);
        }

        @JavascriptInterface
        public boolean hasPermission() {
            return hasReadPermission();
        }

        @JavascriptInterface
        public String scanPhotos(long startMs, long endMs, String keyword, int limit) {
            JSONObject result = new JSONObject();
            JSONArray items = new JSONArray();
            if (!hasReadPermission()) {
                try {
                    result.put("ok", false);
                    result.put("message", "尚未取得照片讀取權限");
                    result.put("items", items);
                } catch (JSONException ignored) {}
                return result.toString();
            }

            limit = Math.max(50, Math.min(limit <= 0 ? 1500 : limit, 5000));
            String[] projection = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT
            };

            List<String> clauses = new ArrayList<>();
            List<String> args = new ArrayList<>();
            if (startMs > 0) {
                clauses.add(MediaStore.Images.Media.DATE_TAKEN + " >= ?");
                args.add(String.valueOf(startMs));
            }
            if (endMs > 0) {
                clauses.add(MediaStore.Images.Media.DATE_TAKEN + " <= ?");
                args.add(String.valueOf(endMs));
            }
            if (keyword != null && !keyword.trim().isEmpty()) {
                clauses.add(MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?");
                args.add("%" + keyword.trim() + "%");
            }
            String selection = clauses.isEmpty() ? null : String.join(" AND ", clauses);
            String[] selArgs = args.isEmpty() ? null : args.toArray(new String[0]);
            int total = 0;

            try (Cursor c = getContentResolver().query(imagesUri(), projection, selection, selArgs,
                    MediaStore.Images.Media.DATE_TAKEN + " DESC")) {
                if (c != null) {
                    total = c.getCount();
                    int count = 0;
                    while (c.moveToNext() && count < limit) {
                        JSONObject o = new JSONObject();
                        long id = c.getLong(0);
                        o.put("id", id);
                        o.put("name", safe(c.getString(1)));
                        o.put("dateTaken", c.isNull(2) ? 0 : c.getLong(2));
                        o.put("dateAdded", c.isNull(3) ? 0 : c.getLong(3) * 1000L);
                        o.put("dateModified", c.isNull(4) ? 0 : c.getLong(4) * 1000L);
                        o.put("mime", safe(c.getString(5)));
                        o.put("size", c.isNull(6) ? 0 : c.getLong(6));
                        o.put("width", c.isNull(7) ? 0 : c.getInt(7));
                        o.put("height", c.isNull(8) ? 0 : c.getInt(8));
                        o.put("uri", uriForId(id).toString());

                        Date parsed = parseFilenameDate(o.optString("name"));
                        if (parsed != null) {
                            o.put("filenameTime", parsed.getTime());
                            long dt = o.optLong("dateTaken");
                            if (dt > 0) o.put("timeDiffMinutes", Math.round((dt - parsed.getTime()) / 60000.0));
                        }
                        items.put(o);
                        count++;
                    }
                }
                result.put("ok", true);
                result.put("total", total);
                result.put("shown", items.length());
                result.put("truncated", total > items.length());
                result.put("items", items);
            } catch (Exception e) {
                try {
                    result.put("ok", false);
                    result.put("message", e.getMessage());
                    result.put("items", items);
                } catch (JSONException ignored) {}
            }
            return result.toString();
        }

        @JavascriptInterface
        public String getPhotoInfo(long id) {
            return exifInfo(id).toString();
        }

        @JavascriptInterface
        public String healthCheck(String idsJson) {
            JSONObject r = new JSONObject();
            JSONArray problems = new JSONArray();
            int missingTime = 0, mismatch = 0, noGps = 0, readOnly = 0, checked = 0;
            try {
                JSONArray ids = new JSONArray(idsJson);
                int max = Math.min(ids.length(), 600);
                for (int i = 0; i < max; i++) {
                    long id = ids.optLong(i, -1);
                    if (id < 0) continue;
                    checked++;
                    JSONObject info = exifInfo(id);
                    String name = info.optString("name");
                    String exifDate = info.optString("dateTimeOriginal");
                    Date fnDate = parseFilenameDate(name);
                    long exifMs = exifDateToMillis(exifDate);
                    if (exifMs <= 0 && info.optLong("dateTaken") <= 0) {
                        missingTime++;
                        problems.put(detail(id, name, "", "缺少拍攝時間"));
                    }
                    if (fnDate != null && exifMs > 0 && Math.abs(fnDate.getTime() - exifMs) >= 60000L) {
                        mismatch++;
                        problems.put(detail(id, name, "", "檔名時間與 EXIF 不一致"));
                    }
                    if (!info.optBoolean("hasGps")) noGps++;
                    if (!info.optBoolean("metadataWritable")) readOnly++;
                }
                r.put("ok", true);
                r.put("checked", checked);
                r.put("missingTime", missingTime);
                r.put("mismatch", mismatch);
                r.put("noGps", noGps);
                r.put("readOnly", readOnly);
                r.put("problems", problems);
            } catch (Exception e) {
                try {
                    r.put("ok", false);
                    r.put("message", e.getMessage());
                } catch (JSONException ignored) {}
            }
            return r.toString();
        }

        @JavascriptInterface
        public String previewAction(String idsJson, String actionJson) {
            try {
                return buildPreview(new JSONArray(idsJson), new JSONObject(actionJson)).toString();
            } catch (Exception e) {
                return errorResult("預覽資料錯誤：" + e.getMessage()).toString();
            }
        }

        @JavascriptInterface
        public String getHistory() {
            return getPreferences(MODE_PRIVATE).getString("history", "[]");
        }

        @JavascriptInterface
        public void requestAction(String idsJson, String actionJson) {
            runOnUiThread(() -> {
                try {
                    requestWrite(new JSONArray(idsJson), new JSONObject(actionJson));
                } catch (Exception e) {
                    sendActionResult(errorResult("操作資料錯誤：" + e.getMessage()));
                }
            });
        }
    }
}
