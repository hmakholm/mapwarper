package net.makholm.henning.mapwarper.geometry;

public class UnitVector extends Vector {

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
