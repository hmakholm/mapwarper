package net.makholm.henning.mapwarper.georaster;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;

public class WebMercatorAddresser implements PixelAddresser {

  private final int shiftForTile;
  private final long shortcodeBase;

  private final int logTilesize;
  private final int shiftForPixel;
  private final int pixelmask;

  public WebMercatorAddresser(int zoom, int logTilesize) {
    this.shiftForTile = Coords.BITS - zoom;
    this.shortcodeBase = ((long)zoom << 56) | 0x10;

    this.logTilesize = logTilesize;
    this.shiftForPixel = shiftForTile - logTilesize;
    this.pixelmask = (1 << logTilesize) - 1;
  }

  private long makeShortcode(int tilex, int tiley) {
    return (tiley & 1) + // index bit for local hashing
        ((tilex & 1) << 1) + // index bit for local hashing
        ((long)tiley << 8) + // 24 bits for tile coordinate
        ((long)tilex << 32) + // 24 bits for tile coordinate
        shortcodeBase;
  }

  public static long makeShortcode(int zoom, int tilex, int tiley) {
    return new WebMercatorAddresser(zoom, 0).makeShortcode(tilex, tiley);
  }

  public static int zoom(long shortcode) {
    return (int)(shortcode >>> 56);
  }

  public static int tilex(long shortcode) {
    return (int)(shortcode >> 32) & 0xFFFFFF;
  }

  public static int tiley(long shortcode) {
    return (int)shortcode >>> 8;
  }

  public static AxisRect rectOf(long shortcode) {
    int zoom = zoom(shortcode);
    int shift = Coords.BITS - zoom;
    int left = tilex(shortcode) << shift;
    int top = tiley(shortcode) << shift;
    int size = 1 << shift;
    return new AxisRect(Point.at(left, top),
        Point.at(left+size, top+size));
  }

  private int x, y;

  @Override
  public long locate(double xx, double yy) {
    // This is in the map renderer hot loop, so performance matters.

    // For the most principled behavior we should use floors, but that only
    // differs from the standard long-to-double conversion for negative
    // coordinates, which are not even meaningful in the global coordinate
    // system.

    // So we actually use the default round-towards-zero conversion -- this
    // just means that the phantom copies of the Earth to the north and west
    // of the main coordinates will be shifted by a small distance (a few
    // dozen millimeters) especially in the x direction that the sign bits
    // of the y coordinates bleed into. But nobody really cares about that
    // anyway.

    // Sufficiently new processors (which support embedded rounding control
    // in FP operations; search for "EVEX prefix") should actually be able
    // to do a true double-to-long _floor_ in a single instruction, but I
    // don't think HotSpot uses that possibility.
    x = (int)(long)xx & (Coords.EARTH_SIZE-1);
    y = (int)(long)yy & (Coords.EARTH_SIZE-1);

    return makeShortcode(x >> shiftForTile, y >> shiftForTile);
  }

  @Override
  public boolean onTileEdge() {
    return (((x >> shiftForPixel) + 1) & pixelmask) <= 1 ||
        (((y >> shiftForPixel) + 1) & pixelmask) <= 1;
  }

  @Override
  public int getPixel(TileBitmap data) {
    // Cater for blank tiles
    if( data.numPixels == 1 )
      return data.pixelByIndex(0);

    int xp = (x >> shiftForPixel) & pixelmask;
    int yp = (y >> shiftForPixel) & pixelmask;
    return data.pixelByIndex((yp << logTilesize) + xp);
  }

  @Override
  public long getDownloadPriority(long shortcode) {
    int zoom = zoom(shortcode);
    int shift = Coords.BITS - zoom - 1;
    int dx = ((tilex(shortcode)*2+1) << shift) - x;
    int dy = ((tiley(shortcode)*2+1) << shift) - y;
    long sqdiff = (long)dx*dx + (long)dy*dy;
    return sqdiff + ((long)zoom << 58);
  }

  @Override
  public boolean isOddDownloadTile(long shortcode) {
    return
        (shortcode & (1L << 32)) != 0 ^
        (shortcode & (1L << 8)) != 0;
  }

}
