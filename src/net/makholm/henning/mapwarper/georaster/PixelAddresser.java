package net.makholm.henning.mapwarper.georaster;

import net.makholm.henning.mapwarper.geometry.Point;

/**
 * Implementations of this class know how to convert a set of global
 * coordinates to a pixel location in a particular tileset at a particular
 * zoom level. It includes coordinate transformations when the tileset is
 * not in web-Mercator coordinates.
 */
public interface PixelAddresser {

  /**
   * Prepare the object to select a pixel at the given global
   * coordinates.
   * <p>
   * Returns a "shortcode" for the tile, which is a mostly opaque
   * 64-bit value that is unique among all tiles the tileset can
   * produce. The exception from opaqueness are: (a) the two least
   * significant bits should differ between any two tiles that
   * meet in a corner; (b) no shortcodes for the tileset differ only
   * in those two bits; (c) 0L, 1L, 2L, 3L are not valid shortcodes.
   * <p>
   * The object should store the address <em>within</em> the tile
   * in an appropriate format internally, such that an RGB value
   * can later be extracted with {@link #getPixel(TileBitmap)}.
   */
  public long locate(double globalX, double globalY);

  public default long locate(Point p) {
    return locate(p.x, p.y);
  }

  /**
   * Return true if the point we need is right on the edge of the
   * tile, such that we shouldn't attempt to download based on just
   * needing that point.
   */
  public boolean onTileEdge();

  /**
   * Given a bitmap that the caller promises to be for the right
   * shortcode, get the ARGB pixel value at the position given by
   * the last call to {@link #locate(Point)}.
   */
  public int getPixel(TileBitmap data);

  /**
   * Return a smaller number for tiles that should be downloaded first,
   * based on the size and distance to the position given by the last
   * call to {@link #locate(Point)}.
   */
  public long getDownloadPriority(long shortcode);

  public boolean isOddDownloadTile(long shortcode);

}
