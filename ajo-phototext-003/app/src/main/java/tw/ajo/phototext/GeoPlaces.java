package tw.ajo.phototext;
import android.content.Context;
import android.location.*;
import android.os.Build;
import java.util.*;
import java.util.concurrent.*;

final class GeoPlaces {
 static final class Area{
  final String title,iso3;final double lat,lon;final boolean country;final int radius;
  Area(String title,String iso,double lat,double lon,boolean c,int r){this.title=title;iso3=iso;this.lat=lat;this.lon=lon;country=c;radius=r;}
  String description(){return country?title+"・全國":title+"・周邊 "+radius+" 公里";}
 }
 private static final LinkedHashMap<String,Area> known=new LinkedHashMap<>();
 static String key(String s){return (s==null?"":s).trim().toLowerCase(Locale.ROOT).replaceAll("[\\s　]+","").replaceAll("(市|縣|県|郡)$","");}
 static void put(String title,String aliases,String iso,double lat,double lon){Area area=new Area(title,iso,lat,lon,false,100);for(String s:(title+","+aliases).split(","))known.put(key(s),area);}
 static{
  put("仙台","sendai,せんだい,仙台市","JPN",38.2682,140.8694);
  put("山形","yamagata,山形市,山形県","JPN",38.2554,140.3396);
  put("尾花澤","尾花沢,obanazawa,尾花沢市","JPN",38.6033,140.4050);
  put("宮城","miyagi,宮城縣,宮城県","JPN",38.45,141.0);
  put("松島","matsushima","JPN",38.372,141.061);
  put("東京","tokyo,とうきょう","JPN",35.6762,139.6503);
  put("大阪","osaka,おおさか","JPN",34.6937,135.5023);
  put("京都","kyoto,きょうと","JPN",35.0116,135.7681);
  put("奈良","nara","JPN",34.6851,135.8048);
  put("神戶","神戸,kobe","JPN",34.6901,135.1956);
  put("名古屋","nagoya","JPN",35.1815,136.9066);
  put("橫濱","横浜,yokohama","JPN",35.4437,139.6380);
  put("札幌","sapporo","JPN",43.0618,141.3545);
  put("北海道","hokkaido","JPN",43.2,142.7);
  put("函館","hakodate","JPN",41.7687,140.7288);
  put("青森","aomori","JPN",40.8244,140.7400);
  put("盛岡","morioka","JPN",39.7036,141.1527);
  put("秋田","akita","JPN",39.7200,140.1020);
  put("福島","fukushima","JPN",37.7503,140.4676);
  put("新潟","niigata","JPN",37.9162,139.0364);
  put("金澤","金沢,kanazawa","JPN",36.5613,136.6562);
  put("富山","toyama","JPN",36.6953,137.2113);
  put("長野","nagano","JPN",36.6485,138.1950);
  put("日光","nikko","JPN",36.7199,139.6982);
  put("高山","takayama","JPN",36.1461,137.2522);
  put("廣島","広島,hiroshima","JPN",34.3853,132.4553);
  put("福岡","fukuoka","JPN",33.5904,130.4017);
  put("長崎","nagasaki","JPN",32.7503,129.8777);
  put("熊本","kumamoto","JPN",32.8032,130.7079);
  put("沖繩","沖縄,okinawa","JPN",26.2124,127.6809);
  put("那霸","那覇,naha","JPN",26.2124,127.6809);
  put("石垣島","ishigaki","JPN",24.3407,124.1556);
  put("釜山","busan,부산","KOR",35.1796,129.0756);
  put("海雲台","haeundae,해운대","KOR",35.1632,129.1636);
  put("南浦洞","nampo,nampodong","KOR",35.097,129.034);
  put("首爾","seoul,서울,漢城","KOR",37.5665,126.9780);
  put("濟州","jeju,제주","KOR",33.4996,126.5312);
  put("仁川","incheon","KOR",37.4563,126.7052);
  put("因特拉肯","interlaken","CHE",46.6863,7.8632);
  put("策馬特","zermatt","CHE",46.0207,7.7491);
  put("蘇黎世","zurich,zürich","CHE",47.3769,8.5417);
  put("日內瓦","geneva,genève","CHE",46.2044,6.1432);
  put("伯恩","bern","CHE",46.9480,7.4474);
  put("倫敦","london","GBR",51.5074,-0.1278);
  put("愛丁堡","edinburgh","GBR",55.9533,-3.1883);
  put("約克","york","GBR",53.9590,-1.0815);
  put("牛津","oxford","GBR",51.7520,-1.2577);
  put("巨石陣","stonehenge","GBR",51.1789,-1.8262);
  put("北角","nordkapp,northcape","NOR",71.1725,25.7841);
  put("洪寧斯沃格","honningsvåg,honningsvag","NOR",70.9821,25.9704);
  put("奧斯陸","oslo","NOR",59.9139,10.7522);
  put("台中","臺中,taichung","TWN",24.1477,120.6736);
  put("台北","臺北,taipei","TWN",25.0330,121.5654);
  put("高雄","kaohsiung","TWN",22.6273,120.3014);
  put("花蓮","hualien","TWN",23.9872,121.6016);
 }
 static String countryAlias(String q){
  String k=key(q);switch(k){
   case "日本":case "日本國":case "japan":case "nippon":case "jp":return "JPN";
   case "韓國":case "南韓":case "大韓民國":case "korea":case "southkorea":case "republicofkorea":return "KOR";
   case "瑞士":case "switzerland":return "CHE";
   case "英國":case "聯合王國":case "uk":case "unitedkingdom":case "greatbritain":return "GBR";
   case "挪威":case "norway":return "NOR";
   case "台灣":case "臺灣":case "taiwan":return "TWN";
   case "美國":case "usa":case "unitedstates":case "unitedstatesofamerica":return "USA";
   case "德國":case "germany":return "DEU";
   case "法國":case "france":return "FRA";
   case "義大利":case "意大利":case "italy":return "ITA";
   case "西班牙":case "spain":return "ESP";
   case "奧地利":case "austria":return "AUT";
   case "加拿大":case "canada":return "CAN";
   case "澳洲":case "澳大利亞":case "australia":return "AUS";
   case "泰國":case "thailand":return "THA";
   case "越南":case "vietnam":return "VNM";
   case "中國":case "中國大陸":case "china":return "CHN";
  }for(String iso2:Locale.getISOCountries())try{
   Locale l=new Locale("",iso2);
   if(key(l.getDisplayCountry(Locale.TAIWAN)).equals(k)||key(l.getDisplayCountry(Locale.ENGLISH)).equals(k))return l.getISO3Country();
  }catch(Exception ignored){}
  return "";
 }
 static Area resolve(String s,int radius,Context ctx){
  String normalized=key(s);if(normalized.isEmpty())return null;
  String iso=countryAlias(s);if(!iso.isEmpty())return new Area(s.trim(),iso,0,0,true,0);
  Area knownArea=known.get(normalized);
  if(knownArea!=null)return new Area(knownArea.title,knownArea.iso3,knownArea.lat,knownArea.lon,false,radius);
  // The Android system geocoder is an optional fallback; device services may be offline or unavailable.
  if(!Geocoder.isPresent()||ctx==null)return null;
  try{
   Geocoder g=new Geocoder(ctx,Locale.TAIWAN);
   List<Address> addresses;
   if(Build.VERSION.SDK_INT>=33){
    CountDownLatch latch=new CountDownLatch(1);ArrayList<Address> out=new ArrayList<>();
    g.getFromLocationName(s,3,new Geocoder.GeocodeListener(){
     @Override public void onGeocode(List<Address> result){if(result!=null)out.addAll(result);latch.countDown();}
     @Override public void onError(String err){latch.countDown();}
    });
    if(!latch.await(5,TimeUnit.SECONDS))return null;addresses=out;
   }else addresses=g.getFromLocationName(s,3);
   if(addresses!=null)for(Address a:addresses){String cn=a.getCountryCode();if(!a.hasLatitude()||!a.hasLongitude())continue;
    String country="";try{country=new Locale("",cn).getISO3Country();}catch(Exception ignored){}
    // Reject broad results misinterpreted as a city, but permit unrecognized international towns.
    if(a.getLocality()==null&&a.getSubAdminArea()==null&&a.getAdminArea()==null)continue;
    return new Area(s.trim(),country,a.getLatitude(),a.getLongitude(),false,radius);
   }
  }catch(Exception ignored){}
  return null;
 }
}