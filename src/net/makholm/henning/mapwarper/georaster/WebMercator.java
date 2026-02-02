package net.makholm.henning.mapwarper.georaster;

import java.util.Locale;

import net.makholm.henning.mapwarper.geometry.Point;

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
    double xfrac = p.x / Coords.EARTH_SIZE;
    double yfrac = p.y / Coords.EARTH_SIZE;
    double lon = xfrac * 360 - 180;
    double ymerc = (1 - 2*yfrac) * Math.PI;
    double latrad = Math.atan(Math.sinh(ymerc));
    double lat = latrad / Coords.DEGREE;
    return new double[] { lat, lon };
  }

  private static final double WGS84_A = 6_378_137.0;
  private static final double WGS84_F = 1/298.257_223_563;

  private static final double WGS84_EQCIRC = WGS84_A * 2 * Math.PI;

  public static double unitsPerMeter(double y) {
    double ymerc = (1 - 2*y/Coords.EARTH_SIZE) * Math.PI;
    // The cosine of the latitude value
    // cos(atan(sinh(y)) simplifies to 1/cosh(y)
    double cos = 1/Math.cosh(ymerc);

    // Compute the (3d) radius of the latitude circle on the WGS84 ellipsoid
    // in units of the semimajor axis.
    double g = 1/(1-WGS84_F)-1;
    double h = Math.sqrt(1 + (2*g+g*g)*cos*cos);
    double rlatcirc = cos*(1+g)/h;
    // (Just approximating rlatcirc as cos*(1+f*(1-cos^2)) would give a
    // relative error less than F^2, but this is not on a hot path).

    // Anyway, scaling this factor to the appropriate coordinate system gives
    // the correct east-west scale. The web-Mercator projection is not
    // strictly conformal; the north-sourth scale is larger by a relative
    // factor of up to 2*WGS84_F, worst at the equator -- but the callers
    // of this method don't care for such subtleties anyway.
    return (Coords.EARTH_SIZE / WGS84_EQCIRC) / rlatcirc;
  }

  public static String[] signedRoundedDegrees(int zoom, long wrapped) {
    return Coords.signedRoundedDegrees(zoom, toLatlon(wrapped));
  }

  public static String showlength(double dist, Point refpoint) {
    dist /= unitsPerMeter(refpoint.y);
    double meters = dist;
    if( dist >= 4000 )
      return String.format(Locale.ROOT, "%.1f km", meters/1000);
    else if( dist >= 100 )
      return (int)meters + " m";
    else if( meters >= 10 )
      return String.format(Locale.ROOT, "%.1f m", meters);
    else if( meters >= 0.1 )
      return (int)(meters * 100) + " cm";
    else if( meters >= 0.01 )
      return (int)(meters * 1000) + " mm";
    else
      return String.format(Locale.ROOT, "%.3e mm", meters * 1000);
  }

}
