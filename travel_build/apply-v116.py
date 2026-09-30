from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')

# Web UI: remove camera icon so the two receipt source buttons align.
p=root/'app/src/main/assets/index.html'
s=p.read_text()
old="""<button class="primary" onclick="startReceiptScan('camera')">${iconSvg('camera')}<br>拍照收據</button>"""
new="""<button class="primary" onclick="startReceiptScan('camera')">拍照收據</button>"""
if old not in s:
    raise SystemExit('v1.0.16 patch failed: receipt camera button')
s=s.replace(old,new,1)
p.write_text(s)

# Native gallery: prefer OPPO Photos instead of the generic Android document picker.
p=root/'app/src/main/java/tw/ajo/travelnotebook/MainActivity.java'
m=p.read_text()
old_java='''    private void launchReceiptGallery() {
        discardPendingReceipt();
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i,"選擇收據照片"),REQ_RECEIPT_GALLERY);
    }
'''
new_java='''    private void launchReceiptGallery() {
        discardPendingReceipt();

        // OPPO / ColorOS Photos.  On OPPO devices this opens the familiar system album
        // directly instead of Android's generic document/file browser.
        Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pick.setType("image/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent oppo = new Intent(pick);
        oppo.setPackage("com.coloros.gallery3d");
        if (oppo.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(oppo, REQ_RECEIPT_GALLERY);
            return;
        }

        // Fallback for non-OPPO devices: use the device's normal image picker first.
        if (pick.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(pick, REQ_RECEIPT_GALLERY);
            return;
        }

        // Last fallback only.
        Intent doc = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        doc.addCategory(Intent.CATEGORY_OPENABLE);
        doc.setType("image/*");
        doc.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(doc, REQ_RECEIPT_GALLERY);
    }
'''
if old_java not in m:
    raise SystemExit('v1.0.16 patch failed: receipt gallery launcher')
m=m.replace(old_java,new_java,1)
p.write_text(m)

# Version.
p=root/'app/build.gradle'
g=p.read_text()
g=re.sub(r"versionCode\s+\d+","versionCode 116",g,count=1)
g=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.16'",g,count=1)
p.write_text(g)
