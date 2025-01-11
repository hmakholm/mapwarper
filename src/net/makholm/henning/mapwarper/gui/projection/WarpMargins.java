package net.makholm.henning.mapwarper.gui.projection;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.gui.projection.WarpedProjectionWorker.LocalPoint;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.util.SingleMemo;
import net.makholm.henning.mapwarper.util.XyTree;

class WarpMargins {

  public static final  SingleMemo<WarpedProjection, WarpMargins> cache =
      SingleMemo.of(WarpMargins::new);

  private final WarpedProjection owner;

  /**
   * The boundary trees are trees in a special coordinate system, where
   * the x-coordinate is the <em>smallest</em> lefting relevant for a segment,
   * and the y-coordinate is the <em>largest</em> relevant lefting.
   */
  private final XyTree<MarginSource> leftBoundaryTree, rightBoundaryTree;

  private double maxMargin;
  private double defaultMargin;

  public static WarpMargins get(WarpedProjection owner) {
    return cache.apply(owner);
  }

  private WarpMargins(WarpedProjection owner) {
    this.owner = owner;

    double meter = WebMercator.unitsPerMeter(owner.track.nodes.get(0).y);
    maxMargin = 700 * meter;
    defaultMargin = 20 * meter;

    var leftTree = treeJoiner.empty();
    var rightTree = treeJoiner.empty();

    var worker = new WarpedProjectionWorker(owner);
    for( FileContent track : owner.usedFiles ) {
      for( SegmentChain chain : track.chains() ) {
        if( !chain.isBound() ) continue;
        TrackNode prevNode = chain.nodes.get(0), nextNode;
        LocalPoint prevLocal = worker.global2local(prevNode), nextLocal;
        for( int i=0; i<chain.numSegments;
            i++, prevNode = nextNode, prevLocal = nextLocal ) {
          nextNode = chain.nodes.get(i+1);
          nextLocal = worker.global2local(nextNode);
          boolean rightBound;
          Point leftingMinMax;
          if( prevLocal.x < nextLocal.x ) {
            // Single sided bounds are _left_ bounds in the direction
            // they're drawn.
            if( !prevLocal.rightOfTrack() && !nextLocal.rightOfTrack() ) {
              rightBound = false;
              leftingMinMax = Point.at(prevLocal.x, nextLocal.x);
            } else
              continue;
          } else {
            if( prevLocal.rightOfTrack() && nextLocal.rightOfTrack() ) {
              rightBound = true;
              leftingMinMax = Point.at(nextLocal.x, prevLocal.x);
            } else
              continue;
          }
          if( leftingMinMax.x > owner.totalLength || leftingMinMax.y < 0 )
            continue;

          MarginSource thisSeg;
          switch( chain.kinds.get(i) ) {
          case BOUND:
            LineSeg ls = prevNode.to(nextNode);
            thisSeg = (local, global) -> global.intersectWithNormal(ls);
            break;
          case LBOUND:
            ls = prevLocal.to(nextLocal);
            thisSeg = (local, gobal) -> local.intersectWithNormal(ls);
            break;
          default:
            // anything else is not a bound at all
            continue;
          }

          var tree = XyTree.singleton(leftingMinMax, thisSeg);
          if( rightBound )
            rightTree = treeJoiner.union(rightTree, tree);
          else
            leftTree = treeJoiner.union(leftTree, tree);

          prevNode = nextNode;
          prevLocal = nextLocal;
        }
      }
    }
    XyTree.resolveDeep(leftTree, source -> {});
    XyTree.resolveDeep(rightTree, source -> {});
    leftBoundaryTree = leftTree;
    rightBoundaryTree = rightTree;
  }

  interface MarginSource {
    abstract double get(PointWithNormal pwnLocal, PointWithNormal pwnGlobal);
  }

  private static final XyTree.Unioner<MarginSource> treeJoiner =
      new XyTree.Unioner<MarginSource>() {
    @Override
    protected MarginSource combine(MarginSource a, MarginSource b) {
      return (local, global) -> {
        double aa = a.get(local, global);
        double bb = b.get(local, global);
        return aa >= 0 && aa < bb ? aa : bb;
      };
    }
  };

  private class MarginFinder implements XyTree.Callback<MarginSource> {
    final double lefting;
    final PointWithNormal pwnLocal, pwnGlobal;

    double closest = maxMargin;

    MarginFinder(PointWithNormal pwnLocal, PointWithNormal pwnGlobal) {
      this.lefting = pwnLocal.x;
      this.pwnLocal = pwnLocal;
      this.pwnGlobal = pwnGlobal;
    }

    @Override
    public boolean recurseInto(AxisRect rect) {
      // xmin is the first lefting covered by the offered subtree
      if( lefting < rect.xmin() ) return false;
      // ymax is the last lefting covered by the offered substree
      if( lefting > rect.ymax() ) return false;
      return true;
    }

    @Override
    public void accept(MarginSource source) {
      double got = source.get(pwnLocal, pwnGlobal);
      if( got >= 0 && got < closest )
        closest = got;
    }

    double findMargin(XyTree<MarginSource> tree) {
      XyTree.recurse(tree, Point.ORIGIN, this);
      return closest >= maxMargin ? defaultMargin : closest;
    }
  }

  private static final UnitVector UP = Vector.of(0, -1).normalize();
  private static final UnitVector DOWN = Vector.of(0, 1).normalize();

  public double leftMargin(MinimalWarpWorker worker, double lefting) {
    if( lefting < 0 || lefting > owner.totalLength )
      return Double.POSITIVE_INFINITY;
    PointWithNormal pwnGlobal = worker.normalAt(lefting);
    double slew = owner.curves.segmentSlew(worker.segment);
    PointWithNormal pwnLocal = new PointWithNormal(lefting, slew, UP);
    var mf = new MarginFinder(pwnLocal, pwnGlobal.reverse());
    return slew - mf.findMargin(leftBoundaryTree);
  }

  public double rightMargin(MinimalWarpWorker worker, double lefting) {
    if( lefting < 0 || lefting > owner.totalLength )
      return Double.NEGATIVE_INFINITY;
    PointWithNormal pwnGlobal = worker.normalAt(lefting);
    double slew = owner.curves.segmentSlew(worker.segment);
    PointWithNormal pwnLocal = new PointWithNormal(lefting, slew, DOWN);
    var mf = new MarginFinder(pwnLocal, pwnGlobal);
    return slew + mf.findMargin(rightBoundaryTree);
  }

}
