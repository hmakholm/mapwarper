package net.makholm.henning.mapwarper.georaster;

import java.util.Locale;

import net.makholm.henning.mapwarper.geometry.Point;

/**
 * Fixed-point representation of a global coordinate system according to
 * https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
 *
 * The entire nearly-global pseudo-Mercator square is divided into
 * 2^30 units on each side, which lets us represent pixel boundaries
 * at zoom level 22 as integers.
 *
 * Coordinate points then have a real spacing of 37 mm at the equator
 * and 21 mm at in Copenhagen (56° N); the width of a standard-gauge
 * track in Houston (29 °N) is 44 coordinate units, increasing to 69
 * units in Copenhagen. This gives plenty of precision to define
 * track positions, so in particular the external storage format
 * uses integer coordinates.
 *
 * At the same time, the largest aerophoto zoom scale that seems to be
 * useful for track layouts is z20: with sufficiently sharp imagery,
 * this scale even clearly distinguishes between double and single slip
 * switches. (In most practical cases, useful warped maps can be
 * made from z18 tiles). So integer coordinates are also enough
 * for locating map pixels.
 *
 * Intermediate calculation for <em>warping</em> need greater precision,
 * but with 52 significant bits, {@code double} coordinates have sufficient
 * precision that we can use global coordinates even with floating point,
 * without needing to translate to a local coordinate system.
 */
public final class Coords {

  public static final int BITS = 30;

  public static final int EARTH_SIZE = 1 << BITS;

  public static final double DEGREE = Math.PI / 180.0;

  public static int zoom2logPixsize(int zoom) {
    // UI-oriented zoom values assume tiles are 256 pixels
    return BITS - 8 - zoom;
  }

  public static int zoom2pixsize(int zoom) {
    return 1 << zoom2logPixsize(zoom);
  }

  public static int logPixsize2zoom(int logPixsize) {
    return BITS - 8 - logPixsize;
  }

  // -------------------------------------------------------

  // Helper methods for wrapping and unwrapping coordinate pairs.
  // These are convenience methods, not encapsulation boundaries.

  public static final long ONE_ONE = 1L + (1L << 32);

  public static long wrap(int x, int y) {
    return ((long)x << 32) + (y & 0xFFFFFFFFL);
  }

  public static long point2pixcoord(Point p) {
    return point2pixcoord(p.x, p.y);
  }

  public static long point2pixcoord(double x, double y) {
    // This is in the map renderer hot loop, so performance matters.

    // For the most principled behavior we should use floors, but that only
    // differs from the standard long-to-double conversion for negative
    // coordinates, which are not even meaningful in the global coordinate
    // system.

    // So we actually use the default round-towards-zero conversion and also
    // forgo masking -- this just means that the phantom copies of the Earth
    // to the north and west of the main coordinates will be shifted by a
    // small distance (a few dozen millimeters) especially in the x direction
    // that the sign bits of the y coordinates bleed into. But nobody really
    // cares about that anyway.

    // Sufficiently new processors (which support embedded rounding control
    // in FP operations; search for "EVEX prefix") should actually be able
    // to do a true double-to-long _floor_ in a single instruction, but I
    // don't think HotSpot uses that possibility.
    return ((long)x << 32) + (long)y;
  }


  public static int x(long wrapped) {
    return (int)(wrapped >> 32);
  }

  public static int y(long wrapped) {
    return (int)wrapped;
  }

  public static String wprint(long wrapped) {
    return x(wrapped) + "::" + y(wrapped);
  }

  public static long midpoint(long a, long b) {
    return ((a+b)>>>1) & 0x7FFF_FFFF_7FFF_FFFFL;
  }

  // --------------------------------------------------

  public static String[] signedRoundedDegrees(int zoom, double[] latlon) {
    double lat = latlon[0];
    double lon = latlon[1];
    double lonPerPixel = 180 * Math.pow(2, -8-zoom);
    double latPerPixel = lonPerPixel * Math.cos(lat * DEGREE);
    return new String[] {
        Coords.decimalWithPrecision(lat, latPerPixel),
        Coords.decimalWithPrecision(lon, lonPerPixel) };
  }

  public static String roundedDMS(int zoom, double[] latlon) {
    double lat = latlon[0];
    double lon = latlon[1];
    double lonPerPixel = 180 * Math.pow(2, -8-zoom);
    double latPerPixel = lonPerPixel * Math.cos(lat * DEGREE);
    return
        Coords.dmsWithPrecision(Math.abs(lat), latPerPixel) +
        (lat >= 0 ? "N " : "S ") +
        Coords.dmsWithPrecision(Math.abs(lon), lonPerPixel) +
        (lon >= 0 ? "E" : "W");
  }

  public static String decimalWithPrecision(double value, double prec) {
    if( prec >= 1.0 ) {
      return Integer.toString((int)Math.round(value));
    } else {
      int decimals = (int)Math.ceil(-Math.log10(prec));
      return format("%."+decimals+"f", value);
    }
  }

  private static final String DEGSIGN = "\u00B0";
  private static final String MINSIGN = "\u2032";
  private static final String SECSIGN = "\u2033";

  public static String dmsWithPrecision(double degrees, double prec) {
    if( degrees < 0 ) {
      return "-" + dmsWithPrecision(-degrees, prec);
    }

    if( prec >= 1.0 ) {
      return (int)Math.round(degrees) + DEGSIGN;
    } else {
      int minutes;
      String secondsstring;
      if( prec*60 >= 1.0 ) {
        minutes = dmsRounding(degrees*60, prec*60);
        secondsstring = "";
      } else if( prec*3600 >= 1.0 ) {
        var seconds = dmsRounding(degrees*3600, prec*3600);
        minutes = seconds / 60;
        secondsstring = format("%02d" + SECSIGN, seconds % 60);
      } else {
        var seconds = degrees * 3600;
        minutes = (int)Math.floor(seconds) / 60;
        seconds -= minutes * 60;
        secondsstring = decimalWithPrecision(seconds, prec*3600);
        if( secondsstring.startsWith("60") ) {
          minutes++ ;
          secondsstring = decimalWithPrecision(0, prec*3600);
        }
        if( secondsstring.charAt(1) == '.' )
          secondsstring = "0" + secondsstring;
        secondsstring += SECSIGN;
      }
      return format("%d"+DEGSIGN+"%02d"+MINSIGN+"%s",
          minutes/60, minutes%60, secondsstring);
    }
  }

  private static int dmsRounding(double value, double precision) {
    int iprecision ;
    if( precision >= 15 ) {
      iprecision = 15;
    } else if( precision >= 5 ) {
      iprecision = 5;
    } else {
      iprecision = 1;
    }
    return iprecision * (int)Math.round(value / iprecision);
  }

  private static String format(String format, Object...params) {
    return String.format(Locale.ROOT, format, params);
  }

}
