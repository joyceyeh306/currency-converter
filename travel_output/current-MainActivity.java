package tw.ajo.travelnotebook;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.util.Base64;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQ_FILE_CHOOSER = 5001;
    private static final int REQ_SAVE_FILE = 5002;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingSaveBytes;
    private String pendingSaveMime = "application/octet-stream";
    private String pendingSaveName = "file.bin";
    private boolean pageReady = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.rgb(255,214,0));
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
                if (scheme.equals("http") || scheme.equals("https") || scheme.equals("geo") || scheme.equals("market")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
                    catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "無法開啟連結", Toast.LENGTH_SHORT).show(); }
                    return true;
                }
                return false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                handleReminderIntent(getIntent());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf","image/*","application/json","application/octet-stream","text/plain"});
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(i, "選擇檔案"), REQ_FILE_CHOOSER);
                return true;
            }
        });

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 8001);
        }
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER) {
            if (fileCallback == null) return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int x=0;x<n;x++) result[x]=data.getClipData().getItemAt(x).getUri();
                } else if (data.getData() != null) result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result); fileCallback = null;
            return;
        }
        if (requestCode == REQ_SAVE_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveBytes != null) {
                try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                    if (os != null) { os.write(pendingSaveBytes); os.flush(); Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show(); }
                } catch (Exception e) { Toast.makeText(this, "儲存失敗", Toast.LENGTH_LONG).show(); }
            }
            pendingSaveBytes = null;
        }
    }

    private byte[] decodeDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        String body = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
        return Base64.decode(body, Base64.DEFAULT);
    }

    private String safeName(String name) {
        String n = name == null ? "file" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return n.isEmpty() ? "file" : n;
    }

    public class AndroidBridge {
        @JavascriptInterface public void saveBase64File(String name, String mime, String dataUrl) {
            try {
                byte[] bytes = decodeDataUrl(dataUrl);
                runOnUiThread(() -> {
                    pendingSaveBytes = bytes; pendingSaveName = safeName(name); pendingSaveMime = mime == null ? "application/octet-stream" : mime;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(pendingSaveMime); i.putExtra(Intent.EXTRA_TITLE, pendingSaveName);
                    startActivityForResult(i, REQ_SAVE_FILE);
                });
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "無法匯出檔案", Toast.LENGTH_LONG).show()); }
        }

        @JavascriptInterface public void openBase64File(String name, String mime, String dataUrl) {
            try {
                byte[] bytes = decodeDataUrl(dataUrl);
                File dir = new File(getCacheDir(), "shared"); if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, safeName(name));
                try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
                runOnUiThread(() -> {
                    Intent i = new Intent(MainActivity.this, AttachmentViewerActivity.class);
                    i.putExtra("path", f.getAbsolutePath());
                    i.putExtra("mime", mime == null ? "application/octet-stream" : mime);
                    i.putExtra("name", safeName(name));
                    startActivity(i);
                });
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "附件開啟失敗", Toast.LENGTH_LONG).show()); }
        }

        @JavascriptInterface public void scheduleReminder(String id, long whenMs, String title, String tripId, String dayId, String targetType, String targetId) {
            runOnUiThread(() -> {
                Intent bi = new Intent(MainActivity.this, ReminderReceiver.class);
                bi.setAction("tw.ajo.travelnotebook.REMINDER");
                bi.setData(Uri.parse("ajo://reminder/" + Uri.encode(id)));
                bi.putExtra("id", id); bi.putExtra("title", title); bi.putExtra("trip", tripId); bi.putExtra("day", dayId); bi.putExtra("targetType", targetType); bi.putExtra("targetId", targetId);
                PendingIntent pi = PendingIntent.getBroadcast(MainActivity.this, id.hashCode(), bi, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager am=(AlarmManager)getSystemService(Context.ALARM_SERVICE);
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi);
                else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi);
                else am.setExact(AlarmManager.RTC_WAKEUP, whenMs, pi);
                SharedPreferences sp=getSharedPreferences("reminders",MODE_PRIVATE); Set<String> ids=new HashSet<>(sp.getStringSet("ids",new HashSet<>())); ids.add(id); sp.edit().putStringSet("ids",ids).apply();
            });
        }

        @JavascriptInterface public void cancelAllReminders() {
            runOnUiThread(() -> {
                SharedPreferences sp=getSharedPreferences("reminders",MODE_PRIVATE); Set<String> ids=new HashSet<>(sp.getStringSet("ids",new HashSet<>())); AlarmManager am=(AlarmManager)getSystemService(Context.ALARM_SERVICE);
                for(String id:ids){Intent bi=new Intent(MainActivity.this,ReminderReceiver.class);bi.setAction("tw.ajo.travelnotebook.REMINDER");bi.setData(Uri.parse("ajo://reminder/"+Uri.encode(id)));PendingIntent pi=PendingIntent.getBroadcast(MainActivity.this,id.hashCode(),bi,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(pi!=null){am.cancel(pi);pi.cancel();}}
                sp.edit().remove("ids").apply();
            });
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleReminderIntent(intent);
    }

    private void handleReminderIntent(Intent intent) {
        if (!pageReady || intent == null || !intent.hasExtra("reminder_trip")) return;
        String trip=intent.getStringExtra("reminder_trip"), day=intent.getStringExtra("reminder_day"), type=intent.getStringExtra("reminder_type"), target=intent.getStringExtra("reminder_target");
        String js="openReminderTarget("+JSONObject.quote(trip==null?"":trip)+","+JSONObject.quote(day==null?"":day)+","+JSONObject.quote(type==null?"trip":type)+","+JSONObject.quote(target==null?"":target)+")";
        webView.evaluateJavascript(js,null);
        intent.removeExtra("reminder_trip");
    }

    @Override public void onBackPressed() {
        webView.evaluateJavascript("(window.androidBack?androidBack():false)", value -> {
            if ("false".equals(value) || "null".equals(value)) MainActivity.super.onBackPressed();
        });
    }
}
