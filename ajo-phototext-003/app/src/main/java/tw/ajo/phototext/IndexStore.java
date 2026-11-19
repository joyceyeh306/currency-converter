package tw.ajo.phototext;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.text.Normalizer;
import java.util.*;

final class IndexStore extends SQLiteOpenHelper {
 static final String[] MODES={"繁中＋英文（預設）","英文＋數字"};
 static class Photo {
  String uri,name,stamp; int mode; long seq;
  Photo(String u,String n,String s,int m){uri=u;name=n;stamp=s;mode=m;}
 }
 static class Result {
  String uri,name,text,error; int mode; long ms;
  Result(Cursor c){uri=c.getString(0);name=c.getString(1);mode=c.getInt(2);text=c.getString(3);error=c.getString(4);ms=c.getLong(5);}
 }
 static class Job {
  int total,done,skipped,failed,withText,empty; long ms,elapsed; String scope;
  int pending(){return total-done;}
 }
 static class Page {
  final ArrayList<Result> rows=new ArrayList<>(); int matches,ok,empty,failed; Job job;
 }
 IndexStore(Context c){super(c,"photo_index006.db",null,2);setWriteAheadLoggingEnabled(true);}
 @Override public void onCreate(SQLiteDatabase d){
  d.execSQL("CREATE TABLE records(uri TEXT NOT NULL, mode INTEGER NOT NULL, name TEXT NOT NULL, stamp TEXT NOT NULL, body TEXT NOT NULL, norm TEXT NOT NULL, folded TEXT NOT NULL DEFAULT '', error TEXT NOT NULL, ms INTEGER NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY(uri,mode))");
  d.execSQL("CREATE INDEX records_updated ON records(updated DESC)");
  d.execSQL("CREATE TABLE queue(seq INTEGER PRIMARY KEY,uri TEXT NOT NULL,name TEXT NOT NULL,stamp TEXT NOT NULL,mode INTEGER NOT NULL,UNIQUE(uri,mode))");
  d.execSQL("CREATE TABLE job(id INTEGER PRIMARY KEY CHECK(id=1),total INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,skipped INTEGER NOT NULL DEFAULT 0,failed INTEGER NOT NULL DEFAULT 0,withtext INTEGER NOT NULL DEFAULT 0,empty INTEGER NOT NULL DEFAULT 0,ms INTEGER NOT NULL DEFAULT 0,elapsed INTEGER NOT NULL DEFAULT 0,scope TEXT NOT NULL)");
  d.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY,v TEXT NOT NULL)");createPhotoMeta(d);
 }
 void createPhotoMeta(SQLiteDatabase d){d.execSQL("CREATE TABLE IF NOT EXISTS photo_meta(uri TEXT PRIMARY KEY,album_key TEXT NOT NULL,album_label TEXT NOT NULL,kind TEXT NOT NULL,date_ms INTEGER NOT NULL)");d.execSQL("CREATE INDEX IF NOT EXISTS photo_meta_album ON photo_meta(album_key)");}
 @Override public void onUpgrade(SQLiteDatabase d,int old,int now){if(old<2){d.execSQL("ALTER TABLE records ADD COLUMN folded TEXT NOT NULL DEFAULT ''");createPhotoMeta(d);
  long at=0;while(true){ArrayList<Long> ids=new ArrayList<>();ArrayList<String> values=new ArrayList<>();
   try(Cursor c=d.rawQuery("SELECT rowid,body FROM records WHERE rowid>? ORDER BY rowid LIMIT 150",new String[]{String.valueOf(at)})){while(c.moveToNext()){at=c.getLong(0);ids.add(at);values.add(HanFold.fold(c.getString(1)));}}
   if(ids.isEmpty())break;for(int i=0;i<ids.size();i++){ContentValues v=new ContentValues();v.put("folded",values.get(i));d.update("records",v,"rowid=?",new String[]{String.valueOf(ids.get(i))});}
  }
 }}
 void refreshMetadata(Context ctx){SQLiteDatabase d=getWritableDatabase();ArrayList<String> uris=new ArrayList<>();try(Cursor c=d.rawQuery("SELECT DISTINCT uri FROM records",null)){while(c.moveToNext())uris.add(c.getString(0));}
  HashMap<String,PhotoMetadata.Item> data=PhotoMetadata.read(ctx,uris);
  d.beginTransaction();try{d.delete("photo_meta",null,null);for(PhotoMetadata.Item p:data.values()){ContentValues v=new ContentValues();v.put("uri",p.uri);v.put("album_key",p.key);v.put("album_label",p.label);v.put("kind",p.kind);v.put("date_ms",p.date);d.insertWithOnConflict("photo_meta",null,v,SQLiteDatabase.CONFLICT_REPLACE);}d.setTransactionSuccessful();}finally{d.endTransaction();}
 }
 ArrayList<String[]> albums(){ArrayList<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT p.album_key,p.album_label FROM photo_meta p JOIN records r ON r.uri=p.uri WHERE p.album_key<>'' ORDER BY p.album_label COLLATE NOCASE,p.album_key",null)){while(c.moveToNext())out.add(new String[]{c.getString(0),c.getString(1)});}return out;}

 static String norm(String x){return Normalizer.normalize(x==null?"":x,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+","");}
 void put(SQLiteDatabase d,Photo p,String body,String error,long ms,long updated){
  ContentValues v=new ContentValues();v.put("uri",p.uri);v.put("mode",p.mode);v.put("name",p.name);v.put("stamp",p.stamp);v.put("body",body);v.put("norm",norm(body));v.put("folded",HanFold.fold(body));v.put("error",error);v.put("ms",ms);v.put("updated",updated);
  d.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE);
 }
 void migrate(Context c)throws Exception{
  SQLiteDatabase d=getWritableDatabase();
  try(Cursor flag=d.rawQuery("SELECT v FROM meta WHERE k='legacy005'",null)){if(flag.moveToFirst())return;}
  File old=new File(c.getFilesDir(),"index.json");
  JSONArray a=new JSONArray();
  if(old.exists()){try(FileInputStream in=new FileInputStream(old)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);a=new JSONArray(out.toString("UTF-8"));}}
  d.beginTransaction();try{
   try(Cursor flag=d.rawQuery("SELECT v FROM meta WHERE k='legacy005'",null)){if(flag.moveToFirst()){d.setTransactionSuccessful();return;}}
   long now=System.currentTimeMillis();for(int i=a.length()-1;i>=0;i--){JSONObject j=a.getJSONObject(i);int mode=MODES[1].equals(j.optString("mode"))?1:0;String u=j.getString("uri");put(d,new Photo(u,j.optString("name","照片"),"",mode),j.optString("text"),j.optString("error"),j.optLong("ms"),now-i);}
   d.execSQL("INSERT INTO meta(k,v) VALUES('legacy005','1')");d.setTransactionSuccessful();
  }finally{d.endTransaction();}
 }
 HashMap<String,String> scanned(int mode){HashMap<String,String> out=new HashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,stamp FROM records WHERE mode=? AND error='' ",new String[]{String.valueOf(mode)})){while(c.moveToNext())out.put(c.getString(0),c.getString(1));}return out;}
 Job job(){try(Cursor c=getReadableDatabase().rawQuery("SELECT total,done,skipped,failed,withtext,empty,ms,elapsed,scope FROM job WHERE id=1",null)){if(!c.moveToFirst())return null;Job j=new Job();j.total=c.getInt(0);j.done=c.getInt(1);j.skipped=c.getInt(2);j.failed=c.getInt(3);j.withText=c.getInt(4);j.empty=c.getInt(5);j.ms=c.getLong(6);j.elapsed=c.getLong(7);j.scope=c.getString(8);return j;}}
 void createJob(ArrayList<Photo> photos,int skipped,String scope){
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
   d.delete("queue",null,null);d.delete("job",null,null);int count=0;
   for(Photo p:photos){ContentValues v=new ContentValues();v.put("uri",p.uri);v.put("name",p.name);v.put("stamp",p.stamp);v.put("mode",p.mode);if(d.insertWithOnConflict("queue",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1)count++;}
   d.execSQL("INSERT INTO job(id,total,skipped,scope) VALUES(1,?,?,?)",new Object[]{count,skipped,scope});d.setTransactionSuccessful();
  }finally{d.endTransaction();}
 }
 Photo next(){try(Cursor c=getReadableDatabase().rawQuery("SELECT seq,uri,name,stamp,mode FROM queue ORDER BY seq LIMIT 1",null)){if(!c.moveToFirst())return null;Photo p=new Photo(c.getString(1),c.getString(2),c.getString(3),c.getInt(4));p.seq=c.getLong(0);return p;}}
 void finish(Photo p,String body,String error,long ms,long elapsed){
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
   put(d,p,body,error,ms,System.currentTimeMillis());
   if(d.delete("queue","seq=?",new String[]{String.valueOf(p.seq)})!=1)throw new IllegalStateException("Missing queue item");
   int fail=error.isEmpty()?0:1,empty=fail==0&&body.trim().isEmpty()?1:0;
   d.execSQL("UPDATE job SET done=done+1,failed=failed+?,empty=empty+?,withtext=withtext+?,ms=ms+?,elapsed=elapsed+? WHERE id=1",new Object[]{fail,empty,1-fail-empty,ms,elapsed});
   d.setTransactionSuccessful();
  }finally{d.endTransaction();}
 }
 void discard(){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("queue",null,null);d.delete("job",null,null);d.setTransactionSuccessful();}finally{d.endTransaction();}}
 void clear(){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("queue",null,null);d.delete("job",null,null);d.delete("records",null,null);d.setTransactionSuccessful();}finally{d.endTransaction();}}
 Page search(SearchSpec spec,int limit){
  SQLiteDatabase d=getReadableDatabase();Page p=new Page();SearchSpec.Where w=spec.where(HanFold::fold);String where=w.sql;String[] args=w.args;String joined=" FROM records r LEFT JOIN photo_meta p ON r.uri=p.uri WHERE ";
  try(Cursor c=d.rawQuery("SELECT COUNT(*), COALESCE(SUM(CASE WHEN error='' AND length(trim(body))>0 THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN error='' AND length(trim(body))=0 THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN error<>'' THEN 1 ELSE 0 END),0) FROM records",null)){if(c.moveToFirst()){p.ok=c.getInt(1);p.empty=c.getInt(2);p.failed=c.getInt(3);}}
  try(Cursor c=d.rawQuery("SELECT COUNT(*)"+joined+where,args)){if(c.moveToFirst())p.matches=c.getInt(0);}
  try(Cursor c=d.rawQuery("SELECT r.uri,r.name,r.mode,r.body,r.error,r.ms"+joined+where+" ORDER BY "+spec.order()+" LIMIT "+limit,args)){while(c.moveToNext())p.rows.add(new Result(c));}
  p.job=job();return p;
 }
}
