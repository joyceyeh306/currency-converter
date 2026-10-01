from pathlib import Path

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/java/tw/ajo/travelnotebook/MainActivity.java'
m=p.read_text()

# imports
m=m.replace('import android.graphics.Color;\n','import android.graphics.Color;\nimport android.graphics.Bitmap;\nimport android.graphics.BitmapFactory;\n',1)
m=m.replace('import java.io.File;\n','import java.io.File;\nimport java.io.FileInputStream;\nimport java.io.InputStream;\nimport java.io.ByteArrayOutputStream;\n',1)
m=m.replace('import java.util.Set;\n','import java.util.Set;\nimport java.util.UUID;\n',1)

# Receipt file helpers + preview data for the split workspace.
marker='    private byte[] decodeDataUrl(String dataUrl) {\n'
helpers=r'''    private File receiptFileFromToken(String token) {
        if (token == null || !token.startsWith("ajo-receipt://")) return null;
        try {
            String name = Uri.decode(token.substring("ajo-receipt://".length()));
            if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) return null;
            return new File(new File(getFilesDir(), "receipts"), name);
        } catch (Exception e) { return null; }
    }

    private InputStream openReceiptInput(String ref) throws Exception {
        File f = receiptFileFromToken(ref);
        if (f != null) return new FileInputStream(f);
        return getContentResolver().openInputStream(Uri.parse(ref));
    }

    private String persistReceiptPhoto(Uri uri) throws Exception {
        if (uri == null) return "";
        File dir = new File(getFilesDir(), "receipts");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("receipt dir");
        String mime = null;
        try { mime = getContentResolver().getType(uri); } catch (Exception ignored) {}
        String ext = mime != null && mime.contains("png") ? ".png" : (mime != null && mime.contains("webp") ? ".webp" : ".jpg");
        String name = "receipt_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0,8) + ext;
        File out = new File(dir, name);
        try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IllegalStateException("receipt input");
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return "ajo-receipt://" + Uri.encode(name);
    }

    private String buildReceiptPreviewData(String ref) {
        if (ref == null || ref.isEmpty()) return "";
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = openReceiptInput(ref)) { if (in == null) return ""; BitmapFactory.decodeStream(in, null, bounds); }
            int sample = 1;
            while ((bounds.outWidth > 0 && bounds.outWidth / sample > 900) || (bounds.outHeight > 0 && bounds.outHeight / sample > 5000)) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inSampleSize = Math.max(1, sample);
            Bitmap bmp;
            try (InputStream in = openReceiptInput(ref)) { if (in == null) return ""; bmp = BitmapFactory.decodeStream(in, null, opts); }
            if (bmp == null) return "";
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, bos); bmp.recycle();
            return "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }

'''
if helpers not in m:
    if marker not in m: raise SystemExit('v1.0.19b failed: helper marker')
    m=m.replace(marker,helpers+marker,1)

# Keep = really copy the receipt into app-private storage.
start=m.index('        @JavascriptInterface public String keepReceiptPhoto() {')
end=m.index('        @JavascriptInterface public void discardReceiptPhoto()',start)
new_keep=r'''        @JavascriptInterface public String keepReceiptPhoto() {
            if (pendingReceiptUri == null) return "";
            Uri source = pendingReceiptUri; boolean tempCamera = pendingReceiptFromCamera;
            try {
                String token = persistReceiptPhoto(source);
                if (tempCamera) { try { getContentResolver().delete(source,null,null); } catch(Exception ignored) {} }
                pendingReceiptUri = null; pendingReceiptFromCamera = false;
                return token;
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface public String getReceiptPreviewData(String uriText) { return buildReceiptPreviewData(uriText); }

        @JavascriptInterface public void deleteReceiptPhoto(String uriText) {
            File f = receiptFileFromToken(uriText);
            if (f != null && f.exists()) { try { f.delete(); } catch(Exception ignored) {} }
        }

'''
m=m[:start]+new_keep+m[end:]

# Saved receipt: internal copies open in the existing image viewer; old content URIs still have fallback support.
start=m.index('        @JavascriptInterface public void openReceiptPhoto(String uriText) {')
end=m.index('        @JavascriptInterface public void saveBase64File',start)
new_open=r'''        @JavascriptInterface public void openReceiptPhoto(String uriText) {
            runOnUiThread(() -> {
                try {
                    File f = receiptFileFromToken(uriText);
                    if (f != null && f.exists()) {
                        Intent i = new Intent(MainActivity.this, AttachmentViewerActivity.class);
                        i.putExtra("path", f.getAbsolutePath()); i.putExtra("mime", "image/*"); i.putExtra("name", f.getName()); startActivity(i); return;
                    }
                    Uri uri=Uri.parse(uriText); Intent i=new Intent(Intent.ACTION_VIEW); i.setDataAndType(uri,"image/*"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(i);
                } catch (Exception e) { Toast.makeText(MainActivity.this,"收據照片已不存在或無法開啟",Toast.LENGTH_SHORT).show(); }
            });
        }

'''
m=m[:start]+new_open+m[end:]

# If OCR fails, still pass the selected image URI back so manual side-by-side entry can continue.
old='''    private void sendReceiptError(String message) {
        runOnUiThread(() -> { if (pageReady) webView.evaluateJavascript("receiptOcrError("+JSONObject.quote(message)+")",null); else Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show(); });
    }
'''
new='''    private void sendReceiptError(String message) {
        runOnUiThread(() -> {
            if (pageReady) {
                String uri = pendingReceiptUri == null ? "" : pendingReceiptUri.toString();
                webView.evaluateJavascript("receiptOcrError("+JSONObject.quote(message)+","+JSONObject.quote(uri)+")",null);
            } else Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();
        });
    }
'''
if old not in m: raise SystemExit('v1.0.19b failed: sendReceiptError')
m=m.replace(old,new,1)

p.write_text(m)
