package net.makholm.henning.mapwarper.tiles;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.util.BackgroundThread;
import net.makholm.henning.mapwarper.util.BadError;

class TileDownloader extends BackgroundThread {

  final Tileset tileset;
  final TileContext context;
  final TileCache cache;

  private boolean startedYet;
  private final Map<TileSpec, Set<Consumer<TileBitmap>>> queue =
      new LinkedHashMap<>();
  private final Map<TileSpec, Set<Consumer<TileBitmap>>> watchers =
      new LinkedHashMap<>();

  TileDownloader(Tileset tileset) {
    super("Tile downloader "+tileset.name);
    this.tileset = tileset;
    this.context = tileset.context;
    this.cache = context.ramCache;
  }

  public Runnable subscribe(boolean eager,
      TileSpec spec, Consumer<TileBitmap> whenDone) {
    if( spec.tileset != tileset )
      throw BadError.of("Tileset mismatch, %s vs %s", spec.tileset, tileset);
    var map = eager ? queue : watchers;
    synchronized( this ) {
      Set<Consumer<TileBitmap>> subscribers =
          map.computeIfAbsent(spec, spec0 -> new LinkedHashSet<>());
      subscribers.add(whenDone);
      if( eager ) {
        if( startedYet ) {
          notify();
        } else {
          start();
          startedYet = true;
        }
      }
    }
    return () -> {
      synchronized( TileDownloader.this ) {
        Set<Consumer<TileBitmap>> subscribers = map.get(spec);
        if( subscribers != null ) {
          subscribers.remove(whenDone);
          if( subscribers.isEmpty() ) map.remove(spec);
        }
      }
    };
  }

  @Override
  public void run() {
    int backoffSecs = 0;
    for(;;) {
      TileSpec toDownload = null;
      synchronized(this) {
        while( queue.isEmpty() ) {
          try {
            wait();
          } catch (InterruptedException e) {
            scheduleAbort(e, null);
            return;
          }
        }
        long best = Long.MAX_VALUE;
        Point focus = context.downloadFocus;
        var addrr = tileset.makeAddresser(tileset.guiTargetZoom, focus);
        addrr.locate(focus);
        for( TileSpec spec : queue.keySet() ) {
          long priority = addrr.getDownloadPriority(spec.shortcode);
          if( priority <= best ) {
            toDownload = spec;
            best = priority;
          }
        }
        if( queue.get(toDownload).isEmpty() ) {
          queue.remove(toDownload);
          continue;
        }
      }

      // It's possible that it's become possible to simply _load_ the tile
      // while it was waiting in the queue, so try that first.
      TileBitmap got = cache.getTile(toDownload, true);
      if( got == null ) {
        try {
          tileset.downloadTile(toDownload.shortcode, this::downloadCallback);
        } catch( IOException e ) {
          scheduleAbort(e, null);
          return;
        } catch( TryDownloadLater e ) {
          backoffSecs = Math.max(1, backoffSecs * 2);
          backoffSecs = Math.min(3600, backoffSecs);
          System.err.println("Waiting "+backoffSecs+
              " seconds after intermittent failure for "+toDownload);
          e.getCause().printStackTrace();
          // SwingUtils.beep();
          try {
            Thread.sleep(1000 * backoffSecs);
            continue;
          } catch( InterruptedException ee ) {
            scheduleAbort(ee, null);
            return;
          }
        }
        // Note: perhaps we've already scheduled a loading of this particular
        // tile in the parallel loading thread, but that's okay. Invalidating
        // twice is not a problem, because the invalidation only takes effect
        // if the tile _cannot_ be loaded.
        got = cache.invalidateMissingAndGet(toDownload, true);
        if( got == null )
          throw BadError.of("Failed to load %s even after downloading",
              tileset.tilename(toDownload.shortcode));
        backoffSecs = backoffSecs / 2;
      }
      deliverToSubscribers(toDownload, got);
    }
  }

  private void downloadCallback(long tile) {
    System.err.println("    (received "+tileset.tilename(tile)+")");
    context.progressiveLoader.execute(() -> {
      var spec = new TileSpec(tileset, tile);
      boolean anySubscribers;
      synchronized(this) {
        anySubscribers = queue.containsKey(spec) || watchers.containsKey(spec);
      }
      var got = cache.invalidateMissingAndGet(spec, anySubscribers);
      if( got != null )
        deliverToSubscribers(spec, got);
    });
  }

  private void deliverToSubscribers(TileSpec spec, TileBitmap finalGot) {
    Set<Consumer<TileBitmap>> toCall1, toCall2;
    synchronized(this) {
      toCall1 = queue.remove(spec);
      toCall2 = watchers.remove(spec);
    }
    if( toCall1 != null )
      toCall1.forEach(c -> c.accept(finalGot));
    if( toCall2 != null )
      toCall2.forEach(c -> c.accept(finalGot));
  }

}
