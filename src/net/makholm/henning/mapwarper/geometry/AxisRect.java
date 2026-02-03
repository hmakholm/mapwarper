package net.makholm.henning.mapwarper.geometry;

import java.awt.geom.Rectangle2D;
import java.util.Locale;
import java.util.function.Function;

import net.makholm.henning.mapwarper.util.MathUtil;
import net.makholm.henning.mapwarper.util.MutableLongRect;

public class AxisRect {

  private final double xmin, ymin, xmax, ymax;
  private final Point center;

  public final double xmin() { return xmin; }
  public final double xmax() { return xmax; }
  public final double ymin() { return ymin; }
  public final double ymax() { return ymax; }

  private AxisRect(double xmin, double ymin, double xmax, double ymax) {
    this.xmin = xmin; this.ymin = ymin;
    this.xmax = xmax; this.ymax = ymax;
    center = Point.at(MathUtil.avg(xmin,xmax), MathUtil.avg(ymin, ymax));
  }

  public AxisRect(Point p, Point q) {
    this(
        Math.min(p.x, q.x), Math.min(p.y, q.y),
        Math.max(p.x, q.x), Math.max(p.y, q.y));
  }

  public static AxisRect extend(AxisRect r, Point p) {
    if( r == null ) return p == null ? null : new AxisRect(p);
    if( p == null || r.contains(p) ) return r;
    return new AxisRect(r, new AxisRect(p));
  }

  public AxisRect(Point p) {
    xmin = xmax = p.x;
    ymin = ymax = p.y;
    center = p;
  }

  public AxisRect(AxisRect a, AxisRect b) {
    this(
        Math.min(a.xmin, b.xmin), Math.min(a.ymin, b.ymin),
        Math.max(a.xmax, b.xmax), Math.max(a.ymax, b.ymax));
  }

  public AxisRect(AxisRect orig) {
    xmin = orig.xmin;
    xmax = orig.xmax;
    ymin = orig.ymin;
    ymax = orig.ymax;
    center = orig.center;
  }

  public AxisRect(MutableLongRect r) {
    this(r.left, r.top, r.right, r.bottom);
  }

  public AxisRect(Rectangle2D r) {
    this(r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY());
  }

  public AxisRect translate(Vector v) {
    return new AxisRect(xmin + v.x, ymin + v.y, xmax + v.x, ymax + v.y);
  }

  public AxisRect transform(Function<Point, Point> cornerFunc) {
    return new AxisRect(
        cornerFunc.apply(nwCorner()),
        cornerFunc.apply(seCorner()));
  }

  public AxisRect grow(double extra) {
    return new AxisRect(xmin-extra, ymin-extra, xmax+extra, ymax+extra);
  }

  public Point center() {
    return center;
  }

  public Point nwCorner() {
    return Point.at(xmin,ymin);
  }

  public Point seCorner() {
    return Point.at(xmax, ymax);
  }

  public double width() {
    return xmax-xmin;
  }

  public double height() {
    return ymax-ymin;
  }

  public double sqDist(Point p) {
    double result = 0;
    if( p.x < xmin )result += MathUtil.sqr(xmin - p.x);
    else if( p.x > xmax ) result += MathUtil.sqr(p.x - xmax);
    if( p.y < ymin ) result += MathUtil.sqr(ymin - p.y);
    else if( p.y > ymax ) result += MathUtil.sqr(p.y - ymax);
    return result;
  }

  public boolean contains(Point p) {
    return contains(p.x, p.y);
  }

  public boolean contains(double x, double y) {
    return x >= xmin && x <= xmax && y >= ymin && y <= ymax;
  }

  public Point clamp(Point p) {
    if( contains(p) )
      return p;
    else
      return Point.at(MathUtil.clamp(xmin, p.x, xmax),
          MathUtil.clamp(ymin, p.y, ymax));
  }

  public boolean contains(AxisRect other) {
    return
        other.xmin >= xmin &&
        other.xmax <= xmax &&
        other.ymin >= ymin &&
        other.ymax <= ymax;
  }

  public boolean intersects(AxisRect other) {
    return
        other.xmin <= xmax &&
        other.xmax >= xmin &&
        other.ymin <= ymax &&
        other.ymax >= ymin;
  }

  public AxisRect intersect(AxisRect other) {
    if( other.contains(this) )
      return this;
    else if( intersects(other) )
      return new AxisRect(
          Math.max(xmin, other.xmin),
          Math.max(ymin, other.ymin),
          Math.min(xmax, other.xmax),
          Math.min(ymax, other.ymax));
    else
      return null;
  }

  public AxisRect union(AxisRect other) {
    if( other == null || contains(other) )
      return this;
    else if( other.contains(this) )
      return other;
    else
      return new AxisRect(this, other);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof AxisRect other &&
        other.xmin == xmin &&
        other.xmax == xmax &&
        other.ymin == ymin &&
        other.ymax == ymax;
  }

  @Override
  public String toString() {
    return String.format(Locale.ROOT, "[%.2f,%.2f]x[%.2f,%.2f]",
        xmin, xmax, ymin, ymax);
  }

}
