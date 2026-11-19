package tw.ajo.phototext;
import android.content.Context;
import android.graphics.*;
import android.view.*;
final class ZoomPhoto extends View {
 Bitmap photo; boolean failed=false; float zoom=1,dx=0,dy=0,px,py; final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG); final ScaleGestureDetector scale; final GestureDetector gestures;
 ZoomPhoto(Context c){super(c);scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float old=zoom;zoom=Math.max(1,Math.min(6,zoom*d.getScaleFactor()));float ratio=zoom/old;dx=(dx+getWidth()/2f-d.getFocusX())*ratio+d.getFocusX()-getWidth()/2f;dy=(dy+getHeight()/2f-d.getFocusY())*ratio+d.getFocusY()-getHeight()/2f;bound();invalidate();return true;}});gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onDoubleTap(MotionEvent e){zoom=zoom>1?1:2.5f;dx=dy=0;invalidate();return true;}});}
 void setPhoto(Bitmap b){photo=b;zoom=1;dx=dy=0;invalidate();}
 float fit(){return photo==null?1:Math.min((float)getWidth()/photo.getWidth(),(float)getHeight()/photo.getHeight());}
 void bound(){if(photo==null)return;float f=fit()*zoom, bx=Math.max(0,(photo.getWidth()*f-getWidth())/2),by=Math.max(0,(photo.getHeight()*f-getHeight())/2);dx=Math.max(-bx,Math.min(bx,dx));dy=Math.max(-by,Math.min(by,dy));}
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){bound();}
 @Override protected void onDraw(Canvas c){super.onDraw(c);if(photo==null){paint.setColor(0xff526b66);paint.setTextSize(16*getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(Paint.Align.CENTER);c.drawText(failed?"無法讀取原始照片":"載入照片…",getWidth()/2f,getHeight()/2f,paint);return;}float f=fit()*zoom,w=photo.getWidth()*f,h=photo.getHeight()*f,x=(getWidth()-w)/2f+dx,y=(getHeight()-h)/2f+dy;c.drawBitmap(photo,null,new RectF(x,y,x+w,y+h),paint);}
 @Override public boolean onTouchEvent(MotionEvent e){scale.onTouchEvent(e);gestures.onTouchEvent(e);int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_UP){int p=action==MotionEvent.ACTION_POINTER_UP&&e.getActionIndex()==0?1:0;px=e.getX(p);py=e.getY(p);}else if(action==MotionEvent.ACTION_MOVE&&e.getPointerCount()==1&&!scale.isInProgress()){dx+=e.getX()-px;dy+=e.getY()-py;px=e.getX();py=e.getY();bound();invalidate();}else if(action==MotionEvent.ACTION_UP)performClick();return true;}
 @Override public boolean performClick(){super.performClick();return true;}
}