package net.makholm.henning.mapwarper.geometry;

import net.makholm.henning.mapwarper.util.BadError;

public class UnitVector extends Vector {

  public static final UnitVector RIGHT = new UnitVector(1,0);
  public static final UnitVector DOWN = new UnitVector(0,1);

  private UnitVector(double x, double y) {
    super(x, y);
  }

  public static UnitVector of(Vector v) {
    double len = v.norm();
    return new UnitVector(v.x/len, v.y/len);
  }

  public static UnitVector along(double x, double y) {
    double sqlen = x*x + y*y;
    double factor;
    if( sqlen > 0.9999 && sqlen < 1.0001 ) {
      factor = (3 - sqlen)/2;
    } else {
      factor = 1/Math.sqrt(sqlen);
    }
    return new UnitVector(x * factor, y * factor);
  }

  public static UnitVector withBearing(double degrees) {
    int quarterturns = (int)Math.floor((degrees+45)/90);
    degrees -= 90*quarterturns;
    if( degrees == 0 )
      return fromTrig(1, 0, quarterturns);
    else if( degrees == -45 )
      return fromTrig(COS45, -COS45, quarterturns);
    else if( Math.abs(degrees) == 30 )
      return fromTrig(COS30, Math.copySign(0.5, degrees), quarterturns);
    else {
      double radians = degrees * (Math.PI/180);
      return fromTrig(Math.cos(radians), Math.sin(radians), quarterturns);
    }
  }

  // COS30 differs from Math.sqrt(0.75) by one unit in the last significant
  // bit, but is better in that it makes the length of the resulting vector
  // come out exactly one.
  private static final double COS30 = 8660254037844387e-16;
  private static final double COS45 = 7071067811865476e-16;

  private static UnitVector fromTrig(double cos, double sin, int quarterturns) {
    switch( quarterturns & 3 ) {
    case 0: return new UnitVector(sin, -cos);
    case 1: return new UnitVector(cos, sin);
    case 2: return new UnitVector(-sin, cos);
    case 3: return new UnitVector(-cos, -sin);
    default: throw BadError.of("Weird number of quarterturns");
    }
  }

  @Override
  public UnitVector normalize() {
    return this;
  }

  @Override
  public UnitVector turnRight() {
    // We use the "graphics" convention where x goes right, and y goes DOWN,
    // so turning right means the opposite of what it does in mathematics!
    return new UnitVector(-y, x);
  }

  @Override
  public UnitVector turnLeft() {
    return new UnitVector(y, -x);
  }

  @Override
  public UnitVector reverse() {
    return new UnitVector(-x, -y);
  }

  @Override
  public String toString() {
    return "dir("+bearingString()+"°)";
  }

}
