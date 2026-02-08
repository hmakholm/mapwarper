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
    if( !target.isUrgent() || spec.mainTiles().context.ramCache.isEmpty() ) {
      // Pretend we have already completed the from-RAM-only pass
      renderPassesCompleted ++;
    }
  }

  protected abstract PointWithNormal locateColumn(double x, double y);

  @Override
  public final void doSomeWork() throws AbortRendering {
    if( cacheLookupLevel == LookupLevel.DOWNLOAD ) {
      // Keep it like that
    } else if( renderPassesCompleted == 0 )
      cacheLookupLevel = LookupLevel.RAM;
    else if( renderPassesCompleted == renderPassesWanted-1 )
      cacheLookupLevel = LookupLevel.DOWNLOAD;
    else
      cacheLookupLevel = LookupLevel.DISK;
    oneRenderPass();
    renderPassesCompleted++;
    if( renderPassesCompleted < renderPassesWanted ) {
      markAllForRendering();
    }
  }

  protected final boolean renderWithoutSupersampling(int col, double xmid,
      int ymin, int ymax, double ybase, long fallbackChain, int dimmask) {
    dimmask &= 0x7F7F7F;
    PointWithNormal pwn = locateColumn(xmid, ybase + yscale/2);
    boolean hadAllPixels = true;
    for( int y=ymin; y<=ymax; y++ ) {
      Point p = pwn.pointOnNormal(y * yscale);
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
    if( renderPassesCompleted < renderPassesWanted )
      return 1000 - renderPassesCompleted;
    else if( anythingMarkedForRendering() )
      return 1;
    else
      return -1;
  }

}
