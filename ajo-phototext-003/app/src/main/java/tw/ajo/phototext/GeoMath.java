package tw.ajo.phototext;
final class GeoMath {
 static double km(double a,double b,double c,double d){
  double y=Math.toRadians(c-a),x=Math.toRadians(d-b),v=Math.sin(y/2)*Math.sin(y/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(x/2)*Math.sin(x/2);
  return 12742.0*Math.atan2(Math.sqrt(Math.max(0,v)),Math.sqrt(Math.max(0,1-v)));
 }
 // ring has [longitude, latitude] pairs; boundary inclusion prevents accidental coast exclusion.
 static boolean inside(double lat,double lon,double[] xy){
  boolean hit=false;int n=xy.length/2;for(int i=0,j=n-1;i<n;j=i++){
   double xi=xy[i*2],yi=xy[i*2+1],xj=xy[j*2],yj=xy[j*2+1];
   double cross=(lon-xj)*(yi-yj)-(lat-yj)*(xi-xj);
   if(Math.abs(cross)<1e-10&&lon>=Math.min(xi,xj)-1e-9&&lon<=Math.max(xi,xj)+1e-9&&lat>=Math.min(yi,yj)-1e-9&&lat<=Math.max(yi,yj)+1e-9)return true;
   if((yi>lat)!=(yj>lat)&&lon<(xj-xi)*(lat-yi)/(yj-yi)+xi)hit=!hit;
  }return hit;
 }
 static boolean valid(double lat,double lon){return Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180&&!(lat==0&&lon==0);}
}