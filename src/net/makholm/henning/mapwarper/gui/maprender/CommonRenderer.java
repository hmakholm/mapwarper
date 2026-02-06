package net.makholm.henning.mapwarper.gui.maprender;


import static net.makholm.henning.mapwarper.gui.maprender.FallbackChain.ATTEMPT_MASK;
import static net.makholm.henning.mapwarper.gui.maprender.FallbackChain.BITS_PER_ATTEMPT;
import static net.makholm.henning.mapwarper.gui.maprender.FallbackChain.DOWNLOAD_BIT;
import static net.makholm.henning.mapwarper.gui.maprender.FallbackChain.FALLBACK_BIT;
import static net.makholm.henning.mapwarper.gui.maprender.FallbackChain.ZOOM_SHIFT;
import static net.makholm.henning.mapwarper.gui.maprender.FallbackChain.addresserIndex;
import static net.makholm.henning.mapwarper.gui.maprender.FallbackChain.cacheSetOf64;
import static net.makholm.henning.mapwarper.gui.maprender.FallbackChain.cacheTag;
import static net.makholm.henning.mapwarper.gui.maprender.FallbackChain.downloadifyCacheTag;

import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.tiles.TileSpec;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.util.AbortRendering;
import net.makholm.henning.mapwarper.util.BadError;

abstract class CommonRenderer implements RenderWorker {

  protected final LayerSpec spec;
  protected final double xscale;
  protected final double yscale;
  protected final RenderTarget target;
  protected final boolean tilegrid;

  private final Tileset mainTiles;
  private final Tileset fallbackTiles;

  private final Point globalMidpoint;

  private final int ncols;
  private final ColsToRenderNow colsToRenderNow = new ColsToRenderNow();
  private final BitSet dirtyColumns;

  CommonRenderer(
      LayerSpec spec, double xscale, double yscale, RenderTarget target) {
    this.spec = spec;
    this.xscale = xscale;
    this.yscale = yscale;
    this.target = target;
    mainTiles = spec.mainTiles();
    fallbackTiles = spec.fallbackTiles();
    tilegrid = Toggles.TILEGRID.setIn(spec.flags());

    ncols = target.columns();
    dirtyColumns = new BitSet(ncols);
    dirtyColumns.set(0, ncols);

    globalMidpoint = spec.projection().createWorker().local2global(
        Point.at(target.left()+target.columns()/2,
            target.top()+target.rows()/2));

    this.cacheLookupLevel =
        target.eagerDownload() ? LookupLevel.DOWNLOAD : LookupLevel.DISK;

    markAllForRendering();
  }

  protected final void markAllForRendering() {
    synchronized(colsToRenderNow) {
      colsToRenderNow.first = 0;
      colsToRenderNow.last = ncols - 1;
    }
  }

  protected final boolean anythingMarkedForRendering() {
    // no need to synchronize: reading an int is atomic
    return colsToRenderNow.last >= 0;
  }

  private static class ColsToRenderNow {
    int first, last;
  }

  protected final void oneRenderPass() throws AbortRendering {
    int x, xlast;
    synchronized(colsToRenderNow) {
      x = colsToRenderNow.first;
      xlast = colsToRenderNow.last;
      colsToRenderNow.first = ncols;
      colsToRenderNow.last = -1;
    }
    if( x > xlast ) return;

    double left = target.left() * xscale;
    do {
      currentColumn = x;
      Arrays.fill(localCacheIndex, 0);

      boolean renderResult = renderColumn(x, left + (x+0.5)*xscale,
          0, target.rows()-1, target.top() * yscale);
      if( renderResult )
        dirtyColumns.clear(x);
      target.checkCanceled();
      x++ ;
    } while( x <= xlast );

    Arrays.fill(localCache, null);
    for( NeededTile nt : tileDict.values() ) {
      nt.checkedCache = false;
      nt.midcache = null;
    }

    if( dirtyColumns.isEmpty() )
      target.isNowGrownUp();
  }

  /**
   * return true if all pixels in the column were successfully rendered.
   */
  protected abstract boolean renderColumn(int col, double xmid,
      int ymin, int ymax, double ybase);

  private int currentColumn;

  protected enum LookupLevel { RAM, DISK, DOWNLOAD };
  protected LookupLevel cacheLookupLevel = LookupLevel.DISK;

  protected final int getPixel(Point p, long fallbackSpec) {
    return getPixel(p.x, p.y, fallbackSpec);
  }

