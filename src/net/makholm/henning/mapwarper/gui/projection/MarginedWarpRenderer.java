package net.makholm.henning.mapwarper.gui.projection;

import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.track.SegmentChain;

final class MarginedWarpRenderer extends BaseWarpRenderer {

  private final WarpMargins margins;
  private final long marginChain;
  private final SegmentChain.Smoothed curves;
  private final boolean blankOutsideMargins;

  protected MarginedWarpRenderer(WarpedProjection warp, LayerSpec spec,
      double xpixsize, double ypixsize, RenderTarget target,
      SupersamplingRecipe supersample, WarpMargins margins, long marginChain) {
    super(warp, spec, xpixsize, ypixsize, target, supersample);
    this.margins = margins;
    this.marginChain = marginChain;
    this.curves = warp.curves;
    this.blankOutsideMargins = Toggles.BLANK_OUTSIDE_MARGINS.setIn(spec.flags());
  }

  /**
   * Avoid triggering downloads in right next to the defined margins --
   * to guard against overdownloading due to floating-point rounding.
   * This size is in global coordinates, corresponding to one z18-pixel,
   * or about half a meter in Copenhagen.
   */
  private static final double GUARD_ZONE = 16;

  @Override
  protected boolean renderColumn(int col, double xmid,
      int ymin, int ymax, double ybase) {
    boolean hadAllPixels = true;

    double leftMargin =
        (margins.leftMargin(worker, xmid) - ybase) / yscale - 0.5;
    double rightMargin =
        (margins.rightMargin(worker, xmid) - ybase) / yscale - 0.5;
    double leftInnerMargin = leftMargin + GUARD_ZONE / yscale;
    double rightInnerMargin = rightMargin - GUARD_ZONE / yscale;

    if( blankOutsideMargins ) {
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
    double radius = 1/worker.curvatureAt(xmid);
    double curvecenter = (radius + curves.segmentSlew(worker.segment) - ybase)
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
          start, ymax, ybase, marginChain, -1);
      if( start == ymin ) return hadAllPixels; else ymax = start-1;
    }
    if( leftMargin > ymin ) {
      int end = (int)Math.min(ymax, Math.ceil(leftMargin));
      hadAllPixels &= renderWithoutSupersampling(col, xmid,
          ymin, end, ybase, marginChain, -1);
      if( end == ymax ) return hadAllPixels; else ymin = end+1;
    }

    if( rightInnerMargin < ymax ) {
      int start = (int)Math.max(ymin, rightInnerMargin);
      hadAllPixels &= renderSupersampled(col, xmid, start, ymax, ybase, false);
      if( start == ymin ) return hadAllPixels; else ymax = start-1;
    }
    if( leftInnerMargin > ymin ) {
      int end = (int)Math.min(ymax, Math.ceil(leftInnerMargin));
      hadAllPixels &= renderSupersampled(col, xmid, ymin, end, ybase, false);
      if( end == ymax ) return hadAllPixels; else ymin = end+1;
    }

    hadAllPixels &= renderSupersampled(col, xmid, ymin, ymax, ybase, true);
    return hadAllPixels;
  }

  private void blackout(int col, int ymin, int ymax) {
    for( int row = ymin; row <= ymax; row++ )
      target.givePixel(col, row, RGB.SINGULARITY);
  }

  private void whiteout(int col, int ymin, int ymax) {
    for( int row = ymin; row <= ymax; row++ )
      target.givePixel(col, row, 0xFFCCCCCC);
  }

}
