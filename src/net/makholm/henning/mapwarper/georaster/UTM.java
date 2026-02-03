package net.makholm.henning.mapwarper.georaster;

import java.awt.geom.AffineTransform;
import java.util.Locale;
import java.util.Random;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.util.ValWithPartials;

/**
 * This class can translate single points from global Web-Mercator
 * coordinates to UTM coordinates in meters, including first derivatives
 * of the transformation.
 *
 * The class is thread-safe once constructed.
 *
 * Beware that each translation is somewhat expensive, so it may be
 * advantageous to use the derivative information to handle points
 * close to a previous transformation.
 */
public class UTM {

  public final double centralMeridian;
  public final double falseNorthing;
  public final double falseEasting;
  public final double scaleFactor;
  public final double equatorRadius;
  public final double invFlattening;

  public static final UTM WGS84(int zone, boolean north) {
    return new UTM((zone-30)*6 - 3,
        (north ? 0 : 10_000_000), 500_000,
        0.9996,
        6_378_137, 298.257223563);
  }

  public UTM withEllipsoid(double equatorRadius, double invFlattening) {
    return new UTM(centralMeridian, falseNorthing, falseEasting, scaleFactor,
        equatorRadius, invFlattening);
  }

  public UTM withScaleFactor(double scaleFactor) {
    return new UTM(centralMeridian, falseNorthing, falseEasting, scaleFactor,
        equatorRadius, invFlattening);
  }

  private UTM(double centralMeridian, double falseNorthing, double falseEasting,
      double scaleFactor, double equatorRadius, double invFlattening) {
    this.centralMeridian = centralMeridian;
    this.falseNorthing = falseNorthing;
    this.falseEasting = falseEasting;
    this.scaleFactor = scaleFactor;
    this.equatorRadius = equatorRadius;
    this.invFlattening = invFlattening;

    // https://en.wikipedia.org/wiki/Universal_Transverse_Mercator_coordinate_system
    // says these order-3 coefficients "are accurate to around a millimeter
    // within 3000 km of the central meridian".
    var f = 1/invFlattening; // flattening = (a-b)/a
    var n = f/(2-f);  // third (or second) flattening = (a-b)/(a+b)
    var n2 = n*n;
    var n3 = n2*n;
    var n4 = n2*n2;
    // arc length from equator to pole
    // given as a/(1+n)*(1+n^2/4+n^4/64) in Wikipedia
    var A = equatorRadius*(1+n2/4+n4/64)*(2-f)/2;

    e = Math.sqrt(f*(2-f)); // eccentricity, as 2sqrt(n)/(1+n) in Wikipedia
    k0A = scaleFactor * A;
    alpha = new double[] {
        n/2 - n2*2/3 + n3*5/16,
        n2*13/48 - n3*3/5,
        n3*61/240 };
  }

  private final double e, k0A;
  private final double[] alpha;

  public void toUTM(Point global, ValWithPartials E, ValWithPartials N) {
    var lambda = new ValWithPartials();
    var phi = new ValWithPartials();
    WebMercator.toLatlon(global, phi, lambda);

    lambda.add(-centralMeridian);
    lambda.scale(Math.PI/180);
    phi.scale(Math.PI/180);

    phi.sin(null);
    var tmp = phi.clone().scale(e).atanh().scale(e);
    var tt = new ValWithPartials();
    var t = phi.atanh().sub(tmp).sinh(tt);
    phi = null;

    var cosl = new ValWithPartials();
    var sinl = lambda.sin(cosl);
    lambda = null;

    var xi = t.divide(cosl).atan();
    var eta = sinl.divide(tt).atanh();
    t = tt = sinl = cosl = null;

    E.set(eta);
    N.set(xi);

    // The complex Fourier series loop here could be made cheaper by
    // using the angle doubling/addition formulas instead of evaluating
    // the transcendental functions over and over, but we're already
    // optimizing by doing only one transformation per tile and fallback
    // layer, so that's probably not worth the trouble.
    var cos = new ValWithPartials();
    var sin = new ValWithPartials();
    var cosh = new ValWithPartials();
    var sinh = new ValWithPartials();
    for( int j=0; j<alpha.length; j++ ) {
      int afac = 2*(j+1);
      sin = sin.set(xi).scale(afac).sin(cos);
      sinh = sinh.set(eta).scale(afac).sinh(cosh);
      E.add(cos.multiply(sinh).scale(alpha[j]));
      N.add(sin.multiply(cosh).scale(alpha[j]));
    }

    E.scale(k0A).add(falseEasting);
    N.scale(k0A).add(falseNorthing);
  }

  public AffineTransform toUTM(Point p) {
    var E = new ValWithPartials();
    var N = new ValWithPartials();
    toUTM(p, E, N);
    return ValWithPartials.toAffine(p, E, N);
  }

  public static void main(String[] s) {
    var utm = WGS84(32,true);
    var E = new ValWithPartials();
    var N = new ValWithPartials();
    var EE = new ValWithPartials();
    var NN = new ValWithPartials();
    Point p = Point.at(574135079, 336388481); // should be 720,6169
    if( true ) {
      var r = new Random();
      p = Point.at(560922560 + r.nextInt(575166809-560922560),
          324748672 + r.nextInt(341940864-324748672));
    }
    System.out.println(Coords.roundedDMS(18, WebMercator.toLatlon(p)));
    utm.toUTM(p,E,N);
    Point putm = Point.at(E.v, N.v);
    System.out.println(E.v+" "+N.v);

    var pixel = 0.125*16;
    var rMeter = 4000*pixel;
    var rGlobal = rMeter * WebMercator.unitsPerMeter(p.y);
    for( int deg=0; deg<360; deg += 15 ) {
      var delta = UnitVector.withBearing(deg).scale(rGlobal);
      var aff = Point.at(E.apply(delta), N.apply(delta));
      utm.toUTM(p.plus(delta), EE, NN);
      var exact = Point.at(EE.v, NN.v);
      System.out.printf(Locale.ROOT,
          " %3d deg aff  %s, delta %s, length %.1f error %s\n", deg,
          aff, putm.to(aff), putm.to(aff).length(), exact.to(aff));
      System.out.printf(Locale.ROOT,
          "         true %s, delta %s, length %.1f relative 1/%.0f\n",
          exact, putm.to(exact), putm.to(exact).length(),
          rMeter/exact.to(aff).length());
    }
  }

}
