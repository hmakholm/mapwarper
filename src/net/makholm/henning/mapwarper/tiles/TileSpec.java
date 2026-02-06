package net.makholm.henning.mapwarper.tiles;

import java.util.function.Consumer;

import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.util.LongHashed;

public class TileSpec extends LongHashed {

  public final Tileset tileset;
  public final long shortcode;

  public TileSpec(Tileset tileset, long shortcode) {
    this.tileset = tileset;
    this.shortcode = shortcode;
  }

  @Override
  protected final long longHashImpl() {
    long h = System.identityHashCode(tileset);
    h = hashStep(h);
    h ^= shortcode;
    return h;
  }

  @Override
  public final boolean equals(Object o) {
    return o instanceof TileSpec other &&
        other.tileset == tileset &&
        other.shortcode == shortcode;
  }

  public Runnable request(Consumer<TileBitmap> callback) {
    return tileset.downloader.subscribe(true, this, callback);
  }

  public Runnable watch(Consumer<TileBitmap> callback) {
    return tileset.downloader.subscribe(false, this, callback);
  }

  @Override
  public final String toString() {
    return tileset.tilename(shortcode);
  }

}
