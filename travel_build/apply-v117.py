from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

def sub(pattern,repl,label):
    global s
    ns,n=re.subn(pattern,repl,s,count=1,flags=re.S)
    if n!=1: raise SystemExit('v1.0.17 patch failed: '+label)
    s=ns

s=s.replace("let pendingReceiptResult=null;","let pendingReceiptResult=null,receiptUriQueue=[],receiptBatchIndex=0,receiptBatchSaved=0,receiptBatchSkipped=0;",1)

sub(r"function openReceiptScan\(\)\{.*?\}\nfunction startReceiptScan\(source\)\{.*?\}\nfunction normalizeReceiptLines",
"""function openReceiptScan(){if(!(window.Android&&Android.scanReceipt)){appAlert('此裝置目前無法使用收據辨識');return}dlg('拍收據記帳',`<div class=\"subtle\" style=\"margin-bottom:10px\">可直接拍一張，或從相簿一次選多張收據。每張辨識後都先確認，再依序建立消費。</div><div class=\"receipt-source-grid\"><button class=\"primary\" onclick=\"startReceiptScan('camera')\">拍照收據</button><button class=\"secondary\" onclick=\"startReceiptScan('gallery')\">從相簿選</button></div>`)}
function startReceiptScan(source){closeDlg();pendingReceiptResult=null;receiptUriQueue=[];receiptBatchIndex=0;receiptBatchSaved=0;receiptBatchSkipped=0;try{Android.scanReceipt(source);appAlert(source==='gallery'?'請選擇收據照片，可一次多選':'正在開啟相機…')}catch(e){appAlert('無法啟動收據辨識')}}
function receiptGallerySelected(json){let arr=[];try{arr=typeof json==='string'?JSON.parse(json):json}catch(e){}if(!Array.isArray(arr)||!arr.length){appAlert('沒有選擇收據照片');return}receiptUriQueue=arr.filter(Boolean);receiptBatchIndex=0;receiptBatchSaved=0;receiptBatchSkipped=0;scanQueuedReceipt()}
function scanQueuedReceipt(){const uri=receiptUriQueue[receiptBatchIndex];if(!uri){return receiptBatchFinished()}try{Android.scanReceiptUri(uri);appAlert(`正在辨識第 ${receiptBatchIndex+1} / ${receiptUriQueue.length} 張…`)}catch(e){appAlert('無法讀取這張收據')}}
function advanceReceiptQueue(){if(receiptUriQueue.length&&receiptBatchIndex+1<receiptUriQueue.length){receiptBatchIndex++;scanQueuedReceipt();return}receiptBatchFinished()}
function normalizeReceiptLines""",
"open/start queue")

sub(r"function receiptOcrResult\(json\)\{.*?\}\nfunction chooseReceiptAmount",
"""function receiptOcrResult(json){let obj;try{obj=typeof json==='string'?JSON.parse(json):json}catch(e){return receiptOcrError('收據辨識結果無法讀取')}const lines=normalizeReceiptLines(obj.text||''),d=dayById(tripById(route.tripId),route.dayId),t=tripById(route.tripId),cur=d?.currency||t?.currencies?.[0]||'TWD',store=guessReceiptStore(lines),cands=receiptAmountCandidates(lines,cur),category=guessReceiptCategory(lines.join('\\n')),batchTotal=receiptUriQueue.length||1,batchIndex=receiptUriQueue.length?receiptBatchIndex+1:1;pendingReceiptResult={uri:obj.uri||'',source:obj.source||'',lines,cands};const chips=cands.length?`<div class=\"field\"><label>可能的總金額</label><div class=\"receipt-candidates\">${cands.map((x,i)=>`<button type=\"button\" class=\"receipt-candidate ${i===0?'on':''}\" onclick=\"chooseReceiptAmount(this,'${x.amount}')\">${num(x.amount)} ${esc(cur)}</button>`).join('')}</div></div>`:'';const progress=batchTotal>1?`<div class=\"receipt-status\" style=\"padding:2px 0 10px\">第 ${batchIndex} / ${batchTotal} 張</div>`:'';const foot=batchTotal>1?`<button class=\"secondary\" onclick=\"skipReceipt()\">略過</button><button class=\"secondary\" onclick=\"cancelReceiptConfirm()\">取消全部</button><button class=\"primary\" onclick=\"saveReceiptCost()\">儲存</button>`:`<button class=\"secondary\" onclick=\"rescanReceipt()\">重拍</button><button class=\"secondary\" onclick=\"cancelReceiptConfirm()\">取消</button><button class=\"primary\" onclick=\"saveReceiptCost()\">儲存</button>`;dlg('確認收據消費',`${progress}<div class=\"formgrid\"><div class=\"field\"><label>分類</label><select id=\"rCostCategory\">${costOptions.map(o=>`<option ${o===category?'selected':''}>${o}</option>`).join('')}</select></div><div class=\"field\"><label>店名／備註</label><textarea class=\"cost-form-note\" id=\"rCostNote\" placeholder=\"辨識不到時可手動補上\">${esc(store)}</textarea></div>${chips}<div class=\"field\"><label>金額（${esc(cur)}）</label><input id=\"rCostAmount\" type=\"number\" inputmode=\"decimal\" step=\"any\" value=\"${cands[0]?.amount??''}\" placeholder=\"0\"></div><label class=\"receipt-keep\"><input id=\"rKeepReceipt\" type=\"checkbox\"> 保留收據照片並連結到這筆消費</label>${lines.length?`<details><summary class=\"subtle\">查看辨識文字</summary><div class=\"receipt-raw\">${esc(lines.join('\\n'))}</div></details>`:''}</div>`,foot)}
function chooseReceiptAmount""",
"receipt result")

