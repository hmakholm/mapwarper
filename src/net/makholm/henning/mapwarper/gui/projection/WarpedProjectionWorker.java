package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.gui.FindClosest;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.util.ListMapper;
import net.makholm.henning.mapwarper.util.RootFinder;
import net.makholm.henning.mapwarper.util.TreeList;

final class WarpedProjectionWorker extends MinimalWarpWorker
implements ProjectionWorker {

  private final Projection owner;
  final double xscale, yscale;

  private final LinkedHashMap<GlobalPoint, LocalPoint> global2localCache =
      new LinkedHashMap<>();

  WarpedProjectionWorker(Projection owner, WarpedProjection warp,
      double xscale, double yscale) {
    super(warp);
    this.owner = owner;
    this.xscale = xscale;
    this.yscale = yscale;
  }

  /** This is used for the initial margin classification. */
  WarpedProjectionWorker(WarpedProjection warp) {
    super(warp);
    this.owner = warp;
    this.xscale = 1;
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
    if( nearbyLocal instanceof LocalPoint lp ) {
      selectCurve(lp.segment);
      return locateWithLeftingRef(key, lp.lefting);
    } else {
      return locateWithLeftingRef(key, nearbyLocal.x * xscale);
    }
  }

  private LocalPoint cacheLookup(GlobalPoint global) {
    LocalPoint local = global2localCache.get(global);
    if( local != null ) return local;

    var easy = warp.easyPoints.get(global);
    if( easy == null ) return null;

    local = new LocalPoint(easy.segment(), easy.lefting(), easy.downing(),
        easy.tangent().turnRight());
    global2localCache.put(global, local);
    return local;
  }

  private int searches, probes, maxprobes;

  @Override
  public void dumpSearchStats() {
    // Disable the stats output by making the condition impossible
    if( searches < 0 ) {
      System.err.printf(Locale.ROOT,
          " %d searches used %d probes, avg %.2f, max %d\n",
          searches, probes, (double)probes/searches, maxprobes);
      searches = probes = maxprobes = 0;
    }
  }

  private LocalPoint locateWithLeftingRef(Point target, double lefting1) {
    // We only expect to get this close when following the errors on
    // a straight section of track. However, we'll grab the opportunity
    // whenever it presents ...
    double epsilon = yscale * 0.01;

    double foundLefting = new RootFinder(xscale) {
      int count;
      @Override
      protected double f(double lefting) {
        count++;
        PointWithNormal p = normalAt(lefting);
        double ndist = p.signedDistanceFromNormal(target);
        if( Math.abs(ndist) < epsilon )
          return 0;
        else
          return -ndist;
      }
      @SuppressWarnings("unused")
      @Override
      protected double derivative(double lefting) {
        // Experimentally, providing a precise derivative doesn't even reduce
        // the (already quite small) average number of probes per conversion
        // by even one. So it's not worth the extra work ...
        if( true ) return Double.NaN;
        PointWithNormal p = normalAt(lefting);
        double downing = p.to(target).dot(p.normal);
        double curvature = curvatureAt(lefting);
        return 1-downing*curvature;
      }
      double run(double a) {
        var result = rootNear(a);
        searches++;
        probes += count;
        maxprobes = Math.max(maxprobes, count);
        return result;
      }
    }.run(lefting1);

    PointWithNormal pwn = normalAt(foundLefting);
    return new LocalPoint(segment, foundLefting,
        target.minus(pwn).dot(pwn.normal) + curves.segmentSlew(segment),
        pwn.normal);
  }

  class LocalPoint extends Point {
    final double lefting, downing;
    final int segment;
    final UnitVector normal;

    private LocalPoint(int segment,
        double lefting, double downing, UnitVector normal) {
      super(lefting / xscale, downing / yscale);
      this.lefting = lefting;
      this.downing = downing;
      this.segment = segment;
      this.normal = normal;
    }

    public boolean rightOfTrack() {
      return downing > curves.segmentSlew(segment);
    }
  }

  private LocalPoint local2local(Point local) {
    var lefting = local.x * xscale;
    setLefting(lefting);
    return new LocalPoint(segment, lefting, local.y*yscale, currentNormal());
  }

  @Override
  public List<Bezier> global2local(Bezier global) {
    LocalPoint l1 = global2local(global.p1);
    LocalPoint l4 = global2local(global.p4);
    if( warp.easyCurves.contains(global) ) {
      return List.of(Bezier.line(l1, l4));
    } else if( l1.segment == l4.segment ) {
      return global2localOneSegment(l1.segment, l1, global, l4, 0);
    } else if( l4.segment == l1.segment+1 &&
        l4.lefting == warp.nodeLeftings[l4.segment] ) {
      // Another important easy case is when a segment of the existing
      // warp gets new _tangents_ due to nearby changes, but keeps its
      // _endpoints_.
      return global2localOneSegment(l1.segment, l1, global, l4, 0);
    } else if( l1.segment < l4.segment ) {
      return global2local(l1.segment, l1, global, l4, l4.segment);
    } else {
      var revglobal = global.reverse();
      var revlocal = global2local(l4.segment, l4, revglobal, l1, l1.segment);
      return ListMapper.reverse(ListMapper.map(revlocal, Bezier::reverse));
    }
  }

  private List<Bezier> global2local(int seg1, LocalPoint l1, Bezier global,
      LocalPoint l4, int seg4) {
    if( Math.abs(l4.x - l1.x) < 3 )
      return List.of(Bezier.line(l1, l4));
    while( seg1 < warp.nodeLeftings.length-1 &&
        warp.nodeLeftings[seg1+1] < l1.lefting+xscale ) seg1++;
    while( seg4 >= 0 && warp.nodeLeftings[seg4] > l4.lefting-xscale ) seg4--;
    if( seg1 >= seg4 )
      return global2localOneSegment(seg1, l1, global, l4, 0);

    // Divide the curve along a node line in the middle
    int node = (seg1+seg4+1)/2;
    PointWithNormal divider = warp.nodesWithNormals[node];
    double xx1 = divider.signedDistanceFromNormal(global.p1);
    double xx4 = divider.signedDistanceFromNormal(global.p4);
    double t;
    if( xx1 < 0 && xx4 > 0 ) {
      t = new RootFinder(0.01) {
        @Override
        protected double f(double tt) {
          double xx = divider.signedDistanceFromNormal(global.pointAt(tt));
          return Math.abs(xx) < xscale/4 ? 0 : xx;
        }
      }.rootBetween(0, xx1, 1, xx4);
    } else {
      // TODO these other cases are weird. Expected to happen only for
      // segments far from the warped track, not worth bothering with?
      return List.of(Bezier.line(l1, l4));
    }
    var split = global.split(t);
    Point gMid = split.front().p4;
    LocalPoint lMid = new LocalPoint(node, warp.nodeLeftings[node],
        divider.to(gMid).dot(divider.normal) + curves.nodeSlew(node),
        divider.normal);
    return TreeList.concat(
        global2local(seg1, l1, split.front(), lMid, node-1),
        global2local(node, lMid, split.back(), l4, seg4));
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
  private List<Bezier> global2localOneSegment(
      int seg, LocalPoint l1, Bezier global, LocalPoint l4, int level) {
    if( l1.sqDist(l4) < 8 || level > 5)
      return List.of(Bezier.line(l1, l4));
    selectCurve(seg);
    Vector v1, v4;
    if( currentSegmentIsStraight() ) {
      if( global.isExactlyALine() )
        return List.of(Bezier.line(l1, l4));
      v1 = delta2local(global.v1, l1.normal, xscale);
      v4 = delta2local(global.v4, l4.normal, xscale);
    } else {
      v1 = delta2local(global.v1, l1);
      v4 = delta2local(global.v4, l4);
    }
    Bezier candidate = Bezier.withVs(l1, v1, v4, l4);
    var got = candidate.pointAt(0.5);
    var want = locateWithLeftingRef(global.pointAt(0.5), got.x*xscale);
    if( want.sqDist(got) < 8 ) {
      // this looks good!
      return List.of(candidate);
    } else if( want.segment != seg ) {
      // Huh? This sometimes happens when we're far from the track
      // and and the x scale varies wildly.
      return List.of(candidate);
    } else {
      var split = global.split(0.5);
      return TreeList.concat(
          global2localOneSegment(seg, l1, split.front(), want, level+1),
          global2localOneSegment(seg, want, split.back(), l4, level+1));
    }
  }

  private Vector delta2local(Vector v, LocalPoint lp) {
    setLeftingWithCurrentSegment(lp.lefting);
    double curvature = curvatureAt(lp.lefting);
    double curvebase = curves.segmentSlew(segment);
    double factor = 1-(lp.downing-curvebase)*curvature;
    if( factor < 0.01 ) factor = 0.01;
    return delta2local(v, lp.normal, factor*xscale);
  }

  private Vector delta2local(Vector v, UnitVector down, double xscale) {
    double y = down.dot(v);
    double x = down.y*v.x - down.x*v.y;
    return Vector.of(x/xscale, y/yscale);
  }

  @Override
  public AffineTransform createDifferential(Point local) {
    LocalPoint lp = local2local(local);
    var xx = delta2local(UnitVector.RIGHT, lp);
    var yy = delta2local(UnitVector.DOWN, lp);
    return new AffineTransform(xx.x, xx.y, yy.x, yy.y, 0, 0);
  }

}