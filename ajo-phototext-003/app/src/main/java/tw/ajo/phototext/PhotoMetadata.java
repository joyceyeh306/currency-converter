package tw.ajo.phototext;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.*;
final class PhotoMetadata {
 static class Item{
  String uri,key,label,kind;long date;
  Item(String u,String k,String l,String t,long d){uri=u;key=k;label=l;kind=t;date=d;}
 }
 static final String[] COLS={"_id","volume_name","relative_path","bucket_display_name","datetaken","date_modified","date_added"};
 static long value(Cursor c,String col){int i=c.getColumnIndex(col);return i<0||c.isNull(i)?0:c.getLong(i);}
 static String string(Cursor c,String col){int i=c.getColumnIndex(col);return i<0||c.isNull(i)?"":c.getString(i);}
 static Item item(Cursor c,String u){String vol=string(c,"volume_name"),path=string(c,"relative_path"),bucket=string(c,"bucket_display_name");
  String lower=(path+"/"+bucket).toLowerCase(Locale.ROOT);String kind="";
  if(lower.contains("screenshot")||lower.contains("螢幕截圖")||lower.contains("截圖")||lower.contains("屏幕截图"))kind="screens";
  else if(lower.startsWith("dcim/camera")||lower.contains("/camera")||lower.endsWith("/相機"))kind="camera";
  String key=path.isEmpty()?(bucket.isEmpty()?"":vol+"|"+bucket):vol+"|"+path;
  String label=path.isEmpty()?bucket:path.replaceAll("/+$","");
  long date=value(c,"datetaken");if(date<=0)date=value(c,"date_modified")*1000L;if(date<=0)date=value(c,"date_added")*1000L;
  return new Item(u,key,label,kind,date);
 }
 static HashMap<String,Item> read(Context ctx,Collection<String> indexed){
  HashMap<String,Item> result=new HashMap<>();
  try(Cursor c=ctx.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,COLS,null,null,null)){
   if(c!=null)while(c.moveToNext()){String vol=string(c,"volume_name");Uri u=ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(vol.isEmpty()?MediaStore.VOLUME_EXTERNAL:vol),value(c,"_id"));result.put(u.toString(),item(c,u.toString()));}
  }catch(Exception ignored){}
  // Picker/document URIs use their own provider metadata; never guess their album from filename.
  for(String u:indexed){if(result.containsKey(u))continue;
   Uri uri=Uri.parse(u);if("media".equals(uri.getAuthority())&&!u.contains("/picker/")&&!u.contains("/picker_get_content/")&&u.contains("/images/media/"))continue;
   try(Cursor c=ctx.getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){Item p=item(c,u);if(p.date>0||!p.key.isEmpty())result.put(u,p);}}catch(Exception ignored){}
  }
  return result;
 }
}
