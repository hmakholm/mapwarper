package net.makholm.henning.mapwarper.tiles;

import java.util.List;

public class OpenRailwayMap extends PngTileServer {

  private OpenRailwayMap(TileContext ctx) {
    super(ctx, "openrail", "OpenRailwayMap",
        "https://a.tiles.openrailwaymap.org/standard/*.png", null);
  }

  @Override
  public int logTilesize() {
    return 9;
  }

  @Override
  public int guiTargetZoom() {
    return 19;
  }

  @Override
  public List<String> getCopyrightBlurb() {
    return List.of(OpenStreetMap.COPYRIGHT_BLURB,
        "Style: CC-BY-SA 2.0, OpenRailwayMap.org");
  }

  @Override
  public boolean isOverlayMap() {
    return true;
  }

  static void defineIn(TileContext ctx) {
    new OpenRailwayMap(ctx);
  }

}
