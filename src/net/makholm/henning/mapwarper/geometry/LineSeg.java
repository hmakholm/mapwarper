package net.makholm.henning.mapwarper.geometry;

public class LineSeg extends Vector {

  public final Point a, b;

  public LineSeg(Point a, Point b) {
    super(b.x-a.x, b.y-a.y);
    this.a = a;
    this.b = b;
  }

  public double eqnLhs(Vector p) {
    return p.x*y - p.y*x;
  }

  public boolean isPointToTheRight(Point p) {
    return eqnLhs(p) < eqnLhs(a);
  }

}
