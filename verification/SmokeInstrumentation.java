package tw.ajo.travelnotebook;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
/** Synthetic emulator-only fixtures, never runs in the release application. */
public final class SmokeInstrumentation extends Instrumentation {
    private String mode;
    private WebView web;
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args.getString("mode","verify");start();}
    private String js(String code)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
        runOnMainSync(()->web.evaluateJavascript(code,v->{result.set(v);latch.countDown();}));
        if(!latch.await(15,TimeUnit.SECONDS))throw new Exception("JavaScript timeout");return result.get();
    }
    private void waitFor(String expression)throws Exception{for(int i=0;i<120;i++){if("true".equals(js(expression)))return;Thread.sleep(250);}throw new Exception("Condition failed: "+expression);}
    private void require(boolean pass,String message)throws Exception{if(!pass)throw new Exception(message);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Intent intent=new Intent(Intent.ACTION_MAIN).setClassName("tw.ajo.travelnotebook","tw.ajo.travelnotebook.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=startActivitySync(intent);java.lang.reflect.Field f=activity.getClass().getDeclaredField("webView");f.setAccessible(true);web=(WebView)f.get(activity);
        waitFor("typeof data!=='undefined'&&typeof db==='function'");Thread.sleep(1500);
        File marker=new File(getTargetContext().getFilesDir(),"upgrade-marker.txt");File receipt=new File(getTargetContext().getFilesDir(),"receipts/smoke.png");
        if("seed".equals(mode)){
            receipt.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(receipt)){out.write(android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6l8AAAAASUVORK5CYII=",0));}
            try(FileOutputStream out=new FileOutputStream(marker)){out.write("preserve-v193".getBytes());}
            js("data=migrateData({font:'normal',currentTripId:'SMOKE',trips:[{id:'SMOKE',name:'Upgrade fixture',currencies:['TWD'],memo:'preserve memo',days:[{id:'DAY',date:'2026-10-12',items:[{id:'ITEM',title:'preserve item'}],costs:[{id:'COST',category:'餐飲',amount:42,currency:'TWD',receiptUri:'ajo-receipt://smoke.png'}]}]}]});saveData();window.fixtureReady=false;putAttachment({id:'FILE',itemId:'ITEM',name:'sample.txt',type:'text/plain',blob:new Blob(['hello'])}).then(()=>window.fixtureReady=true)");
            waitFor("window.fixtureReady===true");Thread.sleep(2000);
            require("true".equals(js("JSON.parse(localStorage.getItem(APPKEY)).trips[0].id==='SMOKE'")),"Seed not in LocalStorage");
            runOnMainSync(()->activity.finish());Thread.sleep(2000);
        }else if("baselineCheck".equals(mode)){
            require("true".equals(js("data.trips[0].id==='SMOKE'")),"Baseline persistence: "+js("JSON.stringify({data,raw:localStorage.getItem(APPKEY)})"));
        }else{
            waitFor("window.appStartupReady===true");
            require(marker.isFile()&&receipt.isFile(),"Native private files lost in upgrade");
            require("true".equals(js("data.trips[0].id==='SMOKE'&&data.trips[0].memo==='preserve memo'&&data.trips[0].days[0].costs[0].receiptUri==='ajo-receipt://smoke.png'")),"Trip or receipt reference lost: "+js("JSON.stringify({data,raw:localStorage.getItem(APPKEY),blocked:storageBlocked,url:location.href})"));
            js("window.fixtureReady=false;getAttachments('ITEM').then(async a=>window.fixtureReady=a.length===1&&(await a[0].blob.text())==='hello')");waitFor("window.fixtureReady===true");
            js("route={tab:'memo',tripId:'SMOKE',dayId:null};renderMemo()");require("true".equals(js("$('#memoArea').readOnly")),"Memo opened in edit mode");
            js("$('#memoEditBtn').click()");require("false".equals(js("$('#memoArea').readOnly")),"Memo edit button failed");js("$('#memoEditBtn').click()");
            require("true".equals(js("$('#memoArea').readOnly&&!!$('#hiddenFile')")),"Memo/input regression");
            js("route={tab:'trips',tripId:'SMOKE',dayId:'DAY'};activeReceiptSplit={mode:'view',id:'COST'};androidBack()");require("true".equals(js("activeReceiptSplit===null&&route.dayId==='DAY'")),"Receipt back navigation");
            require("true".equals(js("guessReceiptDateTime(['Date 2026-09-29 PM 7:05'],'2026-10-01')==='2026/09/29 19:05'&&actualTwdValue({actualTwd:0})===0")),"Date/money regression");
            js("window.fixtureReady=false;duplicateItem('ITEM').then(()=>getAttachments(data.trips[0].days[0].items[1].id)).then(a=>window.fixtureReady=a.length===1)");waitFor("window.fixtureReady===true");
            js("window.fixtureReady=false;pendingRestoreFile={text:async()=>JSON.stringify({version:2,data:{trips:[{id:'BAD',days:[]}]},files:[{id:'B',itemId:'B',blob:'bad'}]})};confirmRestore().then(()=>window.fixtureReady=data.trips[0].id==='SMOKE')");waitFor("window.fixtureReady===true");
            js("closeDlg();saveAppSession()");
            File pdf=new File(getTargetContext().getCacheDir(),"smoke-20-pages.pdf");android.graphics.pdf.PdfDocument doc=new android.graphics.pdf.PdfDocument();for(int i=0;i<20;i++){android.graphics.pdf.PdfDocument.Page page=doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,i+1).create());page.getCanvas().drawText("Page "+(i+1),30,60,new android.graphics.Paint());doc.finishPage(page);}try(FileOutputStream out=new FileOutputStream(pdf)){doc.writeTo(out);}doc.close();
            Activity viewer=startActivitySync(new Intent().setClassName("tw.ajo.travelnotebook","tw.ajo.travelnotebook.AttachmentViewerActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("path",pdf.getAbsolutePath()).putExtra("mime","application/pdf").putExtra("name","20 pages"));
            Thread.sleep(2500);java.lang.reflect.Field vf=viewer.getClass().getDeclaredField("documentView");vf.setAccessible(true);Object view=vf.get(viewer);java.lang.reflect.Field sizes=view.getClass().getDeclaredField("sizes"),bytes=view.getClass().getDeclaredField("cachedBytes");sizes.setAccessible(true);bytes.setAccessible(true);
            AtomicReference<Exception> failure=new AtomicReference<>();runOnMainSync(()->{try{require(((java.util.List<?>)sizes.get(view)).size()==20,"PDF pages missing");require(bytes.getLong(view)>0&&bytes.getLong(view)<=24L*1024*1024,"PDF cache outside bound");viewer.finish();}catch(Exception e){failure.set(e);}});if(failure.get()!=null)throw failure.get();
        }
        result.putString("result","PASS "+mode+" Android "+android.os.Build.VERSION.SDK_INT);finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("result","FAIL "+e);finish(Activity.RESULT_CANCELED,result);}}
}
