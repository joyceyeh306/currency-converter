package tw.ajo.phototext;
import java.util.*;
public class SyncPlanTest{
 static void c(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args){
  String root="content://media/external_primary/images/media/";String a=root+"1",b=root+"2",d=root+"3",e=root+"4",f="content://media/removed/images/media/5",g="content://media/picker/0/x/7";
  Map<String,SyncPlan.Item> live=new LinkedHashMap<>();live.put(a,new SyncPlan.Item(a,"rename","1:100"));live.put(b,new SyncPlan.Item(b,"edited","2:100"));live.put(e,new SyncPlan.Item(e,"new","4:100"));
  List<SyncPlan.Known> old=Arrays.asList(new SyncPlan.Known(a,"1:100",0),new SyncPlan.Known(b,"1:100",0),new SyncPlan.Known(b,"1:100",1),new SyncPlan.Known(d,"1:100",0),new SyncPlan.Known(f,"1:100",0),new SyncPlan.Known(g,"",0));
  SyncPlan p=SyncPlan.build(live,old,Set.of("external_primary"));
  c(p.skipped==1,"unchanged/rename");c(p.work.size()==3,"modified modes and new");c(p.work.get(0).item.uri.equals(b)&&p.work.get(0).mode==0,"default refresh");c(p.work.get(1).mode==1,"existing english refresh");c(p.work.get(2).item.uri.equals(e),"new");c(p.missing.equals(List.of(d)),"only missing mounted canonical");
  c(SyncPlan.build(live,old,Collections.emptySet()).missing.isEmpty(),"failed snapshot safe");
  c(SyncPlan.volume(g).isEmpty(),"picker safe");c(SyncPlan.volume("content://media/external_primary/images/media/abc").isEmpty(),"not canonical");
  c(SyncPlan.build(Collections.singletonMap(a,live.get(a)),List.of(new SyncPlan.Known(a,"1:100",0)),Set.of("external_primary")).work.isEmpty(),"successful or failed same version no repeated auto OCR");
  System.out.println("SyncPlan: 10 cases passed");
 }
}