package tw.ajo.photocollage;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQ_FILE_CHOOSER = 5001;
    private static final int REQ_SAVE_FILE = 5002;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingSaveBytes;
    private String pendingSaveMime = "application/octet-stream";
    private String pendingSaveName = "image.png";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(Color.rgb(18, 64, 115));
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }

        try {
            webView = new WebView(this);
            webView.setBackgroundColor(Color.rgb(246, 250, 255));
            setContentView(webView);

            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(true);
            s.setAllowFileAccessFromFileURLs(true);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            s.setTextZoom(100);

            webView.addJavascriptInterface(new AndroidBridge(), "Android");
            webView.setWebViewClient(new WebViewClient());
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                    if (fileCallback != null) fileCallback.onReceiveValue(null);
                    fileCallback = callback;
                    try {
                        Intent i = params.createIntent();
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("image/*");
                        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        startActivityForResult(Intent.createChooser(i, "選擇照片"), REQ_FILE_CHOOSER);
                        return true;
                    } catch (Exception ex) {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("image/*");
                        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        startActivityForResult(Intent.createChooser(i, "選擇照片"), REQ_FILE_CHOOSER);
                        return true;
                    }
                }
            });

            webView.loadUrl("file:///android_asset/index.html");
        } catch (Throwable t) {
            TextView error = new TextView(this);
            error.setPadding(40, 60, 40, 40);
            error.setTextSize(18);
            error.setTextColor(Color.DKGRAY);
            error.setText("照片拼圖無法啟動 WebView。\n請更新 Android System WebView 後再開啟。\n\n" + t.getClass().getSimpleName());
            setContentView(error);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER) {
            if (fileCallback == null) return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int x = 0; x < n; x++) result[x] = data.getClipData().getItemAt(x).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            return;
        }

        if (requestCode == REQ_SAVE_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveBytes != null) {
                try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                    if (os != null) {
                        os.write(pendingSaveBytes);
                        os.flush();
                        Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "儲存失敗", Toast.LENGTH_LONG).show();
                }
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
        String n = name == null ? "照片拼圖.png" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return n.isEmpty() ? "照片拼圖.png" : n;
    }

    private boolean saveToGallery(String name, String mime, byte[] bytes) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, safeName(name));
            values.put(MediaStore.Images.Media.MIME_TYPE, mime);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/照片拼圖");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return false;
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) return false;
                os.write(bytes);
                os.flush();
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
            return true;
        } catch (Exception e) {
            if (uri != null) {
                try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
            }
            return false;
        }
    }

    private void launchSaveAs(String name, String mime, byte[] bytes) {
        pendingSaveBytes = bytes;
        pendingSaveName = safeName(name);
        pendingSaveMime = mime == null ? "application/octet-stream" : mime;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(pendingSaveMime);
        i.putExtra(Intent.EXTRA_TITLE, pendingSaveName);
        startActivityForResult(i, REQ_SAVE_FILE);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void saveBase64File(String name, String mime, String dataUrl) {
            try {
                byte[] bytes = decodeDataUrl(dataUrl);
                runOnUiThread(() -> {
                    String safeMime = mime == null ? "image/png" : mime;
                    if (saveToGallery(name, safeMime, bytes)) {
                        Toast.makeText(MainActivity.this, "已儲存到 Pictures／照片拼圖", Toast.LENGTH_SHORT).show();
                    } else {
                        launchSaveAs(name, safeMime, bytes);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "無法匯出圖片", Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
