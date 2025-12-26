package net.makholm.henning.mapwarper.tiles;

import java.util.List;

import net.makholm.henning.mapwarper.georaster.Tile;

class Google extends HttpTileset {

  /**
   * https://stackoverflow.com/questions/32806084/google-map-zoom-parameter-in-url-not-working
   */
  private static final String WEBURL =
      "https://maps.google.com/?q=[LAT],[LON]&ll=[LAT],[LON]&z=[Z]";


  private Google(TileContext ctx) {
    super(ctx, "google", "Google Maps aerophotos", ".jpg", WEBURL);
  }

  @Override
  public String tileUrl(Tile tile) {
    // The 'v' parameter is apparently not a protocol version but something
    // they bump when they update the photo collection. It appears to
    // change in lockstep from time to time; I haven't figured if it's
    // on a schedule or just whenever.
    return "https://khms0.google.com/kh/v=1004?x="
    + tile.tilex + "&y=" + tile.tiley + "&z=" + tile.zoom;
  }

  @Override
  public int guiTargetZoom() {
    return 18;
  }

  @Override
  public List<String> getCopyrightBlurb() {
    return List.of("© Google, I suppose ...");
  }

  static void defineIn(TileContext ctx) {
    new Google(ctx);
  }

}