  protected final int getPixel(double x, double y, long fallbackSpec) {
    Boolean gridcode = null;
    for(int attempt = 0;; attempt++) {
      int aspec =
          (int)(fallbackSpec >> attempt * BITS_PER_ATTEMPT) & ATTEMPT_MASK;

      if( aspec == 0 ) return RGB.OUTSIDE_BITMAP;
      int zoom = (int)(aspec >> ZOOM_SHIFT);
      if( zoom == 0 ) continue;

      PixelAddresser addresser = addressers[addresserIndex(aspec)];
      if( addresser == null ) {
        addresser = tilesetFor(aspec).makeAddresser(zoom, globalMidpoint);
        if( addresser == null ) throw BadError.of("addresser was null");
        addressers[addresserIndex(aspec)] = addresser;
      }

      long shortcode = addresser.locate(x,y);
      if( shortcode == 0 ) continue;
      if( tilegrid && gridcode == null )
        gridcode = addresser.isOddDownloadTile(shortcode);

      TileBitmap bitmap;
      int lci = cacheSetOf64(aspec, shortcode);
      long wantTag = cacheTag(aspec, shortcode);
      long lciTag = localCacheIndex[lci];
      if( lciTag == wantTag || lciTag == downloadifyCacheTag(wantTag) ) {
        bitmap = localCache[lci];
      } else {
        NeededTile nt = new NeededTile(tilesetFor(aspec), shortcode);
        nt = tileDict.computeIfAbsent(nt, o->o);
        if( nt.checkedCache ) {
          bitmap = nt.midcache;
        } else {
          bitmap = nt.tileset.context.ramCache.getTile(nt,
              cacheLookupLevel != LookupLevel.RAM);
          nt.midcache = bitmap;
          nt.checkedCache = true;
        }
        if( bitmap == null ) {
          if( currentColumn > nt.xmax ) nt.xmax = currentColumn;
          if( currentColumn < nt.xmin ) nt.xmin = currentColumn;
          if( (aspec & DOWNLOAD_BIT) != 0 &&
              cacheLookupLevel == LookupLevel.DOWNLOAD &&
              !addresser.onTileEdge() ) {
            nt.requestDownload();
          } else {
            aspec &= ~DOWNLOAD_BIT;
            nt.watchForDownload();
          }
        }
        localCache[lci] = bitmap;
        localCacheIndex[lci] = cacheTag(aspec, shortcode);
      }
      if( bitmap != null ) {
        int rgb = addresser.getPixel(bitmap);
        if( gridcode != null ) {
          rgb -= (rgb >> 2) & 0x3F3F3F;
          if( gridcode )
            rgb += 0x3F3F3F;
        }
        return rgb;
      }
    }
  }

  private Tileset tilesetFor(int aspec) {
    return (aspec & FALLBACK_BIT) != 0 ? fallbackTiles : mainTiles;
  }

  @Override
  public void dispose() {
    tileDict.values().forEach(NeededTile::cancelSubscriptions);
  }

  // -------------------------------------------------------------------------

  private final PixelAddresser[] addressers = new PixelAddresser[48];

  private static final int LCACHESIZE = 64;

  private final TileBitmap[] localCache = new TileBitmap[LCACHESIZE];
  /**
   * In order to allow different fallback chains in different parts of
   * the download target, the values here are tile shortcodes with the
   * two bottom bit replaced with the bottom bits of the attempt
   * specfification. These two bits are explicitly specified to be
   * redundant in a tile shortcode.
   */
  private final long[] localCacheIndex = new long[LCACHESIZE];

  private final Map<NeededTile, NeededTile> tileDict = new LinkedHashMap<>();

  private class NeededTile extends TileSpec {
    int xmin = Integer.MAX_VALUE;
    int xmax = Integer.MIN_VALUE;

    boolean checkedCache;

    Runnable downloadRequested;
    Runnable downloadWatched;
    TileBitmap midcache;

    public NeededTile(Tileset tileset, long shortcode) {
      super(tileset, shortcode);
    }

    void requestDownload() {
      if( downloadRequested == null ) {
        downloadRequested = request(this::downloadComplete);
      }
    }

    void watchForDownload() {
      if( downloadWatched == null && downloadRequested == null ) {
        downloadWatched = watch(this::downloadComplete);
      }
    }

    void downloadComplete(TileBitmap bitmap) {
      synchronized( this ) {
        if( midcache != null ) return;
        midcache = bitmap;
      }
      synchronized( colsToRenderNow ) {
        if( xmin < colsToRenderNow.first ) colsToRenderNow.first = xmin;
        if( xmax > colsToRenderNow.last ) colsToRenderNow.last = xmax;
      }
      target.pokeSchedulerAsync();
    };

    void cancelSubscriptions() {
      if( downloadRequested != null )
        downloadRequested.run();
      if( downloadWatched != null )
        downloadWatched.run();
    }
  }

}
