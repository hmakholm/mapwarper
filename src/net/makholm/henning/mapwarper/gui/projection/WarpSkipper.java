package net.makholm.henning.mapwarper.gui.projection;

import java.util.ArrayList;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.RootFinder;

class WarpSkipper {

  /**
   * The additional supersqueeze factor for PASS regions.
   */
  private static final int PASS_SUPERSQUEEZE = 15;

  /**
   * The total length in <em>meters</em> of a SKIP region.
   */
  private static final int SKIP_METERS = 4000 / PASS_SUPERSQUEEZE;

  final WarpedProjection warp;
  final MinimalWarpWorker worker;

  final List<WarpMargins.MarginLine> skips;

  final ArrayList<TrackNode> nodes = new ArrayList<>();
  final ArrayList<Bezier> curves = new ArrayList<>();
  final ArrayList<SegKind> skipKinds = new ArrayList<>();
  final ArrayList<Double> slews = new ArrayList<>();

  WarpSkipper(WarpedProjection owner) {
    warp = owner;
    worker = new MinimalWarpWorker(owner);
    skips = sortAndJoinSkips();
  }

  private List<WarpMargins.MarginLine> sortAndJoinSkips() {
    var sorted = new ArrayList<>(WarpMargins.get(warp).skips);
    sorted.sort((a,b) -> Double.compare(a.min(), b.min()));

    var joined = new ArrayList<WarpMargins.MarginLine>();
    WarpMargins.MarginLine current = null;
    for( var next : sorted ) {
      if( next.max() <= 0 ) {
        // Shouldn't happen, but explicitly ignore anyway
      } else if( current == null ) {
        current = next;
      } else if( next.min() < current.max() ) {
        var kind = current.kind;
        if( next.kind.compareTo(kind) > 0 ) kind = next.kind;
        current = new WarpMargins.MarginLine(current.min(), kind,
            Math.max(current.max(), next.max()));
      } else {
        joined.add(current);
        current = next;
      }
    }
    if( current != null ) joined.add(current);
    return joined;
  }

  private int partialCurveIndex;
  private Double boxedSlew;
  private Bezier partialCurve;
  private double partialStartLefting;
  private double partialEndLefting;

  private double nextSplitLefting;
  private int skipIndex;
  private boolean skipping;

  private Double setSlew(double slew) {
    if( boxedSlew == null || boxedSlew.doubleValue() != slew )
      boxedSlew = Double.valueOf(slew);
    return boxedSlew;
  }

  private boolean nextSegment() {
    int i = ++partialCurveIndex;

    nodes.add(warp.track.nodes.get(i));
    slews.add(setSlew(warp.curves.nodeSlew(i)));
    if( i >= warp.curves.size() ) return false;

    partialStartLefting = warp.nodeLeftings[i];
    partialEndLefting = warp.nodeLeftings[i+1];
    partialCurve = warp.curves.get(i);
    setSlew(warp.curves.segmentSlew(i));
    return true;
  }

  private void nextSkip() {
    if( !skipping ) {
      skipping = true;
      nextSplitLefting = skips.get(skipIndex).max();
    } else {
      skipping = false;
      skipIndex++;
      if( skipIndex >= skips.size() )
        nextSplitLefting = Double.POSITIVE_INFINITY;
      else
        nextSplitLefting = skips.get(skipIndex).min();
    }
  }

  private void emitSegmentBody(Bezier curve) {
    curves.add(curve);
    if( skipping )
      skipKinds.add(skips.get(skipIndex).kind);
    else
      skipKinds.add(warp.track.kinds.get(partialCurveIndex));
    slews.add(boxedSlew);
  }

  /**
   * Allow the actual split between skipping and ordinary warp to vary
   * this much from the split line endpoints. (This is the size of one
   * pixel in a default Google aerophoto).
   */
  private static final double JIGGLE = 16;

  private Bezier.SplitBezier splitAtLefting(double lefting) {
    worker.setLefting(lefting);
    var pwn = worker.pointWithNormal(0);
    var t = new RootFinder(JIGGLE/partialCurve.estimateLength()) {
      @Override
      protected double f(double tt) {
        double xx = pwn.signedDistanceFromNormal(partialCurve.pointAt(tt));
        return Math.abs(xx) < JIGGLE/3 ? 0 : xx;
      }
    }.rootBetween(0, partialStartLefting-lefting, 1, partialEndLefting-lefting);
    return partialCurve.split(t);
  }

  WarpedProjection convert() {
    if( skips.isEmpty() )
      return warp;

    partialCurveIndex = -1;
    nextSegment();
    nextSplitLefting = skips.get(0).min();

    for(;;) {
      if( nextSplitLefting <= partialStartLefting+JIGGLE ) {
        nextSkip();
      } else if( nextSplitLefting >= partialEndLefting-JIGGLE ) {
        emitSegmentBody(partialCurve);
        if( !nextSegment() )
          return finish();
      } else {
        var splitres = splitAtLefting(nextSplitLefting);
        emitSegmentBody(splitres.front());
        nodes.add(TrackNode.of(splitres.front().p4));
        slews.add(boxedSlew);
        partialCurve = splitres.back();
        partialStartLefting = nextSplitLefting;
      }
    }
  }

  private WarpedProjection finish() {
    if( curves.size() != skipKinds.size() )
      throw BadError.of("This is very wrong");
    if( nodes.size() != curves.size()+1 )
      throw BadError.of("This is very wrong");
    var newTrack = new SegmentChain(nodes, skipKinds, null);
    var newCurves = SegmentChain.directSmoothed(curves, slews);
    return new WarpedProjection(warp, warp.sourcename0, warp.usedFiles,
        newTrack, newCurves);
  }

  static double[] projectedLengths(SegmentChain track,
      SegmentChain.Smoothed curves) {
    double[] lengths = new double[track.numSegments];
    for( int i=0; i<lengths.length; i++ )
      lengths[i] = curves.get(i).estimateLength();
    for( int i=0; i<lengths.length; i++ ) {
      switch( track.kinds.get(i) ) {
      default:
        // the length is already good
        break;
      case PASS:
        lengths[i] *= 1.0/PASS_SUPERSQUEEZE;
        break;
      case SKIP:
        int i0 = i;
        double total = lengths[i];
        while( i < track.numSegments-1 &&
            track.kinds.get(i+1) == SegKind.SKIP ) {
          i++;
          total += lengths[i];
        }
        double squeezeInto = SKIP_METERS *
            WebMercator.unitsPerMeter(track.nodes.get(i0).y);
        if( squeezeInto < total ) {
          for( int j=i0; j<=i; j++ )
            lengths[j] *= squeezeInto / total;
        }
        break;
      }
    }
    return lengths;
  }

}