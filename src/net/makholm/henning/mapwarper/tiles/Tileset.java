package net.makholm.henning.mapwarper.tiles;

import java.util.List;

import net.makholm.henning.mapwarper.georaster.Tile;
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
   * This should be thread safe with respect to producing <em>different</em>
   * tiles, but the caller must guard against calls to produce the
   * <em>same</em> tile happening in parallel.
   */
  public abstract TileBitmap loadTile(Tile tile, boolean allowDownload) throws TryDownloadLater;

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

  public final String tilename(Tile tile) {
    return name+":"+tile;
  }

}
