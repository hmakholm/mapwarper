package net.makholm.henning.mapwarper.gui.maprender;

import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.projection.Affinoid;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.util.MathUtil;

public class FallbackChain {

  public static final int DOWNLOAD_BIT = 0x01;
  public static final int FALLBACK_BIT = 0x02;
  public static final int ZOOM_SHIFT   = 2;
  public static final int BITS_PER_ATTEMPT = 7;
  public static final int MAX_ATTEMPTS     = 9;

  public static final int ATTEMPT_MASK = (1 << BITS_PER_ATTEMPT)-1;

  public static int addresserIndex(int maskedAttempt) {
    return maskedAttempt >> 1;
  }

  /**
   * Combine the index lowest bits in {@code shortcode} (see {@link
   * PixelAddresser#locate(double, double)}) with low-order bits from
   * the zoom level as well at the fallback bit to create a set index
   * for the local cache in CommonRenderer.
   */
  public static int cacheSetOf64(int maskedAttempt, long shortcode) {
    // It's assumed that we won't have a fallback chain where the zoom
    // levels differ by more than 8!
    return ((int)shortcode & 3) // low-order bits
        | ((maskedAttempt & 0x1E) << 1); // fallback bit and 3 bits of zoom
  }

  public static long cacheTag(int maskedAttempt, long shortcode) {
    return (shortcode & ~3L) + (maskedAttempt & 3);
  }

  public static long downloadifyCacheTag(long cacheTag) {
    return cacheTag | DOWNLOAD_BIT;
  }

  // -------------------------------------------------------------------------

  public final Tileset mainTiles, fallbackTiles;

  public int principalZoom;

  /**
   * These are the <em>intended</em> tiles that should be rendered with
   * supersampling in projections where that makes sense.
   */
  public final long premiumChain;

  /**
   * Tiles that can also be used in general but are already lower
   * quality and don't justify the extra work of supersampling.
   */
  public final long standardChain;

  /**
   * A reduced chain that minimizes download work while still being
   * minimally recognizable; used outside of the defined bounds in
   * warped projections.
   */
  public final long marginChain;

  public FallbackChain(LayerSpec spec) {
    this(spec, spec.projection().getAffinoid());
  }

  public FallbackChain(LayerSpec spec, Affinoid aff) {
    this(spec, aff.scaleAlong(), aff.scaleAcross);
  }

  public FallbackChain(LayerSpec spec, double pixsizex, double pixsizey) {
    mainTiles = spec.mainTiles();
    fallbackTiles = spec.fallbackTiles();
    var flags = spec.flags();
    var naturalZoom = naturalZoom(pixsizey);

    boolean suppressDownload = spec.projection().base()
        .suppressMainTileDownload(pixsizex/pixsizey);

    int mainZoom = Math.min(naturalZoom, mainTiles.guiTargetZoom);

    long draftPremiumChain = 0;
    long draftStandardChain = 0;
    long marginChainMask = -1;

    final boolean wantNiceFallback;

    // MAIN TILES

    if( Toggles.OVERLAY_MAP.setIn(flags) ) {
      // This is for the lens, where we don't bother with fallbacks
      mainZoom = spec.targetZoom();
      draftPremiumChain |= nextAttempt(mainTiles, true, mainZoom);
      for( int i=1; i<=5; i++ )
        draftStandardChain |= nextAttempt(mainTiles, false, mainZoom-i);
      premiumChain = draftPremiumChain;
      standardChain = marginChain = draftPremiumChain | draftStandardChain;
      return;

    } else if( mainTiles == fallbackTiles ) {
      // Another special case: if main and fallback are the same, then
      // don't bother to make them two separate sequences; instead rely
      // on the existing support we have for making the fallback map
      // look good.
      wantNiceFallback = true;

    } else if( !suppressDownload && Toggles.DOWNLOAD.setIn(flags) ) {
      // This is the common case.
      wantNiceFallback = false;

      if( pixsizex >= 3*pixsizey &&
          mainTiles.guiTargetZoom > mainZoom &&
          mainTiles.guiTargetZoom <= mainZoom+5 ) {
        // A special case to avoid lots of sudden downloads when one zooms
        // out from squeezed projection: Try falling back to the tileset's
        // principal resolution _before_ downloading the one corresponding
        // to the display zoom.
        draftPremiumChain |= nextAttempt(mainTiles, false, mainZoom);
        draftPremiumChain |= nextAttempt(mainTiles, false, mainTiles.guiTargetZoom);
        marginChainMask = ~draftPremiumChain;
      }
      draftPremiumChain |= nextAttempt(mainTiles, true, mainZoom);
      draftStandardChain |= additionalMainAttempts(mainZoom);

    } else if( Toggles.hasDebugZoom(flags) && !Toggles.DOWNLOAD.setIn(flags) ) {
      // The tilecache debug no-download mode, where we want only one
      // particular main-tile zoom.
      draftPremiumChain |= nextAttempt(mainTiles, false, Toggles.debugZoom(flags));
      wantNiceFallback = true;

    } else if( mainZoom < mainTiles.coarsestZoom && !suppressDownload ) {
      // We're explicitly (rather than implicitly by quickwarp) in no-download
      // mode, but zoomed farther out than the tileset can deliver.
      // Extraordinarily allow finer zooms to be used then.
      if( mainZoom >= mainTiles.coarsestZoom-2 )
        draftPremiumChain |= nextAttempt(mainTiles, false, mainTiles.coarsestZoom);
      wantNiceFallback = true;

    } else {
      // The ordinary no-download mode, enabled by force in quickwarp projections.
      // Here we want a best-effort attempt to show _something_ from the main
      // tileset, but without needing to download anything.
      draftPremiumChain |= nextAttempt(mainTiles, false, mainZoom);
      if( mainTiles.guiTargetZoom > mainZoom &&
          mainTiles.guiTargetZoom <= mainZoom+2 ) {
        long better = nextAttempt(mainTiles, false, mainTiles.guiTargetZoom);
        draftPremiumChain |= better;
        marginChainMask = ~better;
        principalZoom = mainTiles.guiTargetZoom;
      }
      draftStandardChain |= additionalMainAttempts(mainZoom);

      // When we don't have anything downloaded, be sure to aim for a
      // full-resolution fallback map.
      wantNiceFallback = true;
    }
    long draftMarginChain = neverDownload(
        (draftPremiumChain | draftStandardChain) & marginChainMask);

    // FALLBACK TILES

    /**
     * The best zoom where there are as many fallback pixels as there are
     * display pixels, given the squeeze.
     */
    int naturalFallbackZoom = naturalZoom(Math.sqrt(pixsizex*pixsizey));
    /**
     * The zoom where we have zoomed so far out that the default 256x256
     * pixel tile would be larger than the window.
     */
    int stopDownloadZoom = clampZoom((int)MathUtil.log2(
        Coords.EARTH_SIZE / spec.windowDiagonal().getAsDouble()));

    int fallbackZoom = Math.min(naturalZoom, fallbackTiles.guiTargetZoom);
    int firstStdDownload = Math.min(fallbackZoom-2, naturalFallbackZoom);
    int nextDownload = wantNiceFallback ? fallbackZoom : firstStdDownload;
    int firstMarginDownload = naturalFallbackZoom-2;

    for(; numAttempts < MAX_ATTEMPTS && fallbackZoom >= 1; fallbackZoom-- ) {
      if( numAttempts == MAX_ATTEMPTS-1 ) {
        fallbackZoom = fallbackDownload(fallbackZoom);
        if( fallbackZoom >= naturalFallbackZoom )
          nextDownload = fallbackZoom = fallbackDownload(naturalFallbackZoom);
      }
      long attempt;
      if( fallbackZoom <= nextDownload ) {
        attempt = nextAttempt(fallbackTiles, true, fallbackZoom);
        if( fallbackZoom > firstStdDownload )
          nextDownload = firstStdDownload;
        if( fallbackZoom <= stopDownloadZoom )
          nextDownload = -1;
        else
          nextDownload = fallbackDownload(fallbackZoom-2);
        if( draftPremiumChain == 0 )
          draftPremiumChain |= attempt;
      } else {
        attempt = nextAttempt(fallbackTiles, false, fallbackZoom);
      }
      if( fallbackZoom <= firstMarginDownload )
        draftMarginChain |= attempt;
      else
        draftStandardChain |= attempt;
    }

    // PUTTING IT ALL TOGETHER

    premiumChain = draftPremiumChain;
    standardChain = draftPremiumChain | draftStandardChain | draftMarginChain;
    marginChain = neverDownload(draftStandardChain) | draftMarginChain;
    //    System.out.println("Premium chain:  "+toString(premiumChain));
    //    System.out.println("Standard chain: "+toString(standardChain));
    //    System.out.println("Margin chain:   "+toString(marginChain)+" (threshold "+firstMarginDownload+")");
  }

