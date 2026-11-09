package tw.joyce.xiaobenben;

import android.content.pm.ShortcutManager;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.InputStream;

public final class BookActivity extends MainActivity {
    private static final String HOME_URI = "memoryjo://book/";
    private static final String BOOK_EXTRA = "tw.joyce.xiaobenbe5.book_id";
    private WebView web;
    private String pendingBookId;
    private boolean pageReady;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        View content = findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        View child = ((ViewGroup) content).getChildAt(0);
        if (!(child instanceof WebView)) return;
        web = (WebView) child;
        pendingBookId = readShortcutBookId(getIntent());
        web.stopLoading();
        web.addJavascriptInterface(new BookBridge(), "MemoryJoShortcut");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!url.startsWith("file:///android_asset/index.html")) return;
                pageReady = true;
                openPendingBook();
            }
        });
        web.loadUrl("file:///android_asset/index.html?android=1");
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingBookId = readShortcutBookId(intent);
        openPendingBook();
    }

    private String readShortcutBookId(Intent intent) {
        if (intent == null) return null;
        String id = intent.getStringExtra(BOOK_EXTRA);
        if (id == null && intent.getData() != null && intent.getData().toString().startsWith(HOME_URI)) {
            id = intent.getData().getLastPathSegment();
        }
        return id == null || id.isEmpty() ? null : id;
    }

    private void openPendingBook() {
        if (web == null || !pageReady || pendingBookId == null) return;
        String id = pendingBookId;
        pendingBookId = null;
        web.evaluateJavascript("window.__memoryJoOpenPinnedBook&&window.__memoryJoOpenPinnedBook(" + JSONObject.quote(id) + ");", null);
    }

    private void respond(boolean ok, String message) {
        if (web == null) return;
        web.evaluateJavascript("window.__memoryJoPinResult&&window.__memoryJoPinResult(" + ok + "," + JSONObject.quote(message) + ");", null);
    }

    private final class BookBridge {
        @JavascriptInterface public void requestPinBook(String id, String title, String iconAssetPath) {
            runOnUiThread(() -> pinBookOnUiThread(id, title, iconAssetPath));
        }
    }

    private void pinBookOnUiThread(String id, String title, String assetPath) {
        if (id == null || id.isEmpty() || id.length() > 180 || title == null || title.trim().isEmpty()) {
            respond(false, "找不到這本手帳的名稱，無法新增捷徑。");
            return;
        }
        if (Build.VERSION.SDK_INT < 26) {
            respond(false, "目前 Android 系統版本不支援將這本手帳固定到桌面。");
            return;
        }
        try {
            ShortcutManager manager = getSystemService(ShortcutManager.class);
            if (manager == null || !manager.isRequestPinShortcutSupported()) {
                respond(false, "目前桌面啟動器不支援自動新增捷徑，請確認 OPPO 桌面權限設定。");
                return;
            }
            String label = title.trim();
            if (label.length() > 24) label = label.substring(0, 24);
            Intent open = new Intent(this, BookActivity.class);
            open.setAction(Intent.ACTION_VIEW);
            open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            open.setData(Uri.parse(HOME_URI + Uri.encode(id)));
            open.putExtra(BOOK_EXTRA, id);
            ShortcutInfo.Builder builder = new ShortcutInfo.Builder(this, "memoryjo-book-" + id)
                .setShortLabel(label)
                .setLongLabel(title.trim())
                .setIntent(open);
            Bitmap bmp = loadCoverIcon(assetPath);
            if (bmp != null) builder.setIcon(Icon.createWithBitmap(bmp));
            else builder.setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_agenda));
            boolean accepted = manager.requestPinShortcut(builder.build(), null);
            respond(accepted, accepted ? "已向手機桌面提出新增捷徑的要求，請依系統畫面確認。" : "手機桌面拒絕建立捷徑，請檢查桌面權限設定。");
        } catch (Exception ex) {
            respond(false, "無法建立桌面捷徑，請確認桌面權限後再試。");
        }
    }

    private Bitmap loadCoverIcon(String assetPath) {
        if (assetPath == null || assetPath.isEmpty()) return null;
        String path = assetPath.replaceFirst("^\\./", "");
        if (path.startsWith("assets/")) path = path.substring("assets/".length());
        if (path.contains("..") || path.startsWith("/") ||
            !(path.startsWith("hq_fixed/") || path.startsWith("cover_added/") || path.startsWith("icons/"))) return null;
        try (InputStream in = getAssets().open(path)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            Bitmap src = BitmapFactory.decodeStream(in, null, opts);
            if (src == null) return null;
            int side = Math.min(src.getWidth(), src.getHeight());
            Bitmap square = Bitmap.createBitmap(src, (src.getWidth()-side)/2, (src.getHeight()-side)/2, side, side);
            Bitmap result = Bitmap.createScaledBitmap(square, 144, 144, true);
            return result;
        } catch (Exception ignored) { return null; }
    }
}
