package net.makholm.henning.mapwarper.tiles;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.util.BackgroundThread;

public class TileDownloader {

  private final Map<Tileset, DownloadThread> threads = new LinkedHashMap<>();

  private volatile Point focus;

  public void offerMousePosition(Point global) {
    focus = global;
  }

  /**
   * @return a {@link Runnable} that will cancel the request
   */
  public Runnable request(TileSpec spec, Consumer<TileBitmap> whenDone) {
    DownloadThread dt;
    synchronized(threads) {
      dt = threads.computeIfAbsent(spec.tileset, DownloadThread::new);
    }
    return dt.subscribe(dt.queue, spec, whenDone);
  }

  public Runnable watch(TileSpec spec, Consumer<TileBitmap> whenDone) {
    DownloadThread dt;
    synchronized(threads) {
      dt = threads.computeIfAbsent(spec.tileset, DownloadThread::new);
    }
    return dt.subscribe(dt.watchers, spec, whenDone);
  }

  private class DownloadThread extends BackgroundThread {
    final TileCache cache ;
    final Tileset tileset;

    final Map<TileSpec, Set<Consumer<TileBitmap>>> queue =
        new LinkedHashMap<>();
    final Map<TileSpec, Set<Consumer<TileBitmap>>> watchers =
        new LinkedHashMap<>();

    DownloadThread(Tileset tileset) {
      super("Tile downloader "+tileset.name);
      this.tileset = tileset;
      this.cache = tileset.context.ramCache;
      start();
    }

    private Runnable subscribe(Map<TileSpec, Set<Consumer<TileBitmap>>> map,
        TileSpec spec, Consumer<TileBitmap> whenDone) {
      synchronized( this ) {
        Set<Consumer<TileBitmap>> subscribers =
            map.computeIfAbsent(spec, spec0 -> new LinkedHashSet<>());
        subscribers.add(whenDone);
        if( map == queue ) notify();
      }
      return () -> {
        synchronized( DownloadThread.this ) {
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
          Point focus = TileDownloader.this.focus;
          var addrr = tileset.makeAddresser(tileset.guiTargetZoom(), focus);
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

        // Make sure we try to load from disk first; otherwise another render
        // thread that just wanted to check the disk risks blocking on _this_
        // thread actually downloading.
        // (There's still a race where this can happen, unfortunately,
        // but hopefully it's rare).
        TileBitmap got;
        try {
          got = cache.getTile(toDownload, TileCache.DISK);
          if( got == null ) {
            got = cache.getTile(toDownload, TileCache.DOWNLOAD);
            if( got != null )
              backoffSecs = backoffSecs / 2;
          }
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

        Set<Consumer<TileBitmap>> toCall1, toCall2;
        synchronized(this) {
          toCall1 = queue.remove(toDownload);
          toCall2 = watchers.remove(toDownload);
        }
        var finalGot = got;
        if( toCall1 != null )
          toCall1.forEach(c -> c.accept(finalGot));
        if( toCall2 != null )
          toCall2.forEach(c -> c.accept(finalGot));
      }
    }

  }

}
