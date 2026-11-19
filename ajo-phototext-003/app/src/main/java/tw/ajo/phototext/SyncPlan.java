package tw.ajo.phototext;
import java.util.*;
final class SyncPlan {
 static final class Item {final String uri,name,stamp; Item(String u,String n,String s){uri=u;name=n;stamp=s;}}
 static final class Known {final String uri,stamp;final int mode;Known(String u,String s,int m){uri=u;stamp=s;mode=m;}}
 static final class Work {final Item item;final int mode;Work(Item i,int m){item=i;mode=m;}}
 final ArrayList<Work> work=new ArrayList<>();final ArrayList<String> missing=new ArrayList<>();int skipped;
 static String volume(String uri){String p="content://media/";if(!uri.startsWith(p))return "";String[] s=uri.substring(p.length()).split("/");if(s.length!=4||!s[1].equals("images")||!s[2].equals("media")||!s[3].matches("[0-9]+"))return "";return s[0];}
 static SyncPlan build(Map<String,Item> current,List<Known> known,Set<String> completeVolumes){
  SyncPlan p=new SyncPlan();Map<String,Map<Integer,String>> index=new HashMap<>();
  LinkedHashSet<String> absent=new LinkedHashSet<>();
  for(Known k:known){index.computeIfAbsent(k.uri,x->new HashMap<>()).put(k.mode,k.stamp);
   if(!current.containsKey(k.uri)&&completeVolumes.contains(volume(k.uri)))absent.add(k.uri);}
  p.missing.addAll(absent);
  for(Item item:current.values()){Map<Integer,String> modes=index.get(item.uri);boolean changed=false;
   // Automatic indexing uses bilingual mode; preserve and refresh other already indexed modes too.
   if(modes==null||!item.stamp.equals(modes.get(0))){p.work.add(new Work(item,0));changed=true;}
   if(modes!=null)for(Map.Entry<Integer,String> e:modes.entrySet())if(e.getKey()!=0&&!item.stamp.equals(e.getValue())){p.work.add(new Work(item,e.getKey()));changed=true;}
   if(!changed)p.skipped++;
  }return p;
 }
}
