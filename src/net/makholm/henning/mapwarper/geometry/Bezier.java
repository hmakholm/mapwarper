package net.makholm.henning.mapwarper.geometry;

import java.awt.geom.AffineTransform;
import java.util.List;

import net.makholm.henning.mapwarper.util.Lazy;
import net.makholm.henning.mapwarper.util.LongHashed;
import net.makholm.henning.mapwarper.util.TreeList;

public final class Bezier extends LongHashed {

  public final Point p1, p2, p3, p4;
  public final LineSeg displacement;
  public final Vector v1, v4;
  public final Vector dv1, dv4;

  public final Lazy<AxisRect> bbox;

  /**
   * We treat dv1 = 3(p2-p1)-(p4-p1) and dv4=3(p4-p3)-(p4-p1)
   * as the authoritative truth about what the curve does between
   * the endpoints.
   *
   * This lets us recognize simpler curves exactly: the curve is
   * a line iff dv1=dv4=0, and it is a quadratic curve iff
   * dv1+dv4=0.
   */
  private Bezier(Point p1, Vector dv1, Vector dv4, Point p4) {
    this.p1 = p1;
    this.p4 = p4;
    this.dv1 = dv1;
    this.dv4 = dv4;
    displacement = p1.to(p4);
    v1 = displacement.plus(dv1);
    v4 = displacement.plus(dv4);
    p2 = p1.plus(1/3.0, v1);
    p3 = p4.plus(-1/3.0, v4);

    bbox = Lazy.of(() -> new AxisRect(
        new AxisRect(p1,p2),
        new AxisRect(p3,p4)));
  }

  private Bezier(Bezier toReverse) {
    p1 = toReverse.p4;
    p2 = toReverse.p3;
    p3 = toReverse.p2;
    p4 = toReverse.p1;
    displacement = p1.to(p4);
    v1 = toReverse.v4.reverse();
    v4 = toReverse.v1.reverse();
    dv1 = toReverse.dv4.reverse();
    dv4 = toReverse.dv1.reverse();
    bbox = toReverse.bbox;
  }

  public Bezier reverse() {
    return new Bezier(this);
  }
  public static Bezier cubic(Point p1, Point p2, Point p3, Point p4) {
    Vector md = p1.minus(p4);
    Vector dv1 = md.plus(3, p2.minus(p1));
    Vector dv4 = md.plus(3, p4.minus(p3));
    return new Bezier(p1, dv1, dv4, p4);
  }

  public static Bezier withVs(Point p1, Vector v1, Vector v4, Point p4) {
    Vector d = p1.to(p4);
    return new Bezier(p1, v1.minus(d), v4.minus(d), p4);
  }

  public static Bezier line(Point p1, Point p2) {
    return new Bezier(p1, Vector.ZERO, Vector.ZERO, p2);
  }

  public static Bezier through(Point q1, Point q2, Point q3, Point q4) {
    Vector err2 = q2.minus(q1.interpolate(1/3.0,q4));
    Vector err3 = q3.minus(q1.interpolate(2/3.0,q4));
    return new Bezier(
        q1, err2.scale(9).plus(-4.5,err3), err2.scale(4.5).plus(-9,err3), q4);
  }

  public Bezier transform(AffineTransform at, TransformHelper scratch) {
    return new Bezier(
        scratch.apply(at, p1),
        scratch.applyDelta(at, dv1),
        scratch.applyDelta(at, dv4),
        scratch.apply(at, p4));
  }

  public record SplitBezier(Bezier front, Bezier back) {}

  public SplitBezier split(double t) {
    Point pM = pointAt(t);
    if( isExactlyALine() )
      return new SplitBezier(line(p1, pM), line(pM,p4));
    Vector vM = derivativeAt(t);
    Vector minusDeltaFront = pM.to(p1);
    Vector minusDeltaBack = p4.to(pM);
    Bezier front = new Bezier(
        p1, minusDeltaFront.plus(t, v1),
        minusDeltaFront.plus(t, vM), pM);
    Bezier back = new Bezier(
        pM, minusDeltaBack.plus(1-t, vM),
        minusDeltaBack.plus(1-t, v4), p4);
    return new SplitBezier(front, back);
  }

