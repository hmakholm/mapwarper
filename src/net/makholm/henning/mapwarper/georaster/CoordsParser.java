
package net.makholm.henning.mapwarper.georaster;

import net.makholm.henning.mapwarper.util.Regexer;

public class CoordsParser extends Regexer {

  private static final String degree = "\\s*[\u00B0\u00BA\u2070]";
  private static final String prime = "['\u2032]";
  private static final String dprime = "(?:[\"\u2033]|'')";
  private static final String dmsRe = "(?:"
      + reUnsigned + "|"
      + reUnsigned + degree + "|"
      + nat + degree + wsp + reUnsigned + prime + "|"
      + nat + degree + wsp + nat + prime + wsp + reUnsigned + dprime + ")";

  private static final String cDmsLat = "(" + dmsRe + wsp + "[NSns])";
  private static final String cDmsLon = "(" + dmsRe + wsp + "[EWew])";

  public CoordsParser(String baseString) {
    super(baseString);
  }

  public static double[] parseGeoreference(String s) {
    return new CoordsParser(s.trim()).parseGeoreference();
  }

  public double[] parseGeoreference() {
    // Simple pair of latitude and longitude, as copied by Google maps
    if( match(cSigned + optComma + cSigned) )
      return new double[] { dgroup(1), dgroup(2) };

    // Fancy human coordinates, possibly in degree-minutes-seconds format
    if( match(cDmsLat + ",?" + wsp + cDmsLon) )
      return new double[] { dms(group(1)), dms(group(2)) };

    if( match(cDmsLon + ",?" + wsp + cDmsLat) )
      return new double[] { dms(group(2)), dms(group(1)) };

    // Internal coordinates as printed by Coords.wprint
    if( match("(\\d+)::(\\d+)") )
      return WebMercator.toLatlon(Coords.wrap(igroup(1), igroup(2)));

    // Map center in an URL copied from window.location in Google Maps
    if( match("https://.*/@"+cSigned+","+cSigned+","+cSigned+"z/.*") )
      return new double[] { dgroup(1), dgroup(2), dgroup(3) };

    // Map center in an URL copied from window.location in OpenStreetMap
    if( match("https://.*#map=(\\d+)/"+cSigned+"/"+cSigned+"(&.*)?") )
      return new double[] { dgroup(2), dgroup(3), igroup(1) };

    // Map center of a file in the tilecache
    if( match(".*tilecache/[a-z]+/"+cNat+"/"+cNat+","+cNat+
        "/(\\d\\d),(\\d\\d)(\\.[a-z]+)?") ) {
      Tile t = Tile.at(igroup(1),
          igroup(2)*100 + igroup(4),
          igroup(3)*100 + igroup(5));
      System.err.println(t);
      var ll = WebMercator.toLatlon(t.midpoint());
      return new double[] { ll[0], ll[1], t.zoom };
    }

    return null;
  }

  /**
   * Assumes that s already matches the {@link #dmeRe} regex, possibly
   * with a final N, S, E, W.
   */
  private static double dms(String s) {
    Regexer re = new Regexer(s);

    if( !re.find("^" + cUnsigned) )
      throw new IllegalArgumentException("Strange DMS value ("+s+")");
    double val = re.dgroup(1);

    if( re.find("\\D" + cUnsigned + prime) )
      val += re.dgroup(1) / 60;

    if( re.find("\\D" + cUnsigned + dprime) )
      val += re.dgroup(1) / 3600;

    if( re.find("[WwSs]$") )
      return -val;
    else
      return val;
  }

}
