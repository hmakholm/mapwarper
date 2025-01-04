package net.makholm.henning.mapwarper.georaster;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.LyngHash;

/**
 * This immutable class represent the abstract location and of a tile
 * in the global coordinate system.
 */
public final class Tile extends GeoRect {

  public final int zoom, tilex, tiley;

  /**
   * The shortcode of a tile is the wrapped coordinates of its midpoint.
   * This implicitly encodes the size of the tile too.
   *
   * This is used to speed up comparison of tiles in hot per-pixel loops;
   */
  public final long shortcode;

  /**
   * Private constructor -- the caller must guarantee that the
   * parameters are consistent.
   */
  private Tile(int north, int west, int size) {
    super(north, north+size, west+size, west);
    this.shortcode = midpoint();
    int shift = Integer.numberOfTrailingZeros(size);
    this.tilex = west >> shift;
    this.tiley = north >> shift;
    this.zoom = Coords.BITS - shift;
  }

  public static Tile of(long shortcode) {
    int size = 2 << Long.numberOfTrailingZeros(shortcode);
    int mask = Coords.EARTH_SIZE - size;
    Tile tile = new Tile(
        Coords.y(shortcode) & mask,
        Coords.x(shortcode) & mask,
        size);
    if( tile.shortcode != shortcode )
      throw BadError.of("Bad shortcode %016X -> %s = %016X",
          shortcode, tile.toString(), tile.shortcode);
    return tile;
  }

  public static Tile at(int zoom, int tilex, int tiley) {
    int size = Coords.EARTH_SIZE >> zoom;
    Tile tile = new Tile(tiley * size, tilex * size, size);
    if( tile.zoom != zoom || tile.tilex != tilex || tile.tiley != tiley )
      throw BadError.of("Tile construction failed: %d/%d/%d -> %s",
          zoom, tilex, tiley, tile);
    return tile;
  }

  public static Tile containing(long pos, int zoom) {
    long shortcode = codedContaining(pos, zoom);
    Tile tile = of(shortcode);
    if( tile.zoom != zoom )
      throw BadError.of("Did not preserve zoom: %d/%016X -> %016X -> %s",
          zoom, pos, shortcode, tile);
    return tile;
  }

  public static long codedContaining(long pos, int zoom) {
    long twoEarths = Coords.EARTH_SIZE * Coords.ONE_ONE;
    long mask = twoEarths - (twoEarths >> zoom);
    return (pos & mask) + (twoEarths >> (zoom+1));
  }

  public Point nwcorner() {
    return Point.at(shortcode - Coords.ONE_ONE * Long.lowestOneBit(shortcode));
  }

  @Override
  public String toString() {
    return zoom+"/"+tilex+"/"+tiley;
  }

  @Override
  public int hashCode() {
    return LyngHash.hash64to32(shortcode);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Tile other && other.shortcode == shortcode;
  }

}