  private int numAttempts;

  private long nextAttempt(Tileset tiles, boolean download, int zoom) {
    if( zoom < tiles.coarsestZoom || zoom > tiles.finestZoom )
      return 0; // and _don't_ increment numAttempts

    if( principalZoom == 0 && tiles == mainTiles )
      principalZoom = zoom;

    long val = (zoom << ZOOM_SHIFT) & ATTEMPT_MASK;
    if( tiles != mainTiles ) val |= FALLBACK_BIT;
    if( download ) val |= DOWNLOAD_BIT;
    val <<= numAttempts * BITS_PER_ATTEMPT;
    numAttempts++;
    return val;
  }

  private long additionalMainAttempts(int mainZoom) {
    long result = 0;
    for( int i=1; i<=3 && numAttempts < (MAX_ATTEMPTS+1)/2; i++ )
      result |= nextAttempt(mainTiles, false, mainZoom-i);
    return result;
  }

  private static int fallbackDownload(int zoom) {
    return zoom & ~1;
  }

  public static long neverDownload(long chain) {
    for( long bit = DOWNLOAD_BIT; bit != 0; bit <<= BITS_PER_ATTEMPT )
      chain &= ~bit;
    return chain;
  }

  public static String toString(long v) {
    if( v == 0 ) return "(no tiles at all)";
    StringBuilder sb = new StringBuilder();
    for(;;) {
      boolean download = (v & DOWNLOAD_BIT) != 0;
      boolean fallback = (v & FALLBACK_BIT) != 0;
      int zoom = ((int)(v & ATTEMPT_MASK) >> ZOOM_SHIFT);

      if( !download ) sb.append('(');
      if( zoom == 0 )
        sb.append("--");
      else
        sb.append('z').append(zoom);
      if( fallback ) sb.append('f');
      if( !download ) sb.append(')');

      v = (v >>> BITS_PER_ATTEMPT);
      if( v == 0 ) return sb.toString();
      sb.append(' ');
    }
  }

  public static int naturalZoom(double pixsize) {
    // Math.getExponent rounds down, which is what we want: better
    // map pixels that are slightly smaller than display pixels than
    // the other way around.
    int logPixsize = Math.getExponent(pixsize);
    int zoom = Coords.logPixsize2zoom(logPixsize);
    return clampZoom(zoom);
  }

  private static int clampZoom(int zoom) {
    if( zoom > 31 ) return 31;
    else if( zoom < 1 ) return 1;
    else return zoom;
  }

}
