package net.makholm.henning.mapwarper.georaster;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Ellipsoid;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.TransformHelper;
import net.makholm.henning.mapwarper.util.ValWithPartials;

public final class WebMercator {

  public static String TAG = "web-mercator 2^" + Coords.BITS;

  public static long fromLatlon(double[] latlon) {
    return fromLatlon(latlon[0], latlon[1]);
  }

  public static long fromLatlon(double lat, double lon) {
    while( lon > 180 ) lon -= 360;
    while( lon < -180 ) lon += 360;
    double xfrac = (lon + 180) / 360.0;
    double latrad = lat * Coords.DEGREE;
    double ymerc = Math.log(Math.tan(latrad)+1/Math.cos(latrad));
    double yfrac = 0.5 - ymerc / (2 * Math.PI);
    if( yfrac < 0 ) yfrac = 0;
    if( yfrac > 1 ) yfrac = 1;
    int x = (int)(xfrac * Coords.EARTH_SIZE + 0.5);
    int y = (int)(yfrac * Coords.EARTH_SIZE + 0.5);
    return Coords.wrap(x,y);
  }

  public static double[] toLatlon(long wrapped) {
    return toLatlon(Point.at(wrapped));
  }

  public static double[] toLatlon(Point p) {
    var lat = new ValWithPartials();
    var lon = new ValWithPartials();
    toLatlon(p, lat, lon);
    return new double[] { lat.v, lon.v };
  }

  /**
   * Destructively update the two lon and lat objects.
   */
  public static void toLatlon(Point p,
      ValWithPartials lat, ValWithPartials lon) {
    lon.setRawX(p.x);
    lon.scale(360.0/Coords.EARTH_SIZE);
    lon.add(-180);

    lat.setRawY(p.y);
    lat.scale(-2.0*Math.PI/Coords.EARTH_SIZE);
    lat.add(Math.PI);
    // lat is now the y component of the canonical "mercator" scaling

    lat.sinh(null);
    lat.atan();
    lat.scale(180.0 / Math.PI);
  }

  /**
   * Transform the curve in global coordinates to a curve in a temporary
   * true (ellipsoidal) Mercator projection scaled such that there's one
   * unit per meter at the location of the curve.
   */
  public static Bezier mercatorize(Bezier curve, TransformHelper scratch) {
    if( scratch == null ) scratch = new TransformHelper();
    Point center = curve.p1.plus(0.5, curve.displacement);
    var x = new ValWithPartials();
    var y = new ValWithPartials();
    toLatlon(center, y, x);
    Ellipsoid.WGS84.mercatorize(x,y);
    var affine = ValWithPartials.toAffine(center, x, y);
    return curve.transform(affine, scratch);
  }

  /**
   * The Web-Mercator system is only approximately conformal; the horizontal
   * and vertical scales can vary by a considerable fraction of a percent.
   * This method gives a somewhere-in-the-middle estimate that can be used
   * for non-precision purposes.
   */
  public static double unitsPerMeter(double y) {
    double x = Coords.EARTH_SIZE/2;
    var p1 = Point.at(x-4, y-3);
    var p2 = Point.at(x+4, y+3);
    var tenUnitLine = Bezier.line(p1, p2);
    tenUnitLine = mercatorize(tenUnitLine, null);
    return 10/tenUnitLine.displacement.length();
  }

  public static String[] signedRoundedDegrees(int zoom, long wrapped) {
    return Coords.signedRoundedDegrees(zoom, toLatlon(wrapped));
  }

}
