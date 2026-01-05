package net.makholm.henning.mapwarper.gui.maprender;

import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.Tile;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.tiles.TileCache;
import net.makholm.henning.mapwarper.tiles.TileSpec;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.util.AbortRendering;

abstract class CommonRenderer implements RenderWorker {

  protected final LayerSpec spec;
  protected final double xscale;
  protected final double yscale;
  protected final RenderTarget target;
  protected final int tilegridMask;

  private final Tileset mainTiles;
  private final Tileset fallbackTiles;

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
    tilegridMask = Toggles.TILEGRID.setIn(spec.flags()) ?
        (Coords.EARTH_SIZE >> 18) : 0;

    ncols = target.columns();
    dirtyColumns = new BitSet(ncols);
    dirtyColumns.set(0, ncols);

    this.cacheLookupLevel =
        target.eagerDownload() ? TileCache.DOWNLOAD : TileCache.DISK;

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
  protected byte cacheLookupLevel = TileCache.DISK;

  protected final int getPixel(Point p, long fallbackSpec) {
    return getPixel(Coords.point2pixcoord(p), fallbackSpec);
  }

  protected final int getPixel(double x, double y, long fallbackSpec) {
    return getPixel(Coords.point2pixcoord(x, y), fallbackSpec);
  }

  protected final int getPixel(long coords, long fallbackSpec) {
    for(int attempt = 0;; attempt++) {
      long aspec = (fallbackSpec >> attempt * FallbackChain.BITS_PER_ATTEMPT);
      if( aspec == 0 ) return RGB.OUTSIDE_BITMAP;
      int zoom = (int)aspec & FallbackChain.ZOOM_MASK;
      if( zoom == 0 ) continue;

      TileBitmap bitmap;
      long shortcode = Tile.codedContaining(coords, zoom);
      long shifted = shortcode >> (Coords.BITS - zoom);
      int lci = (attempt << 2) + ((int)shifted & 1) + ((int)(shifted >> 31) & 2);
      if( localCacheIndex[lci] == shortcode ) {
        bitmap = localCache[lci];
      } else {
        NeededTile nt = new NeededTile(
            (aspec & FallbackChain.FALLBACK_BIT) != 0
            ? fallbackTiles : mainTiles,
                shortcode);
        nt = tileDict.computeIfAbsent(nt, x->x);
        if( nt.checkedCache ) {
          bitmap = nt.midcache;
        } else {
          bitmap = nt.midcache =
              nt.tileset.context.ramCache.getTile(nt, cacheLookupLevel);
          nt.checkedCache = true;
        }
        if( bitmap == null ) {
          if( currentColumn > nt.xmax ) nt.xmax = currentColumn;
          if( currentColumn < nt.xmin ) nt.xmin = currentColumn;
          if( (aspec & FallbackChain.DOWNLOAD_BIT) != 0 &&
              cacheLookupLevel == TileCache.DISK )
            nt.requestDownload();
          else
            nt.watchForDownload();
        }
        localCache[lci] = bitmap;
        localCacheIndex[lci] = shortcode;
      }
      if( bitmap != null ) {
        int rgb = bitmap.pixelAt(coords);
        if( tilegridMask != 0 ) {
          rgb -= (rgb >> 2) & 0x3F3F3F;
          if( ((coords ^ (coords >> 32)) & tilegridMask) != 0 )
            rgb += 0x3F3F3F;
        }
        return rgb;
      }
    }
  }

  @Override
  public void dispose() {
    synchronized(this) {
      tileDict.values().forEach(NeededTile::cancelSubscriptions);
    }
  }

  // -------------------------------------------------------------------------

  private static final int LCACHESIZE = FallbackChain.MAX_ATTEMPTS * 4;

  private final TileBitmap[] localCache = new TileBitmap[LCACHESIZE];
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
        downloadRequested =
            tileset.context.downloader.request(this, this::downloadComplete);
      }
    }

    void watchForDownload() {
      if( downloadWatched == null && downloadRequested == null ) {
        downloadWatched =
            tileset.context.downloader.watch(this, this::downloadComplete);
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
