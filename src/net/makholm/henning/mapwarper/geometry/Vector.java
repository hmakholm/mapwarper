package net.makholm.henning.mapwarper.geometry;

import java.util.Locale;

public class Vector {

  public static final Vector ZERO = new Vector(0,0);

  public final double x, y;

  protected Vector(double x, double y) {
    this.x = x;
    this.y = y;
  }

  public static Vector of(double x, double y) {
    return new Vector(x, y);
  }

  public final boolean is(Vector v) {
    return v == this || (x == v.x && y == v.y);
  }

  public final double dot(Vector v) {
    return x*v.x + y*v.y;
  }

  public final double sqnorm() {
    return x*x + y*y;
  }

  public final double norm() {
    return Math.sqrt(sqnorm());
  }

  public final double length() {
    return norm();
  }

  public UnitVector normalize() {
    return UnitVector.of(this);
  }

  public Vector interpolate(double t, Vector v) {
    return new Vector(x+t*(v.x-x), y+t*(v.y-y));
  }

  public final Vector scale(double factor) {
    return new Vector(x*factor, y*factor);
  }

  public final Vector divide(double factor) {
    return new Vector(x/factor, y/factor);
  }

  public Vector turnRight() {
    // We use the "graphics" convention where x goes right, and y goes DOWN,
    // so turning right means the opposite of what it does in mathematics!
    return new Vector(-y, x);
  }

  public Vector turnLeft() {
    return new Vector(y, -x);
  }

  public Vector reverse() {
    return new Vector(-x, -y);
  }

  public Vector plus(Vector v) {
    return new Vector(x+v.x, y+v.y);
  }

  public Vector plus(double f, Vector v) {
    return new Vector(x+f*v.x, y+f*v.y);
  }

  public Vector minus(Vector v) {
    return new Vector(x-v.x, y-v.y);
  }

  public final double bearing() {
    // atan2 usually takes y,x but we want north-based bearings here
    var radians = Math.atan2(x, -y);
    var degrees = radians * (180 / Math.PI);
    if( degrees < 0 ) degrees += 360;
    return degrees;
  }

  public final String bearingString() {
    if( x == 0 ) {
      return y == 0 ? "zero" : y < 0 ? "0" : "180";
    } else if( y == 0 ) {
      return x > 0 ? "90" : "270";
    } else if( x == -y ) {
      return x > 0 ? "45" : "225";
    } else if( x == y ) {
      return x > 0 ? "135" : "315";
    } else {
      return String.format(Locale.ROOT, "%.3f", bearing());
    }
  }

  @Override
  public String toString() {
    return String.format(Locale.ROOT, "(%.6g,%.6g)", x, y);
  }

}
