package net.makholm.henning.mapwarper.geometry;

public class PointWithNormal extends Point {

  public final UnitVector normal;
  private final double eqnRhs;

  public PointWithNormal(Point p, UnitVector n) {
    super(p.x, p.y);
    this.normal = n;
    this.eqnRhs = eqnLhs(p);
  }

  public PointWithNormal(double x, double y, UnitVector n) {
    super(x, y);
    this.normal = n;
    this.eqnRhs = x*normal.y - y*normal.x;
  }

  public double eqnLhs(Vector a) {
    return a.x*normal.y - a.y*normal.x;
  }

  public double intersectWithNormal(LineSeg line) {
    if( (eqnLhs(line.a) > eqnRhs) == (eqnLhs(line.b) > eqnRhs) )
      return Double.NaN;
    else
      return line.eqnLhs(line.a.minus(this)) / line.eqnLhs(normal);
  }

  @Override
  public PointWithNormal reverse() {
    return new PointWithNormal(this, normal.reverse());
  }

  public Point pointOnNormal(double rightDist) {
    return this.plus(rightDist, normal);
  }

  public double signedDistanceFromNormal(Point p) {
    return (p.x-x) * normal.y - (p.y-y) * normal.x;
  }

}
