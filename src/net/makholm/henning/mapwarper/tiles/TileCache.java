package net.makholm.henning.mapwarper.tiles;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;

import net.makholm.henning.mapwarper.georaster.TileBitmap;

/**
 * A shared RAM cache for tiles from all providers.
 */
public final class TileCache {

  // By default, use one fifth of the Java heap for tiles.
  // For some reason the pixel arrays seem to collectively use up to
  // three times their natural size, according to the heap sizes
  // measured by Runtime, so we'll need to make room for plenty
  // of slack ...
  public long maxBytes = Runtime.getRuntime().maxMemory() / 5;

  public void setMaxBytes(long bytes) {
    synchronized( TileCache.this ) {
      maxBytes = bytes;
      perhapsDiscardSomeEntries();
    }
  }

  public boolean isEmpty() {
    synchronized( TileCache.this ) {
      return map.isEmpty();
    }
  }

  public TileBitmap getTile(TileSpec spec, boolean forceLoading) {
    return getInternal(false, spec, forceLoading);
  }

  TileBitmap invalidateMissingAndGet(TileSpec spec, boolean forceLoading) {
    return getInternal(true, spec, forceLoading);
  }

  private TileBitmap getInternal(boolean invalidateMissing,
      TileSpec spec, boolean forceLoading) {
    Entry e;
    long stamp, tryAgainStamp;
    synchronized( TileCache.this ) {
      e = map.get(spec);
      if( e == null ) {
        if( !forceLoading )
          return null;
        perhapsDiscardSomeEntries();
        e = new Entry(spec);
        map.put(spec, e);
        totalBytes += e.cost;
      }

      e.lruStamp = stamp = stampcounter++;
      if( invalidateMissing )
        e.tryAgainStamp = tryAgainStamp = stamp;
      else
        tryAgainStamp = e.tryAgainStamp;

      if( !forceLoading ||
          e.bitmapGlobal != null ||
          e.failedStampGlobal >= tryAgainStamp )
        return e.bitmapGlobal;

      e.loadingThreadsCount++;
    }
    TileBitmap result;
    boolean triedLoading = false;
    synchronized( e ) {
      if( e.bitmapLocal != null ) {
        result = e.bitmapLocal;
      } else if( e.failedStampLocal >= tryAgainStamp ) {
        result = null;
      } else {
        try {
          result = spec.tileset.loadTile(e.spec.shortcode);
        } catch( IOException ex ) {
          System.err.println("Failed to load "+spec+": "+ex);
          // This is not bad enough to schedule an abort for; perhaps
          // re-downloading will fix it after all.
          result = null;
        }
        e.bitmapLocal = result;
        triedLoading = true;
        if( result == null && stamp > e.failedStampLocal )
          e.failedStampLocal = stamp;
      }
    }
    synchronized( TileCache.this ) {
      e.loadingThreadsCount--;
      if( triedLoading ) {
        if( result != null ) {
          e.bitmapGlobal = result;
          long bitmapSize = 4 * result.numPixels;
          e.cost += bitmapSize;
          totalBytes += bitmapSize;
          perhapsDiscardSomeEntries();
        } else {
          if( stamp > e.failedStampGlobal )
            e.failedStampGlobal = stamp;
        }
      }
    }
    return result;
  }
  // -------------------------------------------------------------------------

  private long stampcounter = 1;
  private long totalBytes;

  private LinkedHashMap<TileSpec, Entry> map = new LinkedHashMap<>();

  private Entry[] evictionCandidates = new Entry[0];
  private int nextToEvict = 0;

  private long highestEvictableStamp;

  /**
   * The entry objects serve both to store RAM cached tiles themselves,
   * and to coordinate the promise to sequence attempts to load/download
   * each tile with respect to each other.
   */
  private static final class Entry {
    final TileSpec spec;

    Entry(TileSpec spec) {
      this.spec = spec;
    }

    // These fields belong to the TileCache lock:
    long lruStamp;
    long cost = 100;
    TileBitmap bitmapGlobal;
    long tryAgainStamp;
    long failedStampGlobal = -1;
    int loadingThreadsCount;

    // These fields belong to the lock on the Entry itself:
    TileBitmap bitmapLocal;
    long failedStampLocal = -1;
  }

  public synchronized void clear() {
    long savedMax = maxBytes;
    maxBytes=1;
    perhapsDiscardSomeEntries();
    maxBytes = savedMax;
  }

  /** Called with the global lock held */
  private void perhapsDiscardSomeEntries() {
    if( totalBytes < maxBytes )
      return;
    System.err.println("Evicting some tiles, since "+totalBytes+
        " used, and the limit is "+maxBytes);
    long target = maxBytes - (maxBytes >> 4);
    int checkedCandidates = 0;
    int evictedCandidates = 0;
    int evictedHadBitmap = 0;
    boolean rebuiltListOnce = false;
    while(totalBytes > target) {
      if( nextToEvict >= evictionCandidates.length ) {
        if( rebuiltListOnce || map.isEmpty() ) break;
        rebuildCandidateList();
        rebuiltListOnce = true;
      }

      Entry e = evictionCandidates[nextToEvict];
      evictionCandidates[nextToEvict] = null;
      nextToEvict++;

      checkedCandidates++ ;
      if( e.lruStamp > highestEvictableStamp ) {
        // This entry has become more recently used; skip it
      } else if( e.loadingThreadsCount > 0 ) {
        // There's a thread elsewhere working on this entry.
        // Even if is is not recently used (huh, what's up with that?)
        // it's unsafe to forget it just now.
      } else {
        if( e.bitmapGlobal != null ) evictedHadBitmap++;
        evictedCandidates++;
        map.remove(e.spec);
        totalBytes -= e.cost;
      }
    }
    System.err.println("  After evicting "+evictedCandidates+" of "+
        checkedCandidates+" candidates, ("+evictedHadBitmap+" of which "+
        "had a bitmap), we're using "+totalBytes+" bytes. " +
        map.size() + " tiles still cached.");
  }

  /** Called with the global lock held */
  private void rebuildCandidateList() {
    int size = map.size();
    evictionCandidates = map.values().toArray(new Entry[size]);
    Arrays.sort(evictionCandidates, (a,b)
        -> Long.compare(a.lruStamp, b.lruStamp));
    highestEvictableStamp = stampcounter++;
    nextToEvict = 0;
  }

}