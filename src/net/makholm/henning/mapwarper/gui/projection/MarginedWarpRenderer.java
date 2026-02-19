package net.makholm.henning.mapwarper.gui.projection;

import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.gui.maprender.SupersamplingRenderer;
import net.makholm.henning.mapwarper.gui.projection.MinimalWarpWorker.PointWithNormalAndKind;
import net.makholm.henning.mapwarper.track.SegKind;

final class MarginedWarpRenderer extends SupersamplingRenderer {

  static final int RGB_MARGIN      = 0xFFCCCCCC;
  static final int RGB_PASS        = 0xFFC5C5AA;
  static final int RGB_SKIP        = 0xFFDDDDDD;
  static final int RGB_SINGULARITY = 0xFF222222;

  private final WarpedProjection.WarpRenderFactory common;
  private final MinimalWarpWorker worker;
  private final WarpMargins.Worker marginWorker;

  protected MarginedWarpRenderer(WarpedProjection.WarpRenderFactory common,
      RenderTarget target) {
    super(common.spec, common.xscale, common.yscale, target, common.recipes);
    this.common = common;
    this.worker = new MinimalWarpWorker(common.warp);
    this.marginWorker = common.margins.new Worker(worker, ybase, common.yscale);
  }

  @Override
  protected PointWithNormalAndKind locateColumn(double x, double y) {
    worker.setLefting(x);
    return worker.pointWithNormal(worker.projected2downing(y));
  }

  @Override
  protected boolean renderColumn(int col, double xmid, int ymin, int ymax) {
    var pwn0 = locateColumn(xmid-0.5*xscale, ybase);
    if( pwn0.kind == SegKind.SKIP ) return blankout(col, ymin, ymax, RGB_SKIP);
    var pwnM = locateColumn(xmid, ybase);
    if( pwnM.kind == SegKind.SKIP ) return blankout(col, ymin, ymax, RGB_SKIP);
    var pwn1 = locateColumn(xmid+0.5*xscale, ybase);
    if( pwn1.kind == SegKind.SKIP ) return blankout(col, ymin, ymax, RGB_SKIP);

    boolean fastForward = pwnM.kind == SegKind.PASS;

    double leftMargin, rightMargin;
    if( !fastForward && common.ignoreMargins ) {
      leftMargin = Double.NEGATIVE_INFINITY;
      rightMargin = Double.POSITIVE_INFINITY;
    } else {
      marginWorker.setLefting(xmid);
      leftMargin = marginWorker.findLeft() - 0.5;
      rightMargin = marginWorker.findRight() - 0.5;
      if( marginWorker.seenSkip ) {
        // render everything like outside-margins, but without dimming
        return renderWithoutSupersampling(col, xmid,
            ymin, ymax, common.marginChain);
      }
    }

    int outsideMargins = fastForward ? RGB_PASS :
      common.blankOutsideMargins ? RGB_MARGIN :
        0;
    if( outsideMargins != 0 ) {
      if( rightMargin < ymax ) {
        int start = (int)Math.max(ymin, rightMargin);
        blankout(col, start, ymax, outsideMargins);
        if( start == ymin ) return true; else ymax = start-1;
      }
      if( leftMargin > ymin ) {
        int end = (int)Math.min(ymax, Math.ceil(leftMargin));
        blankout(col, ymin, end, outsideMargins);
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
        return blankout(col, ymin, ymax, RGB_SINGULARITY);
      } else {
        int firstBad = (int)Math.ceil(curvecenter);
        blankout(col, firstBad, ymax, RGB_SINGULARITY);
        ymax = firstBad-1;
      }
    } else {
      if( curvecenter < ymin ) {
        // OK
      } else if( curvecenter >= ymax ) {
        return blankout(col, ymin, ymax, RGB_SINGULARITY);
      } else {
        int lastBad = (int)Math.floor(curvecenter);
        blankout(col, ymin, lastBad, RGB_SINGULARITY);
        ymin = lastBad+1;
      }
    }

    boolean hadAllPixels = true;

    // Use lower-quality rendering (especially without downloading
    // anything but also without supersampling) outside the margins.
    if( rightMargin < ymax ) {
      int start = (int)Math.max(ymin, rightMargin);
      hadAllPixels &= renderWithoutSupersampling(col, pwnM,
          start, ymax, common.marginChain, -1);
      if( start == ymin ) return hadAllPixels; else ymax = start-1;
    }
    if( leftMargin > ymin ) {
      int end = (int)Math.min(ymax, Math.ceil(leftMargin));
      hadAllPixels &= renderWithoutSupersampling(col, pwnM,
          ymin, end, common.marginChain, -1);
      if( end == ymax ) return hadAllPixels; else ymin = end+1;
    }

    return hadAllPixels & supersampleColumn(col, pwn0, pwnM, pwn1, ymin, ymax,
        fastForward ? common.passRecipe : supersample0);
  }

  private boolean blankout(int col, int ymin, int ymax, int rgb) {
    for( int row = ymin; row <= ymax; row++ )
      target.givePixel(col, row, rgb);
    return true;
  }

}
