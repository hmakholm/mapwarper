package net.makholm.henning.mapwarper.gui.projection;

import java.util.ArrayList;

import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.gui.track.FileContent;
import net.makholm.henning.mapwarper.gui.track.SegmentChain;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.util.SingleMemo;

class WarpMargins {

  public static final  SingleMemo<WarpedProjection, WarpMargins> cache =
      SingleMemo.of(WarpMargins::new);

  private final WarpedProjection owner;
  private final LineSeg[] leftBoundaryArray;
  private final LineSeg[] rightBoundaryArray;

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

    var leftList = new ArrayList<LineSeg>();
    var rightList = new ArrayList<LineSeg>();

    var worker = new WarpedProjectionWorker(owner, 1.435 * meter);
    for( FileContent track : owner.usedFiles ) {
      for( SegmentChain chain : track.chains() ) {
        if( chain.chainClass != SegKind.BOUND ) continue;
        TrackNode prevNode = chain.nodes.get(0);
        Point prevLocal = worker.global2local(prevNode);
        for( int i=0; i<chain.numSegments; i++ ) {
          TrackNode nextNode = chain.nodes.get(i+1);
          Point nextLocal = worker.global2local(nextNode);
          switch( chain.kinds.get(i) ) {
          case BOUND:
            if( prevLocal.x < nextLocal.x ) {
              // Single sided bounds are _left_ bounds in the direction
              // they're drawn.
              if( prevLocal.y < 0 && nextLocal.y < 0 )
                leftList.add(prevNode.to(nextNode));
            } else {
              if( prevLocal.y > 0 && nextLocal.y > 0 )
                rightList.add(nextNode.to(prevNode));
            }
            break;
          default:
            // anything else is not a bound at all
          }
          prevNode = nextNode;
          prevLocal = nextLocal;
        }
      }
    }
    leftBoundaryArray = leftList.toArray(new LineSeg[0]);
    rightBoundaryArray = rightList.toArray(new LineSeg[0]);
  }

  public double leftMargin(MinimalWarpWorker worker, double lefting) {
    if( lefting < 0 || lefting > owner.totalLength )
      return Double.POSITIVE_INFINITY;
    PointWithNormal pwn = worker.normalAt(lefting);
    double slew = owner.curves.segmentSlew(worker.segment);
    return slew - findMargin(leftBoundaryArray, pwn.reverse());
  }

  public double rightMargin(MinimalWarpWorker worker, double lefting) {
    if( lefting < 0 || lefting > owner.totalLength )
      return Double.NEGATIVE_INFINITY;
    PointWithNormal pwn = worker.normalAt(lefting);
    double slew = owner.curves.segmentSlew(worker.segment);
    return slew + findMargin(rightBoundaryArray, pwn);
  }

  private double findMargin(LineSeg[] bounds, PointWithNormal point) {
    double closest = maxMargin;
    for( var segment : bounds ) {
      double got = point.intersectWithNormal(segment);
      if( got > 0 && got < closest )
        closest = got;
    }
    return closest == maxMargin ? defaultMargin : closest;
  }

}
