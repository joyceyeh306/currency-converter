package tw.ajo.phototext;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class GeoCountries {
 static final class Part{
  final double[] outer;final ArrayList<double[]> holes=new ArrayList<>();
  final double minLat,maxLat,minLon,maxLon;
  Part(double[] arr){outer=arr;double a=90,b=-90,c=180,d=-180;for(int i=0;i<arr.length;i+=2){c=Math.min(c,arr[i]);d=Math.max(d,arr[i]);a=Math.min(a,arr[i+1]);b=Math.max(b,arr[i+1]);}minLat=a;maxLat=b;minLon=c;maxLon=d;}
  boolean contains(double lat,double lon){if(lat<minLat||lat>maxLat||lon<minLon||lon>maxLon||!GeoMath.inside(lat,lon,outer))return false;for(double[] h:holes)if(GeoMath.inside(lat,lon,h))return false;return true;}
 }
 static final class Country{final String iso;final ArrayList<Part> parts=new ArrayList<>();Country(String s){iso=s;}}
 private final ArrayList<Country> countries=new ArrayList<>();
 GeoCountries(Context context)throws IOException,JSONException{
  try(InputStream in=context.getAssets().open("countries-110m.geojson");ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buf=new byte[32768];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
   JSONArray features=new JSONObject(out.toString("UTF-8")).getJSONArray("features");
   for(int i=0;i<features.length();i++){JSONObject f=features.getJSONObject(i);String iso=f.optString("id","");
    if(iso.length()!=3)continue;Country c=new Country(iso);JSONObject shape=f.getJSONObject("geometry");String type=shape.getString("type");JSONArray coords=shape.getJSONArray("coordinates");
    if(type.equals("Polygon"))addPoly(c,coords);
    else if(type.equals("MultiPolygon"))for(int j=0;j<coords.length();j++)addPoly(c,coords.getJSONArray(j));
    if(!c.parts.isEmpty())countries.add(c);
   }
  }
 }
 static double[] readRing(JSONArray a)throws JSONException{double[] result=new double[a.length()*2];for(int i=0;i<a.length();i++){JSONArray xy=a.getJSONArray(i);result[2*i]=xy.getDouble(0);result[2*i+1]=xy.getDouble(1);}return result;}
 static void addPoly(Country country,JSONArray rings)throws JSONException{
  if(rings.length()<1)return;Part p=new Part(readRing(rings.getJSONArray(0)));for(int i=1;i<rings.length();i++)p.holes.add(readRing(rings.getJSONArray(i)));country.parts.add(p);
 }
 String at(double lat,double lon){
  if(!GeoMath.valid(lat,lon))return "";
  for(Country c:countries)for(Part p:c.parts)if(p.contains(lat,lon))return c.iso;
  // Natural Earth 110m omits small Japanese islands; explicitly cover principal remote island groups.
  if(near(lat,lon,26.213,127.68,85)||near(lat,lon,24.805,125.28,35)||near(lat,lon,24.34,124.16,35)
   ||near(lat,lon,24.468,123.005,18)||near(lat,lon,28.37,129.50,40)||near(lat,lon,27.09,142.20,23)
   ||near(lat,lon,25.85,131.25,20))return "JPN";
  return "";
 }
 static boolean near(double lat,double lon,double a,double b,double km){return GeoMath.km(lat,lon,a,b)<=km;}
}