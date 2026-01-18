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
    double xfrac = (double)Coords.x(wrapped) / Coords.EARTH_SIZE;
    double yfrac = (double)Coords.y(wrapped) / Coords.EARTH_SIZE;
    double lon = xfrac * 360 - 180;
    double ymerc = (1 - 2*yfrac) * Math.PI;
    double latrad = Math.atan(Math.sinh(ymerc));
    double lat = latrad / Coords.DEGREE;
    return new double[] { lat, lon };
  }

  public static double unitsPerMeter(double y) {
    // This assumes a spherical earth. If we wanted to be precise
    // we should be using weird formulas for the WGS 84 ellipsoid,
    // but it's not used for anything particularly important...
    double ymerc = (1 - 2*y/Coords.EARTH_SIZE) * Math.PI;
    // cos(atan(sinh(y)) simplifies to 1/cosh(y)
    // so we're after value at equator _times_ cosh(y).
    return (Coords.EARTH_SIZE / 40_000_000.0) * Math.cosh(ymerc);
  }

  public static String[] signedRoundedDegrees(int zoom, long wrapped) {
    return Coords.signedRoundedDegrees(zoom, toLatlon(wrapped));
  }

  public static String showpos(int zoom, long wrapped) {
    return Coords.roundedDMS(zoom, toLatlon(wrapped));
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
