package tw.ajo.phototext;
public final class GeoMathTest {
 static void c(boolean ok,String s){if(!ok)throw new AssertionError(s);}
 public static void main(String[] args){
  double[] square={139,37,142,37,142,40,139,40};
  c(GeoMath.inside(38.268,140.869,square),"Sendai in box");
  c(!GeoMath.inside(35.68,139.69,square),"Tokyo outside");
  c(GeoMath.inside(37,140,square),"boundary");
  c(GeoMath.km(38.2682,140.8694,38.2554,140.3396)>40,"Sendai to Yamagata");
  c(GeoMath.km(38.2682,140.8694,38.2554,140.3396)<70,"Yamagata 100km included");
  c(GeoMath.km(38.2682,140.8694,35.6762,139.6503)>250,"Tokyo excluded");
  c(GeoMath.km(38.2682,140.8694,38.2682,140.8694)<0.01,"self");
  c(!GeoMath.valid(0,0)&&!GeoMath.valid(91,4)&&GeoMath.valid(24,121),"GPS validity");
  System.out.println("GeoMath: 8 cases passed");
 }
}