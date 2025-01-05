package net.makholm.henning.mapwarper.gui;

import java.util.Collection;
import java.util.function.Function;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.util.MathUtil;
import net.makholm.henning.mapwarper.util.XyTree;

public final class FindClosest<D, L extends Collection<D>>
implements XyTree.Callback<L> {

  public static <D> D point(
      XyTree<D> tree,
      Function<? super D, Point> toPoint,
      double closerThan,
      Point focus) {
    class PointCallback implements XyTree.Callback<D> {
      D found;
      double sqDistLimit = closerThan * closerThan;
      @Override
      public boolean recurseInto(AxisRect rect) {
        return rect.sqDist(focus) < sqDistLimit;
      }
      @Override
      public void accept(D data) {
        double sd = focus.sqDist(toPoint.apply(data));
        if( sd < sqDistLimit ) {
          sqDistLimit = sd;
          found = data;
        }
      }
    }
    var callback = new PointCallback();
    XyTree.recurse(tree, focus, callback);
    return callback.found;
  }

  // ----------------------------------------------------------------------

  public static <D, L extends Collection<D>> D curve(
      XyTree<L> tree,
      Function<? super D, Bezier> toCurve,
      double closerThan,
      Point focus,
      double tolerance) {
    var callback = new FindClosest<D,L>(toCurve, closerThan, focus, tolerance);
    XyTree.recurse(tree, focus, callback);
    return callback.found;
  }

  private final Function<? super D, Bezier> toCurve;
  private final Point focus;
  private final double tolerance;

  private double sqDistLimit;
  private D found;

  private FindClosest(Function<? super D, Bezier> toCurve,
      double closerThan, Point focus, double tolerance) {
    this.tolerance = tolerance;
    this.toCurve = toCurve;
    this.focus = focus;
    this.sqDistLimit = closerThan * closerThan;
  }

  @Override
  public boolean recurseInto(AxisRect rect) {
    return rect.sqDist(focus) < sqDistLimit;
  }

  @Override
  public void accept(L list) {
    for( var data : list ) {
      Bezier curve = toCurve.apply(data);
      consider(data,
          curve.p1.sqDist(focus),
          curve,
          curve.p4.sqDist(focus));
    }
  }

  private void consider(D data, double sqdist1, Bezier curve, double sqdist4) {
    if( sqdist4 < sqdist1 ) {
      double tmp = sqdist1;
      sqdist1 = sqdist4;
      sqdist4 = tmp;
      curve = curve.reverse.get();
    }

    if( sqdist1 < sqDistLimit ) {
      sqDistLimit = sqdist1;
      found = data;
    } else {
      // Perhaps it far enough away that we won't bother anyway
      AxisRect bbox = curve.bbox.get();
      if( bbox.sqDist(focus) >= sqDistLimit )
        return;
    }

    Vector rel = focus.to(curve.p1);
    if( rel.dot(curve.displacement) >= 0 &&
        rel.dot(curve.v1) >= 0 &&
        rel.dot(curve.p3) - rel.dot(curve.p1) >= 0 ) {
      // everything is on the other side of p1.
      return;
    }

    AxisRect bbox = curve.bbox.get();
    if( bbox.width() < tolerance && bbox.height() < tolerance ) {
      // not worth bothering with such a tiny curve
      return;
    }

    // Let's pretend it's straight and see what the distance would be then.
    UnitVector dir = curve.displacement.normalize();
    double straightDist = Math.abs(dir.x * rel.y - rel.x * dir.y);

    double deviation = curve.upperBoundForDeviationFromStraight();

    if( deviation < tolerance ) {
      // Here we commit to viewing the curve as a line.
      // We know that at least one of the control points are
      // on this side of p1, where p1 it the _closest_ endpoint.
      // If that is p4, then surely the perpendicular line to the line falls
      // between p1 and p4. If it's p2 or p3, then everything must be so
      // close together that it doesn't really matter to pretend it is ...
      double sqdist = straightDist * straightDist;
      if( sqdist < sqDistLimit ) {
        sqDistLimit = sqdist;
        found = data;
      }
      return;
    }

    if( deviation < straightDist &&
        MathUtil.sqr(straightDist - deviation) >= sqDistLimit ) {
      // This is definitely hopeless
      return;
    }

    // Shoot, all the clever ideas failed. We'll have to bisect it and
    // try each half separately.
    // We'll recurse into the half with the closest endpoint first --
    // there's somewhat better chance that the other half can then be
    // quickly discarded afterwards.
    var split = curve.split(0.5);
    double sqdistM = split.front().p4.sqDist(focus);
    consider(data, sqdist1, split.front(), sqdistM);
    consider(data, sqdistM, split.back(), sqdist4);
  }

}
