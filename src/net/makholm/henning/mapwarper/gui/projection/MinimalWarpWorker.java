package net.makholm.henning.mapwarper.gui.projection;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.TransformHelper;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.gui.track.SegmentChain;

class MinimalWarpWorker extends TransformHelper {

  protected final WarpedProjection warp;
  protected final SegmentChain.HasSegmentSlew slews;

  protected MinimalWarpWorker(WarpedProjection owner,
      SegmentChain.HasSegmentSlew slews) {
    this.warp = owner;
    this.slews = slews;
    lefting = 0;
    selectCurve(0);
    computePoint();
  }

  private double lefting;
  protected int segment;
  protected double x, y;

  private double dx, dy;
  private UnitVector commonNormal;

  protected final void setLefting(double newLefting) {
    if( newLefting != lefting ) {
      lefting = newLefting;
      findSegment();
      computePoint();
    }
  }

  protected final void setLeftingWithCurrentSegment(double newLefting) {
    lefting = newLefting;
    computePoint();
  }

  protected final double projected2downing(double y) {
    return y - slews.segmentSlew(segment);
  }

  protected PointWithNormal pointWithNormal(double downing) {
    UnitVector normal = currentNormal();
    return new PointWithNormal(
        x + normal.x*downing, y + normal.y*downing,
        normal);
  }

  protected PointWithNormal normalAt(double lefting) {
    setLefting(lefting);
    return new PointWithNormal(x, y, currentNormal());
  }

  protected double curvatureAt(double lefting) {
    setLefting(lefting);
    return curve.signedCurvatureAt(parameterWithinCurve());
  }

  private UnitVector currentNormal() {
    if( commonNormal != null )
      return commonNormal;
    else
      return createNormal();
  }

  protected final boolean currentSegmentIsStraight() {
    return commonNormal != null;
  }

  private double validFrom, validTo;
  private Bezier curve;
  private double w0, invCurvelen;

  private void findSegment() {
    int lowEnough, tooHigh;
    if( lefting < validFrom ) {
      lowEnough = -1;
      tooHigh = segment;
    } else if( lefting < validTo ) {
      return;
    } else if( segment+1 >= warp.track.numSegments ) {
      selectPseudolast();
      return;
    } else if( lefting < warp.nodeLeftings[segment+2] ) {
      // special case for moving one segment onwards
      selectCurve(segment+1);
      return;
    } else {
      lowEnough = segment+2;
      tooHigh = warp.track.numNodes;
    }

    while( tooHigh > lowEnough+1 ) {
      int mid = (lowEnough+tooHigh)/2;
      if( lefting < warp.nodeLeftings[mid] )
        tooHigh = mid;
      else
        lowEnough = mid;
    }
    selectCurve(lowEnough);
  }

  /**
   * Given {@link #segment}, initialize all other local fields except those
   * that vary with {@link #projectedX}.
   */
  protected final void selectCurve(int wantSegment) {
    if( wantSegment < 0 ) {
      selectPseudofirst();
    } else if( wantSegment >= warp.track.numSegments ) {
      selectPseudolast();
    } else {
      segment = wantSegment;
      w0 = validFrom = warp.nodeLeftings[segment];
      validTo = warp.nodeLeftings[segment+1];
      invCurvelen = 1/(validTo - validFrom);
      curve = warp.curves.get(segment);
      if( curve.isExactlyALine() )
        selectedStraightCurve();
      else
        commonNormal = null;
    }
  }

  private void selectPseudofirst() {
    segment = -1;
    validFrom = Double.NEGATIVE_INFINITY;
    w0 = validTo = 0;
    curve = warp.pseudofirst;
    invCurvelen = 1;
    selectedStraightCurve();
  }

  private void selectPseudolast() {
    segment = warp.track.numSegments;
    w0 = validFrom = warp.totalLength;
    validTo = Double.POSITIVE_INFINITY;
    curve = warp.pseudolast;
    invCurvelen = 1;
    selectedStraightCurve();
  }

  private void selectedStraightCurve() {
    dx = curve.displacement.x;
    dy = curve.displacement.y;
    commonNormal = createNormal();
  }

  private UnitVector createNormal() {
    return UnitVector.along(-dy, dx);
  }

  private void computePoint() {
    double t = parameterWithinCurve();
    double u = 1-t;
    x = curve.p1.x + t*curve.displacement.x + t*u*(u*curve.dv1.x - t*curve.dv4.x);
    y = curve.p1.y + t*curve.displacement.y + t*u*(u*curve.dv1.y - t*curve.dv4.y);
    if( commonNormal == null ) {
      double a = 1 + t*(3*t-4);
      double b = t*(2-3*t);
      dx = curve.displacement.x + a*curve.dv1.x - b*curve.dv4.x;
      dy = curve.displacement.y + a*curve.dv1.y - b*curve.dv4.y;
    }
  }

  private double parameterWithinCurve() {
    return (lefting - w0) * invCurvelen;
  }

}