sub(r"function receiptOcrError\(message\)\{.*?\}\nfunction retryReceiptOcr",
"""function receiptOcrError(message){const batchTotal=receiptUriQueue.length||1,batchIndex=receiptUriQueue.length?receiptBatchIndex+1:1;const progress=batchTotal>1?`<div class=\"receipt-status\" style=\"padding:2px 0 10px\">第 ${batchIndex} / ${batchTotal} 張</div>`:'';const foot=batchTotal>1?`<button class=\"secondary\" onclick=\"skipReceipt()\">略過</button><button class=\"secondary\" onclick=\"cancelReceiptConfirm()\">取消全部</button><button class=\"primary\" onclick=\"retryReceiptOcr()\">再試一次</button>`:`<button class=\"secondary\" onclick=\"cancelReceiptConfirm()\">取消</button><button class=\"primary\" onclick=\"retryReceiptOcr()\">再試一次</button>`;dlg('收據辨識暫時無法使用',`${progress}<div class=\"receipt-status\">${esc(message||'辨識失敗')}</div><div class=\"subtle\">第一次使用時，韓文／英文辨識模型可能需要先透過 Google Play 服務下載。保持網路連線後可再試一次。</div>`,foot)}
function retryReceiptOcr""",
"receipt error")

sub(r"function cancelReceiptConfirm\(\)\{.*?\}\nfunction saveReceiptCost",
"""function cancelReceiptConfirm(){try{if(window.Android&&Android.discardReceiptPhoto)Android.discardReceiptPhoto()}catch(e){}pendingReceiptResult=null;receiptUriQueue=[];receiptBatchIndex=0;receiptBatchSaved=0;receiptBatchSkipped=0;closeDlg()}
function saveReceiptCost""",
"receipt cancel")

sub(r"function saveReceiptCost\(\)\{.*?\}\nfunction openSavedReceipt",
"""function saveReceiptCost(){const d=dayById(tripById(route.tripId),route.dayId);if(!d)return;const amount=$('#rCostAmount')?.value;if(amount==='')return appAlert('請確認金額');const id=uid(),keep=!!$('#rKeepReceipt')?.checked;let receiptUri='';try{if(keep&&window.Android&&Android.keepReceiptPhoto)receiptUri=Android.keepReceiptPhoto()||'';else if(window.Android&&Android.discardReceiptPhoto)Android.discardReceiptPhoto()}catch(e){}d.costs=d.costs||[];d.costs.push({id,category:$('#rCostCategory').value,note:$('#rCostNote').value.trim(),amount,receiptUri});saveData();receiptBatchSaved++;pendingReceiptResult=null;closeDlg();renderDay();if(receiptUriQueue.length){appAlert('已儲存，正在辨識下一張…');advanceReceiptQueue()}else appAlert('已由收據建立消費')}
function skipReceipt(){try{if(window.Android&&Android.discardReceiptPhoto)Android.discardReceiptPhoto()}catch(e){}receiptBatchSkipped++;pendingReceiptResult=null;closeDlg();appAlert('已略過，正在辨識下一張…');advanceReceiptQueue()}
function receiptBatchFinished(){const total=receiptUriQueue.length,saved=receiptBatchSaved,skipped=receiptBatchSkipped;pendingReceiptResult=null;receiptUriQueue=[];receiptBatchIndex=0;if(total<=1){receiptBatchSaved=0;receiptBatchSkipped=0;return}dlg('收據記帳完成',`<div class=\"receipt-status\">共 ${total} 張</div><div class=\"subtle\" style=\"text-align:center\">建立 ${saved} 筆・略過 ${skipped} 張</div>`,`<button class=\"primary\" onclick=\"receiptBatchSaved=0;receiptBatchSkipped=0;closeDlg()\">完成</button>`)}
function openSavedReceipt""",
"receipt save queue")

