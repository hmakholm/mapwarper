package net.makholm.henning.mapwarper.gui.maprender;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.util.AbortRendering;

/**
 * Common code for rendering columns of pixels from a projection.
 */
public abstract class SimpleRenderer extends CommonRenderer {

  protected int renderPassesCompleted;
  protected int renderPassesWanted = 2;

  protected SimpleRenderer(LayerSpec spec, double xpixsize, double ypixsize,
      RenderTarget target) {
    super(spec, xpixsize, ypixsize, target);
    if( spec.mainTiles().context.ramCache.isEmpty() ) {
      // Pretend we have already completed the from-RAM-only pass
      renderPassesCompleted ++;
    }
  }

  protected abstract PointWithNormal locateColumn(double x, double y);

  @Override
  public final void doSomeWork() throws AbortRendering {
    adjustForNonUrgency();
    loadTiles = renderPassesCompleted > 0;
    oneRenderPass();
    renderPassesCompleted++;
    if( renderPassesCompleted < renderPassesWanted ) {
      markAllForRendering();
    }
  }

  protected final boolean renderWithoutSupersampling(int col, double xmid,
      int ymin, int ymax, long fallbackChain) {
    return renderWithoutSupersampling(col, locateColumn(xmid, ybase),
        ymin, ymax, fallbackChain, 0);
  }

  protected final boolean renderWithoutSupersampling(int col,
      PointWithNormal pwn,
      int ymin, int ymax, long fallbackChain, int dimmask) {
    dimmask &= 0x7F7F7F;
    boolean hadAllPixels = true;
    for( int y=ymin; y<=ymax; y++ ) {
      Point p = pwn.pointOnNormal((y+0.5) * yscale);
      int rgb = getPixel(p, fallbackChain);
      if( rgb == RGB.OUTSIDE_BITMAP )
        hadAllPixels = false;
      else {
        rgb = applyTilegrid(p, rgb);
        rgb -= (rgb >> 1) & dimmask;
        target.givePixel(col, y, rgb);
      }
    }
    return hadAllPixels;
  }

  @Override
  public int priority() {
    adjustForNonUrgency();
    if( renderPassesCompleted < renderPassesWanted )
      return 1000 - renderPassesCompleted;
    else
      return super.priority();
  }

  /**
   * This adjustment really should have taken place in the constructor,
   * except that subclass constructors can modify {@link renderPassesWanted},
   * so that would be too early to do it correctly.
   */
  private void adjustForNonUrgency() {
    if( renderPassesCompleted < renderPassesWanted && !target.isUrgent() ) {
      // Since there's already _some_ kind of map shown, there's no need
      // (and it might even look bad!) to do any initial lower-quality
      // rendering passes.
      renderPassesCompleted = renderPassesWanted-1;
    }
  }

}
