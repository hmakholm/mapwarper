package net.makholm.henning.mapwarper.gui.projection;

import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.gui.track.SegmentChain;
import net.makholm.henning.mapwarper.rgb.RGB;

final class MarginedWarpRenderer extends BaseWarpRenderer {

  private final WarpMargins margins;
  private final SegmentChain.HasSegmentSlew slews;

  protected MarginedWarpRenderer(WarpedProjection warp, WarpMargins margins,
      LayerSpec spec, double xpixsize, double ypixsize, RenderTarget target,
      SupersamplingRecipe supersample, long fallbackChain) {
    super(warp, spec, xpixsize, ypixsize, target,
        supersample, fallbackChain);
    this.margins = margins;
    this.slews = warp.curves;
  }

  @Override
  protected boolean renderColumn(int col, double xmid,
      int ymin, int ymax, double ybase) {
    boolean hadAllPixels = true;

    double leftMargin =
        (margins.leftMargin(worker, xmid) - ybase) / yscale - 0.5;
    double rightMargin =
        (margins.rightMargin(worker, xmid) - ybase) / yscale - 0.5;

    // Handle curvature singularities
    double radius = 1/worker.curvatureAt(xmid);
    double curvecenter = (radius + slews.segmentSlew(worker.segment) - ybase)
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
          start, ymax, ybase, fallbackChain, -1);
      if( start == ymin )
        return hadAllPixels;
      else
        ymax = start-1;
    }
    if( leftMargin > ymin ) {
      int end = (int)Math.min(ymax, Math.ceil(leftMargin));
      hadAllPixels &= renderWithoutSupersampling(col, xmid,
          ymin, end, ybase, fallbackChain, -1);
      if( end == ymax )
        return hadAllPixels;
      else
        ymin = end+1;
    }

    if( renderPassesCompleted < 2 )
      return hadAllPixels & renderWithoutSupersampling(col, xmid,
          ymin, ymax, ybase, combinedChain(), 0);
    else
      return hadAllPixels & super.renderColumn(col, xmid, ymin, ymax, ybase);
  }

  private void blackout(int col, int ymin, int ymax) {
    for( int row = ymin; row <= ymax; row++ )
      target.givePixel(col, row, RGB.SINGULARITY);
  }

}
