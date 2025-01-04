package net.makholm.henning.mapwarper.tiles;

import java.util.List;

/**
 * In contrast to OpenStreeMap, opentopomap.org can actually show an
 * exact marker.
 */
public class OpenTopoMap extends PngTileServer {

  private static final String WEBURL =
      "https://opentopomap.org/#marker=[Z]/[LAT]/[LON]";

  private OpenTopoMap(TileContext ctx) {
    super(ctx, "topo", "OpenTopoMap",
        "https://c.tile.opentopomap.org/*.png", WEBURL);
  }

  @Override
  public List<String> getCopyrightBlurb() {
    return List.of(OpenStreetMap.COPYRIGHT_BLURB,
        "Map rendering: © OpenTopoMap.org (CC-BY-SA)");
  }

  static void defineIn(TileContext ctx) {
    new OpenTopoMap(ctx);
  }

}
