package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.gui.FindClosest;
import net.makholm.henning.mapwarper.gui.track.ChainRef;
import net.makholm.henning.mapwarper.util.TreeList;

final class WarpedProjectionWorker extends MinimalWarpWorker
implements ProjectionWorker {

  private final Projection owner;
  final double xscale, yscale;

  private final LinkedHashMap<GlobalPoint, LocalPoint> global2localCache =
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
  public LocalPoint global2local(Point global) {
    GlobalPoint key = GlobalPoint.of(global);
    LocalPoint local = cacheLookup(key);
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
  public LocalPoint global2localWithHint(Point global, Point nearbyLocal) {
    GlobalPoint key = GlobalPoint.of(global);
    LocalPoint local = cacheLookup(key);
    if( local != null ) return local;

    // Don't bother to store the result in the cache -- points where we
    // have a local reference are usually very ephemeral.
    return locateWithLeftingRef(key, nearbyLocal.x * xscale);
  }

  private LocalPoint cacheLookup(GlobalPoint global) {
    LocalPoint local = global2localCache.get(global);
    if( local != null ) return local;

    var easy = warp.easyPoints.get(global);
    if( easy == null ) return null;

    local = new LocalPoint(global, easy.segment(),
        easy.lefting(), easy.downing(), easy.tangent().turnRight());
    global2localCache.put(global, local);
    return local;
  }

  private LocalPoint locateWithLeftingRef(Point target, double lefting1) {
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

  private LocalPoint found(Point target, double lefting, PointWithNormal pwn) {
    return new LocalPoint(target, segment, lefting,
        target.minus(pwn).dot(pwn.normal) + slews.segmentSlew(segment),
        pwn.normal);
  }

  private class LocalPoint extends Point {
    final double lefting, downing;
    final int segment;
    final UnitVector normal;

    private LocalPoint(Point global, int segment,
        double lefting, double downing, UnitVector normal) {
      super(lefting / xscale, downing / yscale);
      this.lefting = lefting;
      this.downing = downing;
      this.segment = segment;
      this.normal = normal;
    }
  }

  @Override
  public List<Bezier> global2local(Bezier global) {
    LocalPoint l1 = global2local(global.p1);
    LocalPoint l4 = global2local(global.p4);
    if( warp.easyCurves.contains(global) ) {
      return List.of(Bezier.line(l1, l4));
    } else if( l1.segment == l4.segment ) {
      return global2localSameSegment(l1, global, l4, 0);
    } else {
      // For now fall back to representing everything as straight lines
      return Collections.singletonList(Bezier.line(l1, l4));
    }
  }

  /**
   * Convert a global curve/line to local coordinates, under the assumption
   * that it's entirely within the band warped by the segment named in
   * {@code l1}.
   *
   * This implementation is not particularly exact, but it's good enough
   * for the common use cases of margins roughly parallel to a segment
   * that doesn't curve too much.
   *
   * @param l1 the already converted point p1 of {@code global}.
   * @param l4 the already converted point p4 of {@code global}.
   */
  private List<Bezier> global2localSameSegment(
      LocalPoint l1, Bezier global, LocalPoint l4, int level) {
    if( l1.sqDist(l4) < 8 || level > 5)
      return List.of(Bezier.line(l1, l4));
    selectCurve(l1.segment);
    AffineTransform at1, at4;
    if( currentSegmentIsStraight() ) {
      if( global.isExactlyALine() )
        return List.of(Bezier.line(l1, l4));
      at1 = at4 = backwardsLinear(l1.normal.turnLeft().scale(xscale),
          l1.normal.scale(yscale));
    } else {
      at1 = backwardsLinear(l1);
      at4 = backwardsLinear(l4);
    }
    var v1 = applyDelta(at1, global.v1);
    var v4 = applyDelta(at4, global.v4);
    Bezier candidate = Bezier.withVs(l1, v1, v4, l4);
    var got = candidate.pointAt(0.5);
    var want = global2localWithHint(global.pointAt(0.5), got);
    if( want.sqDist(got) < 8 ) {
      // this looks good!
      return List.of(candidate);
    } else if( want.segment != l1.segment ) {
      // Huh? This sometimes happens when there are singularities around.
      return List.of(candidate);
    } else {
      var split = global.split(0.5);
      return TreeList.concat(
          global2localSameSegment(l1, split.front(), want, level+1),
          global2localSameSegment(want, split.back(), l4, level+1));
    }
  }

  private AffineTransform backwardsLinear(LocalPoint lp) {
    setLeftingWithCurrentSegment(lp.lefting);
    double curvature = curvatureAt(lp.lefting);
    double curvebase = warp.curves.segmentSlew(segment);
    double factor = 1-(lp.downing-curvebase)*curvature;
    if( factor < 0.01 ) factor = 0.01;
    return backwardsLinear(lp.normal.turnLeft().scale(xscale*factor),
        lp.normal.scale(yscale));
  }

  private AffineTransform backwardsLinear(Vector left, Vector down) {
    var at = new AffineTransform(left.x, left.y, down.x, down.y, 0, 0);
    try {
      at.invert();
    } catch (NoninvertibleTransformException e) {
      // Huh, this definitely shouldn't happen
      e.printStackTrace();
      return AffineTransform.getScaleInstance(xscale, yscale);
    }
    return at;
  }

}