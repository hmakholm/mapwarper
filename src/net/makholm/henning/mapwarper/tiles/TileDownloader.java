package net.makholm.henning.mapwarper.tiles;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.util.BackgroundThread;
import net.makholm.henning.mapwarper.util.MathUtil;

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

    final Map<TileSpec, Set<Consumer<TileBitmap>>> queue =
        new LinkedHashMap<>();
    final Map<TileSpec, Set<Consumer<TileBitmap>>> watchers =
        new LinkedHashMap<>();

    DownloadThread(Tileset tileset) {
      super("Tile downloader "+tileset.name);
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
    protected void runInner() {
      for(;;) {
        TileSpec toDownload = null;
        synchronized(this) {
          while( queue.isEmpty() ) {
            try {
              wait();
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
          int bestSize = -1;
          double bestSqdist = -1;
          Point focus = TileDownloader.this.focus;
          for( TileSpec spec : queue.keySet() ) {
            int size = Long.numberOfTrailingZeros(spec.shortcode);
            double sqdist =
                MathUtil.sqr(Coords.x(spec.shortcode) - focus.x) +
                MathUtil.sqr(Coords.y(spec.shortcode) - focus.y);
            if( size > bestSize || size == bestSize && sqdist < bestSqdist ) {
              toDownload = spec;
              bestSize = size;
              bestSqdist = sqdist;
            }
          }
          if( queue.get(toDownload).isEmpty() ) {
            queue.remove(toDownload);
            continue;
          }
        }

        // Make sure we try to load from disk first; otherwise other a render
        // thread that just wanted to check the disk risks blocking on _this_
        // thread actually downloading.
        // (There's still a race where this can happen, unfortunately,
        // but hopefully it's rare).
        TileBitmap got = cache.getTile(toDownload, TileCache.DISK);
        if( got == null )
          got = cache.getTile(toDownload, TileCache.DOWNLOAD);
        synchronized(this) {
          var finalGot = got;
          var subscribers = queue.remove(toDownload);
          if( subscribers != null )
            subscribers.forEach(c -> c.accept(finalGot));
          subscribers = watchers.remove(toDownload);
          if( subscribers != null )
            subscribers.forEach(c -> c.accept(finalGot));
        }
      }
    }

  }

}
