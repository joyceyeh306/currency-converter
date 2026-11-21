package tw.ajo.phototext;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

final class GeoStore extends SQLiteOpenHelper {
 static final class Record{
  String uri,stamp,name,country;long date;double lat,lon;int state;
  Record(String uri,String stamp,String name,long date,double lat,double lon,String country,int state){this.uri=uri;this.stamp=stamp;this.name=name;this.date=date;this.lat=lat;this.lon=lon;this.country=country;this.state=state;}
 }
 static final class Stats{int total,gps,missing,error,updated;}
 static final class Page{ArrayList<Record> rows=new ArrayList<>();int matches;Stats stats;String error="";}
 interface Progress{void update(int done,int total,int changed);}
 GeoStore(Context c){super(c,"photo_geo014.db",null,1);setWriteAheadLoggingEnabled(true);}
 @Override public void onCreate(SQLiteDatabase d){
  d.execSQL("CREATE TABLE gps(uri TEXT PRIMARY KEY,stamp TEXT NOT NULL,name TEXT NOT NULL,date_ms INTEGER NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,country TEXT NOT NULL,state INTEGER NOT NULL)");
  d.execSQL("CREATE INDEX gps_country ON gps(country,date_ms DESC)");
  d.execSQL("CREATE INDEX gps_latlon ON gps(lat,lon)");
 }
 @Override public void onUpgrade(SQLiteDatabase d,int a,int b){}
 boolean permission(Context c){return c.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)==PackageManager.PERMISSION_GRANTED&&MediaSync.full(c);}
 HashMap<String,String> known(){
  HashMap<String,String> map=new HashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,stamp FROM gps",null)){while(c.moveToNext())map.put(c.getString(0),c.getString(1));}return map;
 }
 void batch(ArrayList<Record> rows){
  if(rows.isEmpty())return;SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
   for(Record p:rows){ContentValues v=new ContentValues();v.put("uri",p.uri);v.put("stamp",p.stamp);v.put("name",p.name);v.put("date_ms",p.date);v.put("lat",p.lat);v.put("lon",p.lon);v.put("country",p.country);v.put("state",p.state);d.insertWithOnConflict("gps",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
   d.setTransactionSuccessful();
  }finally{d.endTransaction();}rows.clear();
 }
 void cleanup(Context ctx,MediaSync.Snapshot snapshot,BooleanSupplier canceled){
  HashMap<String,String> old=known();ArrayList<String> removed=new ArrayList<>();
  for(String key:old.keySet()){if(canceled.getAsBoolean())return;
   if(!snapshot.items.containsKey(key)&&MediaSync.confirmedMissing(ctx,key,snapshot.complete))removed.add(key);
  }
  if(canceled.getAsBoolean()||!permission(ctx))return;
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{for(String key:removed)d.delete("gps","uri=?",new String[]{key});d.setTransactionSuccessful();}finally{d.endTransaction();}
 }
 Record exif(Context ctx,SyncPlan.Item item,PhotoMetadata.Item metadata,GeoCountries countries){
  double lat=0,lon=0;int state=0;String country="";
  try{
   Uri u=MediaStore.setRequireOriginal(Uri.parse(item.uri));
   try(InputStream in=ctx.getContentResolver().openInputStream(u)){
    if(in==null)throw new IOException("image stream null");
    ExifInterface exif=new ExifInterface(in);float[] coords=new float[2];
    if(exif.getLatLong(coords)&&GeoMath.valid(coords[0],coords[1])){
     lat=coords[0];lon=coords[1];country=countries.at(lat,lon);state=1;
    }
   }
  }catch(Exception e){state=-1;}
  return new Record(item.uri,item.stamp,item.name,metadata==null?0:metadata.date,lat,lon,country,state);
 }
 Stats scan(Context ctx,BooleanSupplier canceled,Progress progress)throws Exception{
  if(!permission(ctx))throw new SecurityException("需要完整照片與媒體位置權限");
  MediaSync.Snapshot snapshot=MediaSync.read(ctx,canceled);
  if(snapshot.complete.isEmpty()){if(canceled.getAsBoolean())return stats();throw new IOException("相簿暫時無法讀取，索引未變更");}
  HashMap<String,String> old=known();GeoCountries countries=new GeoCountries(ctx);
  ArrayList<Record> pending=new ArrayList<>();int done=0,changed=0,total=snapshot.items.size();
  for(SyncPlan.Item p:snapshot.items.values()){
   if(canceled.getAsBoolean()||!permission(ctx))break;
   PhotoMetadata.Item md=snapshot.metadata.get(p.uri);
   if(!p.stamp.equals(old.get(p.uri))){pending.add(exif(ctx,p,md,countries));changed++;}
   done++;if(pending.size()>=48)batch(pending);
   if(done%48==0)progress.update(done,total,changed);
  }
  batch(pending);
  if(!canceled.getAsBoolean()&&permission(ctx))cleanup(ctx,snapshot,canceled);
  progress.update(done,total,changed);
  return stats();
 }
 Stats stats(){
  Stats s=new Stats();try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(CASE WHEN state=1 THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN state=0 THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN state=-1 THEN 1 ELSE 0 END),0) FROM gps",null)){
   if(c.moveToFirst()){s.total=c.getInt(0);s.gps=c.getInt(1);s.missing=c.getInt(2);s.error=c.getInt(3);}
  }return s;
 }
 Page find(GeoPlaces.Area area,int limit,BooleanSupplier canceled){
  Page out=new Page();out.stats=stats();if(area==null)return out;
  String sql="SELECT uri,stamp,name,date_ms,lat,lon,country,state FROM gps WHERE state=1"+(area.country?" AND country=?":"")+" ORDER BY date_ms DESC,uri ASC";
  String[] arg=area.country?new String[]{area.iso3}:null;
  try(Cursor c=getReadableDatabase().rawQuery(sql,arg)){
   while(c.moveToNext()){
    if(canceled.getAsBoolean())return out;
    double lat=c.getDouble(4),lon=c.getDouble(5);
    if(!area.country&&GeoMath.km(area.lat,area.lon,lat,lon)>area.radius)continue;
    out.matches++;if(out.rows.size()<limit)out.rows.add(new Record(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),lat,lon,c.getString(6),1));
   }
  }return out;
 }
}
