package net.makholm.henning.mapwarper.gui.maprender;

import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
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

  private final int targetZoom;

  private final int mainNaturalZoom;
  private final int fallbackNaturalZoom;
  private final int fallbackMinDownload;
  private final int fallbackMinUse;
  private final int fallbackTooCloseZoom;

  private final boolean fallbackEqualsMain;

  private final int mainZoomToUse;

  private long accumulatedBits;
  private int numAttempts;

  public FallbackChain(LayerSpec spec, double pixsizex, double pixsizey) {
    pixsizex = Math.abs(pixsizex);
    pixsizey = Math.abs(pixsizey);
    var mainTiles = spec.mainTiles();
    var fallbackTiles = spec.fallbackTiles();

    this.targetZoom = spec.targetZoom();
    this.mainNaturalZoom = naturalZoom(pixsizey, mainTiles);
    if( fallbackTiles.name.equals("nomap") ) {
      fallbackEqualsMain = false;
      fallbackNaturalZoom = 1;
      fallbackMinDownload = 1;
      fallbackMinUse = 1;
      fallbackTooCloseZoom = 2;
    } else {
      fallbackEqualsMain = fallbackTiles == mainTiles;
      fallbackNaturalZoom = Math.min(fallbackTiles.guiTargetZoom,
          naturalZoom(pixsizey, fallbackTiles));

      // fallbackMinDownload is when we have zoomed so far out that the
      // tile size is larger than the window.
      fallbackMinDownload = clampZoom((int)MathUtil.log2(
          Coords.EARTH_SIZE / spec.windowDiagonal().getAsDouble()));

      // fallbackMinUse is when we have zoomed so far out that a fallback
      // pixel stretches for more than 20 UI pixels in the Y direction
      fallbackMinUse = clampZoom((int)MathUtil.log2(
          Coords.EARTH_SIZE / (pixsizey * 20)) - 8);

      // fallbackTooCloseZoom is when we have zoomed so far in that there
      // are more fallback pixels than there are pixels to display
      fallbackTooCloseZoom = naturalZoom(Math.sqrt(pixsizex*pixsizey),
          fallbackTiles);
    }

    mainZoomToUse = Math.min(targetZoom, mainNaturalZoom);
  }

  public void addAttempt(int zoom, boolean fallback, boolean download) {
    long val = (zoom << ZOOM_SHIFT) & ATTEMPT_MASK;
    if( fallback ) val |= FALLBACK_BIT;
    if( download ) val |= DOWNLOAD_BIT;
    accumulatedBits |= val << (numAttempts * BITS_PER_ATTEMPT);
    numAttempts++;
  }

  public void attemptMain() {
    addAttempt(mainZoomToUse, false, true);
  }

  public long supersampleMain(boolean downloadWhenSupersampling) {
    long result;
    if( targetZoom > mainZoomToUse && targetZoom <= mainZoomToUse+5 ) {
      addAttempt(mainZoomToUse, false, false);
      long saved = accumulatedBits;
      addAttempt(targetZoom, false, false);
      if( downloadWhenSupersampling )
        addAttempt(mainZoomToUse, false, true);
      result = accumulatedBits;
      accumulatedBits = saved;
    } else {
      if( downloadWhenSupersampling ) {
        addAttempt(mainZoomToUse, false, true);
        result = accumulatedBits;
        accumulatedBits = 0;
        addAttempt(mainZoomToUse, false, false);
      } else {
        addAttempt(mainZoomToUse, false, false);
        result = accumulatedBits;
      }
    }
    return result;
  }

  public void attemptFallbacks(int mainSizesToInclude) {
    int fallbackZoom;
    if( fallbackEqualsMain ) {
      fallbackZoom = mainZoomToUse-1;
    } else {
      fallbackZoom = fallbackNaturalZoom;
      for( int i = 0; i<mainSizesToInclude; i++ ) {
        int z = mainZoomToUse - 1 - i;
        if( z < 0 ) break;
        addAttempt(z, false, false);
      }
    }
    if( fallbackZoom >= fallbackTooCloseZoom )
      fallbackZoom = fallbackTooCloseZoom-1;
    boolean stopDownloading = false;
    while( numAttempts < MAX_ATTEMPTS &&
        fallbackZoom >= 1 ) {
      boolean download =
          !stopDownloading &&
          fallbackZoom <= fallbackNaturalZoom-2 &&
          fallbackZoom <= targetZoom-2 &&
          (fallbackZoom % 2) == 1;
      if( download && fallbackZoom <= fallbackMinDownload )
        stopDownloading = true;
      addAttempt(fallbackZoom, true, download);
      if( stopDownloading && fallbackZoom < fallbackMinUse  )
        break;
      fallbackZoom--;
    }
  }

  public long weakChain(int maxRelativeShrink) {
    if( mainZoomToUse >= targetZoom - maxRelativeShrink )
      addAttempt(targetZoom, false, false);
    else
      addAttempt(mainZoomToUse, false, false);
    addAttempt(fallbackNaturalZoom, true, true);
    attemptFallbacks(0);
    return accumulatedBits;
  }

  public long lensChain() {
    attemptMain();
    return accumulatedBits;
  }

  public static long neverDownload(long chain) {
    for( long bit = DOWNLOAD_BIT; bit != 0; bit <<= BITS_PER_ATTEMPT )
      chain &= ~bit;
    return chain;
  }

  public void downloadTheFirstFallback() {
    for( int i = 0; i < numAttempts; i++ ) {
      int shift = i * BITS_PER_ATTEMPT;
      if( ((accumulatedBits >> shift) & FALLBACK_BIT) != 0 ) {
        accumulatedBits |= DOWNLOAD_BIT << shift;
        return;
      }
    }
  }

  public long getChain() {
    return accumulatedBits;
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

      v >>>= BITS_PER_ATTEMPT;
      if( v == 0 ) return sb.toString();
      sb.append(' ');
    }
  }

  public static int naturalZoom(double pixsize, Tileset tiles) {
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