  public UnitVector dir1() {
    return v1.normalize();
  }

  public UnitVector dir4() {
    return v4.normalize();
  }

  public Point pointAt(double t) {
    double u = 1-t;
    var x = p1.x + t*displacement.x + t*u*(u*dv1.x - t*dv4.x);
    var y = p1.y + t*displacement.y + t*u*(u*dv1.y - t*dv4.y);
    return Point.at(x, y);
  }

  public Vector derivativeAt(double t) {
    double a = 1 + t*(3*t-4);
    double b = t*(2-3*t);
    var x = displacement.x + a*dv1.x - b*dv4.x;
    var y = displacement.y + a*dv1.y - b*dv4.y;
    return Vector.of(x, y);
  }

  public Vector doubleDerivativeAt(double t) {
    double a = 6*t - 4;
    double b = 2 - 6*t;
    var x = a*dv1.x - b*dv4.x;
    var y = a*dv1.y - b*dv4.y;
    return Vector.of(x, y);
  }

  public double signedCurvatureAt(double t) {
    Vector d = derivativeAt(t);
    Vector dd = doubleDerivativeAt(t);
    double cross = d.x*dd.y - d.y*dd.x;
    double sqnd = d.sqnorm();
    return cross / (sqnd * Math.sqrt(sqnd));
  }

  public boolean isExactlyALine() {
    return dv1.x == 0 && dv1.y == 0 && dv4.x == 0 && dv4.y == 0;
  }

  public boolean isPracticallyALine() {
    return Math.abs(dv1.x) < 1 && Math.abs(dv1.y) < 1 &&
        Math.abs(dv4.x) < 1 && Math.abs(dv4.y) < 1;
  }

  public double upperBoundForDeviationFromStraight() {
    double sqdv1 = dv1.sqnorm();
    double sqdv4 = dv4.sqnorm();
    return Math.sqrt(Math.max(sqdv1,sqdv4)) / 4;
  }

  public double estimateLength() {
    return p1.dist(p4);
  }

  /**
   * The it is not a state-of-the-art general-purpose offsetter, but
   * it will work well enough for annotating track curves on the side.
   */
  public List<Bezier> offset(double rightDist) {
    return offset(rightDist,
        p1.plus(rightDist, dir1().turnRight()),
        p4.plus(rightDist, dir4().turnRight()));
  }

  private List<Bezier> offset(double rightDist, Point newP1, Point newP4) {
    double scaleBy = Math.sqrt(newP1.sqDist(newP4)/p1.sqDist(p4));
    var candidate = withVs(newP1, v1.scale(scaleBy), v4.scale(scaleBy), newP4);
    UnitVector midNormal = derivativeAt(0.5).normalize().turnRight();
    Point wantMid = pointAt(0.5).plus(rightDist, midNormal);
    Point gotMid = candidate.pointAt(0.5);
    if( Math.abs(wantMid.to(gotMid).dot(midNormal)) < 1 ||
        displacement.sqnorm() < 9 )
      return List.of(candidate);
    else {
      var split = split(0.5);
      return TreeList.concat(
          split.front().offset(rightDist, newP1, wantMid),
          split.back().offset(rightDist, wantMid, newP4));
    }
  }

  @Override
  protected long longHashImpl() {
    Long h = Double.doubleToRawLongBits(p1.x);
    h = hashStep(h);
    h ^= Double.doubleToRawLongBits(p1.y);
    h = hashStep(h);
    h ^= Double.doubleToRawLongBits(p4.x);
    h = hashStep(h);
    h ^= Double.doubleToRawLongBits(p4.y);
    h = hashStep(h);
    h ^= Double.doubleToRawLongBits(dv1.x);
    h = hashStep(h);
    h ^= Double.doubleToRawLongBits(dv1.y);
    h = hashStep(h);
    h ^= Double.doubleToRawLongBits(dv4.x);
    h = hashStep(h);
    h ^= Double.doubleToRawLongBits(dv4.y);
    return h;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (
        o instanceof Bezier other &&
        other.p1.is(p1) &&
        other.p4.is(p4) &&
        other.dv1.is(dv1) &&
        other.dv4.is(dv4));
  }

}
