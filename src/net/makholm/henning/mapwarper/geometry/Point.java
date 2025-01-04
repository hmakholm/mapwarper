package net.makholm.henning.mapwarper.geometry;

import java.util.Locale;

import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.util.MathUtil;

public class Point extends Vector {

  public static final Point ORIGIN = new Point(0,0);

  protected Point(double x, double y) {
    super(x,y);
  }

  public static Point at(double x, double y) {
    return new Point(x, y);
  }

  public static Point at(long wrapped) {
    return new Point(Coords.x(wrapped), Coords.y(wrapped));
  }

  public boolean is(Point p) {
    return x == p.x && y == p.y;
  }

  //--------------------------------------

  public LineSeg to(Point b) {
    return new LineSeg(this, b);
  }

  public LineSeg minus(Point a) {
    return new LineSeg(a, this);
  }

  @Override
  public Point plus(Vector v) {
    return new Point(x+v.x, y+v.y);
  }

  @Override
  public Point plus(double factor, Vector v) {
    if( factor == 0 ) return this;
    return new Point(x+factor*v.x, y+factor*v.y);
  }

  @Override
  public Point minus(Vector v) {
    return new Point(x-v.x, y-v.y);
  }

  public Point interpolate(double t, Point p) {
    return new Point(x+t*(p.x-x), y+t*(p.y-y));
  }

  public double sqDist(Point p) {
    return MathUtil.sqr(p.x - x) + MathUtil.sqr(p.y - y);
  }

  public double dist(Point p) {
    return to(p).length();
  }

  @Override
  public String toString() {
    return String.format(Locale.ROOT, "%.1f::%.1f", x, y);
  }

}
