package net.makholm.henning.mapwarper.gui.projection;

import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.gui.maprender.SupersamplingRenderer;
import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.track.SegKind;

final class MarginedWarpRenderer extends SupersamplingRenderer {

  private final WarpedProjection.WarpRenderFactory common;
  private final MinimalWarpWorker worker;

  protected MarginedWarpRenderer(WarpedProjection.WarpRenderFactory common,
      RenderTarget target) {
    super(common.spec, common.xscale, common.yscale, target, common.mainRecipe);
    this.common = common;
    this.worker = new MinimalWarpWorker(common.warp);
  }

  @Override
  protected PointWithNormal locateColumn(double x, double y) {
    worker.setLefting(x);
    return worker.pointWithNormal(worker.projected2downing(y));
  }

  @Override
  protected boolean renderColumn(int col, double xmid, int ymin, int ymax) {
    if( worker.kindAt(xmid-0.5) == SegKind.SKIP ||
        worker.kindAt(xmid+0.5) == SegKind.SKIP )
      return skipout(col, ymin, ymax);
    var kind = worker.kindAt(xmid);
    if( kind == SegKind.SKIP ) return skipout(col,ymin,ymax);
    boolean fastForward = kind == SegKind.PASS;

    boolean hadAllPixels = true;
    double leftMargin, rightMargin;
    if( !fastForward && common.ignoreMargins ) {
      leftMargin = Double.NEGATIVE_INFINITY;
      rightMargin = Double.POSITIVE_INFINITY;
    } else {
      var margins = common.margins;
      leftMargin = (margins.leftMargin(worker, xmid) - ybase) / yscale - 0.5;
      rightMargin = (margins.rightMargin(worker, xmid) - ybase) / yscale - 0.5;
    }

    if( fastForward || common.blankOutsideMargins ) {
      if( rightMargin < ymax ) {
        int start = (int)Math.max(ymin, rightMargin);
        whiteout(col, start, ymax);
        if( start == ymin ) return true; else ymax = start-1;
      }
      if( leftMargin > ymin ) {
        int end = (int)Math.min(ymax, Math.ceil(leftMargin));
        whiteout(col, ymin, end);
        if( end == ymax ) return true; else ymin = end+1;
      }
    }

    // Handle curvature singularities
    double slew = common.warp.curves.segmentSlew(worker.segment);
    double radius = 1/worker.curvatureAt(xmid);
    double curvecenter = (radius + slew - ybase)
        / yscale - 0.5;
    if( radius > 0 ) {
      if( curvecenter > ymax ) {
        // OK
      } else if( curvecenter <= ymin ) {
        blackout(col, ymin, ymax);
        return true;
      } else {
        int firstBad = (int)Math.ceil(curvecenter);
        blackout(col, firstBad, ymax);
        ymax = firstBad-1;
      }
    } else {
      if( curvecenter < ymin ) {
        // OK
      } else if( curvecenter >= ymax ) {
        blackout(col, ymin, ymax);
        return true;
      } else {
        int lastBad = (int)Math.floor(curvecenter);
        blackout(col, ymin, lastBad);
        ymin = lastBad+1;
      }
    }

    // Use lower-quality rendering (especially without downloading
    // anything but also without supersampling) outside the margins.
    if( rightMargin < ymax ) {
      int start = (int)Math.max(ymin, rightMargin);
      hadAllPixels &= renderWithoutSupersampling(col, xmid,
          start, ymax, common.marginChain, -1);
      if( start == ymin ) return hadAllPixels; else ymax = start-1;
    }
    if( leftMargin > ymin ) {
      int end = (int)Math.min(ymax, Math.ceil(leftMargin));
      hadAllPixels &= renderWithoutSupersampling(col, xmid,
          ymin, end, common.marginChain, -1);
      if( end == ymax ) return hadAllPixels; else ymin = end+1;
    }

    return hadAllPixels & supersampleColumn(col, xmid, ymin, ymax,
        fastForward ? common.passRecipe : supersample0);
  }

  private void blackout(int col, int ymin, int ymax) {
    for( int row = ymin; row <= ymax; row++ )
      target.givePixel(col, row, RGB.SINGULARITY);
  }

  private void whiteout(int col, int ymin, int ymax) {
    for( int row = ymin; row <= ymax; row++ )
      target.givePixel(col, row, 0xFFCCCCCC);
  }

  private boolean skipout(int col, int ymin, int ymax) {
    for( int row = ymin; row <= ymax; row++ )
      target.givePixel(col, row, 0x00444444);
    return true;
  }

}
