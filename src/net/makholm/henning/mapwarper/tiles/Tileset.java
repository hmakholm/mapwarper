package net.makholm.henning.mapwarper.tiles;

import java.util.List;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.WebMercator;

public abstract class Tileset {

  public final TileContext context;

  public static void defineStandardTilesets(TileContext ctx) {
    OpenStreetMap.defineIn(ctx);
    OpenTopoMap.defineIn(ctx);
    Google.defineIn(ctx);
    Bing.defineIn(ctx);
    OpenRailwayMap.defineIn(ctx);
    GeoDanmark.defineIn(ctx);
  }

  public abstract int logPixsize2tilezoom(int logPixsize);

  public abstract int logTilesize();

  public int guiTargetZoom() {
    return 16;
  }

  /**
   * Create a pixel addresser for a particular resolution and area.
   *
   * The default implementation here must be overridden by tilesets that
   * don't use the usual OpenStreetMap-like tile structure.
   *
   * @param zoom indicates the resolution of the pixels we request. For pixel
   * sources whose underlying raster is in a roughly Mercator-like projection,
   * this indicates that there are 2^(zoom+8) pixels along any circle of latitude.
   * For other sources, the resolution should be the one among the available
   * ones whose scale is closest to one pixel per 2^(18-zoom)/3 meters
   * (These two definitions coincide at latitudes of about 56 degrees).
   *
   * @param globalRefpoint indicates a (global) that the addresser will be
   * used near. Some sources (in particular ones whose underlying raster is
   * already aligned with our global Web-Mercator coordinates) ignore this
   * parameter and produce an addresser that works correctly everywhere.
   * On the other hand, it may also precompute a transformation approximation
   * that may start producing imprecisions when we get far enough away from
   * the source point.
   *
   * (The rough guideline for imprecision is that at a distance of 4,000
   * source pixels from the reference point, the error should be less than
   * about 1/200 of the distance to the reference point. This standard is
   * selected such that rendering 256x256 tiles with squeezes of up to 32
   * should not produce visible jumps between tiles.)
   */
  public abstract PixelAddresser makeAddresser(int zoom, Point globalRefpoint);

  /**
   * This should be thread safe with respect to producing <em>different</em>
   * tiles, but the caller must guard against calls to produce the
   * <em>same</em> tile happening in parallel.
   *
   * @param tile as returned by {@link PixelAddresser#locate(double, double)}
   * of the addressing objects created by {@link #makeAddresser(int, Point)}.
   */
  public abstract TileBitmap loadTile(long tile, boolean allowDownload) throws TryDownloadLater;

  public abstract String tilename(long tile);

  public abstract List<String> getCopyrightBlurb();

  public boolean isOverlayMap() {
    return false;
  }

  /**
   * This default implementation supports generating a web url by
   * substitutions. It can be overridden for map sources that need
   * ad-hoc parameters.
   */
  public String webUrlFor(int zoom, long pos) {
    if( webUrlTemplate == null )
      return null;
    var xyStrings = WebMercator.signedRoundedDegrees(zoom, pos);
    return webUrlTemplate
        .replace("[LAT]", xyStrings[0])
        .replace("[LON]", xyStrings[1])
        .replace("[Z]", Integer.toString(zoom));
  }

  // -----------------------------------------------------

  public final String name;
  public final String desc;
  protected final String webUrlTemplate;

  protected Tileset(TileContext ctx,
      String name, String desc, String webUrlTemplate) {
    this.context = ctx;
    this.name = name;
    this.desc = desc;
    this.webUrlTemplate = webUrlTemplate;

    context.tilesets.put(name, this);
  }

}
