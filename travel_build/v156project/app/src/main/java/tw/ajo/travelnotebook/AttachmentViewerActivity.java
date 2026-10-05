package tw.ajo.travelnotebook;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

public class AttachmentViewerActivity extends Activity {
    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(255,214,0));String path=getIntent().getStringExtra("path"),mime=getIntent().getStringExtra("mime"),name=getIntent().getStringExtra("name");LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(5),dp(8),dp(5));bar.setBackgroundColor(Color.rgb(255,214,0));Button back=new Button(this);back.setText("‹");back.setTextSize(24);back.setOnClickListener(v->finish());TextView title=new TextView(this);title.setText(name==null?"附件":name);title.setTextSize(17);title.setTextColor(Color.BLACK);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);bar.addView(back,new LinearLayout.LayoutParams(dp(46),dp(46)));bar.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));root.addView(bar);ScrollView scroll=new ScrollView(this);LinearLayout pages=new LinearLayout(this);pages.setOrientation(LinearLayout.VERTICAL);pages.setPadding(dp(4),dp(6),dp(4),dp(10));scroll.addView(pages,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);if(path==null){finish();return;}try{if("application/pdf".equalsIgnoreCase(mime)||path.toLowerCase().endsWith(".pdf"))showPdf(new File(path),pages);else showImage(path,pages);}catch(Exception e){Toast.makeText(this,"附件預覽失敗",Toast.LENGTH_LONG).show();}}
    private void showImage(String path,LinearLayout pages){Bitmap bm=BitmapFactory.decodeFile(path);if(bm==null)throw new RuntimeException();ZoomImageView iv=new ZoomImageView(this);iv.setImageBitmap(bm);iv.setAdjustViewBounds(true);pages.addView(iv,new LinearLayout.LayoutParams(-1,-2));}
    private void showPdf(File f,LinearLayout pages)throws Exception{ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer r=new PdfRenderer(fd);int width=Math.min(getResources().getDisplayMetrics().widthPixels*2,1800);for(int i=0;i<r.getPageCount();i++){PdfRenderer.Page p=r.openPage(i);int height=Math.max(1,(int)((double)width*p.getHeight()/p.getWidth()));Bitmap bm=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bm.eraseColor(Color.WHITE);p.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();ZoomImageView iv=new ZoomImageView(this);iv.setImageBitmap(bm);iv.setAdjustViewBounds(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);pages.addView(iv,lp);}r.close();fd.close();}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    static class ZoomImageView extends android.widget.ImageView {private final Matrix m=new Matrix();private final ScaleGestureDetector detector;private float userScale=1f,baseScale=1f;ZoomImageView(android.content.Context c){super(c);setScaleType(ImageView.ScaleType.MATRIX);detector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float next=Math.max(1f,Math.min(4f,userScale*d.getScaleFactor()));float factor=next/userScale;userScale=next;m.postScale(factor,factor,d.getFocusX(),d.getFocusY());setImageMatrix(m);return true;}});}private void fit(){if(getDrawable()==null||getWidth()==0)return;baseScale=(float)getWidth()/Math.max(1,getDrawable().getIntrinsicWidth());m.reset();m.setScale(baseScale,baseScale);userScale=1f;setImageMatrix(m);}@Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);fit();}@Override public void setImageBitmap(Bitmap b){super.setImageBitmap(b);post(this::fit);}@Override public boolean onTouchEvent(android.view.MotionEvent e){detector.onTouchEvent(e);boolean pinch=e.getPointerCount()>1||detector.isInProgress();getParent().requestDisallowInterceptTouchEvent(pinch);return true;}}
}