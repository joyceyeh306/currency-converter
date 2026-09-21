package tw.ajo.travelnotebook;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
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

public class MainActivity extends Activity {
    private static final int REQ_FILE_CHOOSER = 5001;
    private static final int REQ_SAVE_FILE = 5002;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingSaveBytes;
    private String pendingSaveMime = "application/octet-stream";
    private String pendingSaveName = "file.bin";

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
                Uri uri = AttachmentProvider.uriForFile(MainActivity.this, f);
                runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_VIEW); i.setDataAndType(uri, mime == null ? "application/octet-stream" : mime);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(i); }
                    catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "手機沒有可開啟此檔案的 App", Toast.LENGTH_LONG).show(); }
                });
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "附件開啟失敗", Toast.LENGTH_LONG).show()); }
        }
    }

    @Override public void onBackPressed() {
        webView.evaluateJavascript("(window.androidBack?androidBack():false)", value -> {
            if ("false".equals(value) || "null".equals(value)) MainActivity.super.onBackPressed();
        });
    }
}
