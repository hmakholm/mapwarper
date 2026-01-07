package net.makholm.henning.mapwarper.tiles;

import net.makholm.henning.mapwarper.georaster.Tile;
import net.makholm.henning.mapwarper.util.LongHashed;

public class TileSpec extends LongHashed {

  public final Tileset tileset;
  public final long shortcode;

  public TileSpec(Tileset tileset, long shortcode) {
    this.tileset = tileset;
    this.shortcode = shortcode;
  }

  public Tile tile() {
    return Tile.of(shortcode);
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

  @Override
  public final String toString() {
    return tileset.name + ":" + tile();
  }

}
