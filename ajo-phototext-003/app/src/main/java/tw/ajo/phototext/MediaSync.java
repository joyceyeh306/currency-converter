package tw.ajo.phototext;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.util.*;
import java.util.function.BooleanSupplier;
final class MediaSync {
 static final class Snapshot {
  final LinkedHashMap<String,SyncPlan.Item> items=new LinkedHashMap<>();
  final HashMap<String,PhotoMetadata.Item> metadata=new HashMap<>();
  final HashSet<String> complete=new HashSet<>();
 }
 static boolean full(Context c){return c.checkSelfPermission(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
 static Snapshot read(Context c,BooleanSupplier canceled){
  Snapshot s=new Snapshot();
  if(!full(c))return s;
  ArrayList<String> volumes=new ArrayList<>(MediaStore.getExternalVolumeNames(c));Collections.sort(volumes);
  String[] cols={"_id","volume_name","relative_path","bucket_display_name","datetaken","date_modified","date_added","_size","_display_name"};
  for(String volume:volumes){if(canceled.getAsBoolean()||!full(c))break;
   LinkedHashMap<String,SyncPlan.Item> local=new LinkedHashMap<>();HashMap<String,PhotoMetadata.Item> md=new HashMap<>();
   try(Cursor cur=c.getContentResolver().query(MediaStore.Images.Media.getContentUri(volume),cols,null,null,"date_added DESC, _id DESC")){
    if(cur==null)continue;boolean interrupted=false;
    while(cur.moveToNext()){if(canceled.getAsBoolean()){interrupted=true;break;}
     String key=ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(volume),PhotoMetadata.value(cur,"_id")).toString();
     String stamp=PhotoMetadata.value(cur,"date_modified")+":"+PhotoMetadata.value(cur,"_size");
     String name=PhotoMetadata.string(cur,"_display_name");if(name.isEmpty())name="照片";
     local.put(key,new SyncPlan.Item(key,name,stamp));md.put(key,PhotoMetadata.item(cur,key));
    }
    if(!interrupted&&full(c)){s.items.putAll(local);s.metadata.putAll(md);s.complete.add(volume);}
   }catch(Exception ignored){} // no verified snapshot: never delete that volume
  }
  return s;
 }
 static boolean confirmedMissing(Context c,String key,Set<String> completed){
  String volume=SyncPlan.volume(key);
  if(!full(c)||!completed.contains(volume)||!MediaStore.getExternalVolumeNames(c).contains(volume))return false;
  try(Cursor cur=c.getContentResolver().query(Uri.parse(key),new String[]{"_id"},null,null,null)){
   return cur!=null&&!cur.moveToFirst()&&full(c);
  }catch(Exception ex){return false;}
 }
}
