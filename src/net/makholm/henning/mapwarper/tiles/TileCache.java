package net.makholm.henning.mapwarper.tiles;

import java.util.Arrays;
import java.util.LinkedHashMap;

import net.makholm.henning.mapwarper.georaster.TileBitmap;

/**
 * A shared RAM cache for tiles from all providers.
 */
public final class TileCache {

  /**
   * Set during batch operations.
   */
  public static boolean alwaysDownloadImmediately;

  public static final byte RAM = 0;
  public static final byte DISK = 1;
  public static final byte DOWNLOAD = 2;

  // By default, use one third of the Java heap for tiles
  public long maxBytes = Runtime.getRuntime().maxMemory() / 3;

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

  public TileBitmap getTile(TileSpec spec, byte howFarToLook) {
    Entry e;
    TileBitmap result;
    synchronized( TileCache.this ) {
      e = map.get(spec);
      if( e == null ) {
        if( howFarToLook <= RAM )
          return null;
        perhapsDiscardSomeEntries();
        e = new Entry(spec);
        map.put(spec, e);
        totalBytes += e.cost;
      }
      if( alwaysDownloadImmediately )
        howFarToLook = DOWNLOAD;
      e.lruStamp = stampcounter++;
      if( howFarToLook > e.requested )
        e.requested = howFarToLook;
      if( e.achievedGlobal >= howFarToLook )
        return e.bitmap;
    }
    synchronized( e ) {
      if( e.achievedLocal >= howFarToLook )
        return e.bitmap;
      boolean allowDownload = howFarToLook >= DOWNLOAD;
      result = e.spec.tileset.loadTile(e.spec.tile(), allowDownload);
      if( result != null ) {
        e.bitmap = result;
        howFarToLook = Byte.MAX_VALUE;
      }
      e.achievedLocal = howFarToLook;
    }
    synchronized( TileCache.this ) {
      if( howFarToLook > e.achievedGlobal )
        e.achievedGlobal = howFarToLook;
      if( result != null ) {
        long bitmapSize = 4 * result.numPixels;
        e.cost += bitmapSize;
        totalBytes += bitmapSize;
        perhapsDiscardSomeEntries();
      }
    }
    return result;
  }

  // -------------------------------------------------------------------------

  private long stampcounter;
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
  private final class Entry {
    final TileSpec spec;

    Entry(TileSpec spec) {
      this.spec = spec;
      totalBytes += cost;
    }

    // These fields belong to the TileCache lock:
    long lruStamp;
    byte requested = RAM;
    byte achievedGlobal = RAM;

    long cost = 100;

    // These fields belong to the lock on the Entry itself:
    byte achievedLocal = RAM;
    TileBitmap bitmap;
  }

  /** Called with the global lock held */
  private void perhapsDiscardSomeEntries() {
    if( totalBytes < maxBytes )
      return;
    System.err.println("Evicting time tiles, since "+totalBytes+
        " used, and the limit is "+maxBytes);
    long target = maxBytes - (maxBytes >> 4);
    int checkedCandidates = 0;
    int evictedCandidates = 0;
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
      } else if( e.requested > e.achievedGlobal ) {
        // There's a thread elsewhere working on this entry.
        // Even if is is not recently used (huh, what's up with that?)
        // it's unsafe to forget it just now.
      } else {
        evictedCandidates++;
        map.remove(e.spec);
        totalBytes -= e.cost;
      }
    }
    System.err.println("  After evicting "+evictedCandidates+" of "+
        checkedCandidates+" candidates, we're using "+totalBytes+" bytes. " +
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