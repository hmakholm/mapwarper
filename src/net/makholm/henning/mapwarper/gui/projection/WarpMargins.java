package net.makholm.henning.mapwarper.gui.projection;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntPredicate;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.gui.projection.WarpedProjectionWorker.LocalPoint;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.track.SegKind;
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

  static final class MarginLine extends Point {
    final SegKind kind;
    MarginLine(double min , SegKind kind, double max) {
      super(min, max);
      this.kind = kind;
    }
    double min() { return x; }
    double max() { return y; }
  }

  final List<MarginLine> skips = new ArrayList<>();

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
      boolean mainTrack = track.contains(owner.track);
      for( SegmentChain chain : track.chains() ) {
        if( !chain.isBound() ) continue;
        TrackNode prevNode = chain.nodes.get(0), nextNode;
        LocalPoint prevLocal = worker.global2local(prevNode), nextLocal;
        for( int i=0; i<chain.numSegments;
            i++, prevNode = nextNode, prevLocal = nextLocal ) {
          var kind = chain.kinds.get(i);
          nextNode = chain.nodes.get(i+1);
          nextLocal = worker.global2local(nextNode);
          boolean rightBound;
          MarginLine kinded;
          if( prevLocal.x < nextLocal.x ) {
            // Single sided bounds are _left_ bounds in the direction
            // they're drawn.
            if( prevLocal.leftOfTrack() && nextLocal.leftOfTrack() ) {
              rightBound = false;
              kinded = new MarginLine(prevLocal.x, kind, nextLocal.x);
            } else
              continue;
          } else {
            if( prevLocal.rightOfTrack() && nextLocal.rightOfTrack() ) {
              rightBound = true;
              kinded = new MarginLine(nextLocal.x, kind, prevLocal.x);
            } else
              continue;
          }
          if( kinded.min() > owner.totalLength || kinded.max() < 0 )
            continue;

          MarginSource thisSeg;
          switch( chain.kinds.get(i) ) {
          case BOUND:
            LineSeg ls = prevNode.to(nextNode);
            thisSeg = (local, global) -> global.intersectWithNormal(ls);
            break;
          case PASS:
          case SKIP:
            if( mainTrack ) skips.add(kinded);
            // in any case, fall through
          case LBOUND:
            ls = prevLocal.to(nextLocal);
            thisSeg = (local, gobal) -> local.intersectWithNormal(ls);
            break;
          default:
            // anything else is not a bound at all
            continue;
          }

          var tree = XyTree.singleton(kinded, thisSeg);
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

  static IntPredicate makeBoundDiscarder(WarpedProjectionWorker worker,
      SegmentChain chain) {
    BitSet toDiscard = new BitSet(chain.numSegments);
    LocalPoint prevLocal = worker.global2local(chain.nodes.get(0)), nextLocal;
    for( int i=0; i<chain.numSegments; i++, prevLocal = nextLocal ) {
      nextLocal = worker.global2local(chain.nodes.get(i+1));
      if( prevLocal.x < nextLocal.x ) {
        if( !(prevLocal.leftOfTrack() && nextLocal.leftOfTrack()) )
          toDiscard.set(i);
      } else {
        if( !(prevLocal.rightOfTrack() && nextLocal.rightOfTrack()) )
          toDiscard.set(i);
      }
    }
    if( toDiscard.isEmpty() )
      return ProjectionWorker.DISCARD_NONE;
    else
      return toDiscard::get;
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

  public class Worker implements XyTree.Callback<MarginSource> {
    private final MinimalWarpWorker worker;
    private final double ybase, yscale;
    private double lefting, slew;
    private PointWithNormal pwnCommon;
    private PointWithNormal pwnGlobal, pwnLocal;

    public Worker(MinimalWarpWorker worker, double ybase, double yscale) {
      this.worker = worker;
      this.ybase = ybase;
      this.yscale = yscale;
    }

    public void setLefting(double lefting) {
      this.lefting = lefting;
      this.pwnCommon = worker.normalAt(lefting);
      this.slew = owner.curves.segmentSlew(worker.segment);
    }

    public double findLeft() {
      if( lefting < 0 || lefting > owner.totalLength )
        return Double.POSITIVE_INFINITY;
      pwnGlobal = pwnCommon.reverse();
      pwnLocal = new PointWithNormal(lefting, slew, UnitVector.UP);
      return (slew - findMargin(leftBoundaryTree) - ybase) / yscale;
    }

    public double findRight() {
      if( lefting < 0 || lefting > owner.totalLength )
        return Double.NEGATIVE_INFINITY;
      pwnGlobal = pwnCommon;
      pwnLocal = new PointWithNormal(lefting, slew, UnitVector.DOWN);
      return (slew + findMargin(rightBoundaryTree) - ybase) / yscale;
    }

    @Override
    public boolean recurseInto(AxisRect rect) {
      // xmin is the first lefting covered by the offered subtree
      if( lefting < rect.xmin() ) return false;
      // ymax is the last lefting covered by the offered substree
      if( lefting > rect.ymax() ) return false;
      return true;
    }

    private double closest;

    @Override
    public void accept(MarginSource source) {
      double got = source.get(pwnLocal, pwnGlobal);
      if( got >= 0 && got < closest )
        closest = got;
    }

    private double findMargin(XyTree<MarginSource> tree) {
      closest = maxMargin;
      XyTree.recurse(tree, Point.ORIGIN, this);
      return closest >= maxMargin ? defaultMargin : closest;
    }
  }

}
