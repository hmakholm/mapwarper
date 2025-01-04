package net.makholm.henning.mapwarper.gui.projection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.gui.FindClosest;
import net.makholm.henning.mapwarper.gui.track.ChainRef;

final class WarpedProjectionWorker extends MinimalWarpWorker
implements ProjectionWorker {

  private final Projection owner;
  final double xscale, yscale;

  private final LinkedHashMap<GlobalPoint, Point> global2localCache =
      new LinkedHashMap<>();

  WarpedProjectionWorker(Projection owner, WarpedProjection warp,
      double xscale, double yscale) {
    super(warp, warp.curves);
    this.owner = owner;
    this.xscale = xscale;
    this.yscale = yscale;
  }

  /** This is used for the initial margin classification. */
  WarpedProjectionWorker(WarpedProjection warp, double xscale) {
    super(warp, i -> 0);
    this.owner = warp;
    this.xscale = xscale;
    this.yscale = 1;
  }

  @Override
  public Projection projection() {
    return owner;
  }

  @Override
  public PointWithNormal local2global(Point p) {
    setLefting(p.x * xscale);
    return pointWithNormal(projected2downing(p.y * yscale));
  }

  @Override
  public Point global2local(Point global) {
    GlobalPoint key = GlobalPoint.of(global);
    Point local = cacheLookup(key);
    if( local != null ) return local;

    var refref = FindClosest.point(warp.track.nodeTree.get(), ChainRef::data,
        Double.POSITIVE_INFINITY, global);
    var ref = warp.easyPoints.get(GlobalPoint.of(refref.data()));
    selectCurve(refref.index());

    local = locateWithLeftingRef(key, ref.lefting());
    global2localCache.put(key, local);
    return local;
  }

  @Override
  public Point global2localWithHint(Point global, Point nearbyLocal) {
    GlobalPoint key = GlobalPoint.of(global);
    Point local = cacheLookup(key);
    if( local != null ) return local;

    // Don't bother to store the result in the cache -- points where we
    // have a local reference are usually very ephemeral.
    return locateWithLeftingRef(key, nearbyLocal.x * xscale);
  }

  private Point cacheLookup(GlobalPoint global) {
    Point local = global2localCache.get(global);
    if( local != null ) return local;

    var easy = warp.easyPoints.get(global);
    if( easy == null ) return null;

    local = Point.at(easy.lefting() / xscale, easy.downing() / yscale);
    global2localCache.put(global, local);
    return local;
  }

  private Point locateWithLeftingRef(Point target, double lefting1) {
    // We only expect to get this close when following the errors on
    // a straight section of track. However, we'll grab the opportunity
    // whenever it presents ...
    double epsilon = Math.abs(yscale) * 0.01;
    PointWithNormal p0 = null, p1;
    double err0 = 0, err1;
    double lefting0 = lefting1;

    // First stage: move towards where the point appears to be, in
    // exponential increments if the error wasn't a reliable guide.
    for(;;) {
      p1 = normalAt(lefting1);
      err1 = p1.signedDistanceFromNormal(target);

      if( Math.abs(err1) < epsilon )
        return found(target, lefting1, p1);
      else if( err0 * err1 < 0 ) {
        // once we've found a sign difference, break out and start a
        // bisection phase.
        break;
      } else {
        double expmove = 2 * (lefting1 - lefting0);
        double errmove = err1;
        lefting0 = lefting1;
        p0 = p1;
        err0 = err1;

        lefting1 += Math.abs(expmove) > Math.abs(errmove) ?
            expmove : errmove;
      }
    }

    // bisection loop
    int count = 0;
    for(;;) {
      // interpolate between lefting0 and lefting1
      double frac = err1 / (err1-err0);

      if( Math.abs(lefting0 - lefting1) < xscale ||
          p0.normal == p1.normal ) {
        // Either the points are very close, or the normals are _so_
        // equal that they must have come from the same straight segment.
        double lefting = lefting0 + frac*(lefting1-lefting0);
        return found(target, lefting, normalAt(lefting));
      }

      // The trouble with an interpolating bisection is that we might end
      // up chopping off tiny pieces at the same end of the interval all
      // the time if the function doesn't cooperate. Thus:
      if( count++ % 2 == 0 ) {
        // Half of the time, the guess was close to an endpoint, move
        // it inwards so the next interval is at most 2/3 of the current,
        // no matter what.
        // So in the worst case it will take about 3½ guesses for each
        // bit of precision.
        if( frac < 1.0/3 ) frac = 1.0/3;
        else if( frac > 2.0/3 ) frac = 2.0/3;
      } else {
        // The other half of the time, adjust guesses near the endpoints
        // just enough that the original guess will be in the middle of the
        // next interval.
        frac += frac*(2*frac-1)*(frac-1);
      }

      double leftingM = lefting0 + frac*(lefting1-lefting0);
      PointWithNormal pM = normalAt(leftingM);
      double errM = pM.signedDistanceFromNormal(target);
      if( Math.abs(errM) < epsilon )
        return found(target, leftingM, pM);

      if( err0 * errM > 0 ) {
        lefting0 = leftingM;
        p0 = pM;
        err0 = errM;
      } else {
        lefting1 = leftingM;
        p1 = pM;
        err1 = errM;
      }
    }
  }

  private Point found(Point target, double lefting, PointWithNormal pwn) {
    double downing = target.minus(pwn).dot(pwn.normal) +
        slews.segmentSlew(segment);
    return Point.at(lefting / xscale, downing / yscale);
  }

  @Override
  public List<Bezier> global2local(Bezier global) {
    // For now, represent everything as straight lines.
    Point l1 = global2local(global.p1);
    Point l4 = global2local(global.p4);
    return Collections.singletonList(Bezier.line(l1, l4));
  }

}
