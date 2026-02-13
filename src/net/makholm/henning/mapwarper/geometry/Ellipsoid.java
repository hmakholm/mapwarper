package net.makholm.henning.mapwarper.geometry;

import net.makholm.henning.mapwarper.util.ValWithPartials;

public class Ellipsoid {

  public static final Ellipsoid WGS84 =
      new Ellipsoid(6_378_137, 298.257223563);

  public final double a;
  public final double b;
  public final double invF;

  public final double equatorLength;

  // Auxiliary ellipse shape constants
  public final double f; // flattening: (a-b)/a
  public final double n; // third (or second) flattening: (a-b)/(a+b)
  public final double e; // eccentricity

  public Ellipsoid(double a, double invF) {
    this.a = a;
    this.invF = invF;

    equatorLength = a * 2 * Math.PI;
    f = 1/invF;
    n = f/(2-f);
    e = Math.sqrt(f*(2-f));

    b = a - f*a;
  }

  /**
   * Transform (x=lon, y=lat) in degrees to coordinates in a Mercator
   * projection scaled such there's one meter per unit at the given
   * latitude.
   */
  public void mercatorize(ValWithPartials x, ValWithPartials y) {
    // First, get radians
    x.scale(Math.PI / 180);
    y.scale(Math.PI / 180);

    // Convert y to isometric latitude, remember cos(lat) too
    var tmp = new ValWithPartials();
    y.sin(tmp);
    var coslat = tmp.v;
    tmp.set(y);
    y.atanh();
    tmp.scale(e).atanh().scale(e);
    y.sub(tmp);

    // Compute the distance from the Earth's axis
    double g = 1/(1-f)-1;
    double h = Math.sqrt(1+(2*g+g*g)*coslat*coslat);
    double axisdist = a*coslat*(1+g)/h;

    x.scale(axisdist);
    y.scale(axisdist);
  }

}