p.write_text(s)

p=root/'app/src/main/java/tw/ajo/travelnotebook/MainActivity.java'
m=p.read_text()
if 'import org.json.JSONArray;' not in m:
    m=m.replace('import org.json.JSONObject;','import org.json.JSONObject;\nimport org.json.JSONArray;')

old="""        if (requestCode == REQ_RECEIPT_GALLERY) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                pendingReceiptUri = data.getData();
                pendingReceiptFromCamera = false;
                try { getContentResolver().takePersistableUriPermission(pendingReceiptUri, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); } catch (Exception ignored) {}
                processReceiptUri(pendingReceiptUri);
            } else { pendingReceiptUri = null; pendingReceiptFromCamera = false; }
            return;
        }
"""
new="""        if (requestCode == REQ_RECEIPT_GALLERY) {
            if (resultCode == RESULT_OK && data != null) {
                JSONArray arr = new JSONArray();
                if (data.getClipData() != null) {
                    int n=data.getClipData().getItemCount();
                    for(int x=0;x<n;x++){ Uri u=data.getClipData().getItemAt(x).getUri(); if(u!=null) arr.put(u.toString()); }
                } else if (data.getData()!=null) arr.put(data.getData().toString());
                pendingReceiptUri=null; pendingReceiptFromCamera=false;
                final String payload=arr.toString();
                if (pageReady) webView.evaluateJavascript("receiptGallerySelected("+JSONObject.quote(payload)+")",null);
            } else { pendingReceiptUri=null; pendingReceiptFromCamera=false; }
            return;
        }
"""
if old not in m: raise SystemExit('v1.0.17 patch failed: gallery result')
m=m.replace(old,new,1)

old="""    private void launchReceiptGallery() {
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
"""
new="""    private void launchReceiptGallery() {
        discardPendingReceipt();
        String[] oppoPackages=new String[]{"com.coloros.gallery3d","com.oplus.gallery"};
        for(String pkg:oppoPackages){
            Intent oppo=new Intent(Intent.ACTION_GET_CONTENT); oppo.setType("image/*"); oppo.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); oppo.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); oppo.setPackage(pkg);
            if(oppo.resolveActivity(getPackageManager())!=null){ startActivityForResult(oppo,REQ_RECEIPT_GALLERY); return; }
        }
        Intent pick=new Intent(Intent.ACTION_GET_CONTENT); pick.setType("image/*"); pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(pick.resolveActivity(getPackageManager())!=null){ startActivityForResult(pick,REQ_RECEIPT_GALLERY); return; }
        Intent doc=new Intent(Intent.ACTION_OPEN_DOCUMENT); doc.addCategory(Intent.CATEGORY_OPENABLE); doc.setType("image/*"); doc.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); doc.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(doc,REQ_RECEIPT_GALLERY);
    }
"""
if old not in m: raise SystemExit('v1.0.17 patch failed: gallery launch')
m=m.replace(old,new,1)

marker="""        @JavascriptInterface public void retryReceiptOcr() {
            runOnUiThread(() -> { if (pendingReceiptUri != null) processReceiptUri(pendingReceiptUri); else sendReceiptError("找不到剛才的收據照片，請重新拍照。"); });
        }
"""
insert="""        @JavascriptInterface public void scanReceiptUri(String uriText) {
            runOnUiThread(() -> {
                try { pendingReceiptUri=Uri.parse(uriText); pendingReceiptFromCamera=false; processReceiptUri(pendingReceiptUri); }
                catch(Exception e){ sendReceiptError("這張收據照片無法讀取，請略過或換一張收據。"); }
            });
        }

"""
if marker not in m: raise SystemExit('v1.0.17 patch failed: bridge marker')
m=m.replace(marker,insert+marker,1)
p.write_text(m)

p=root/'app/build.gradle'
g=p.read_text()
g=re.sub(r"versionCode\s+\d+","versionCode 117",g,count=1)
g=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.17'",g,count=1)
p.write_text(g)
