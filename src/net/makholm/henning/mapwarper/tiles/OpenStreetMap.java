package net.makholm.henning.mapwarper.tiles;

import java.net.http.HttpRequest;
import java.util.List;

public class OpenStreetMap extends PngTileServer {

  private static final String WEBURL =
      "https://openstreetmap.org/?mlat=[LAT]&mlon=[LON]&zoom=[Z]";

  OpenStreetMap(TileContext ctx, String name,
      String webLayerCode, String desc,
      String urlTemplate) {
    super(ctx, name, "OpenStreetmap - " + desc, urlTemplate,
        webLayerCode == null ? WEBURL : WEBURL + "&layers="+webLayerCode);
  }

  @Override
  protected void finishRequest(HttpRequest.Builder builder) {
    builder.setHeader("Referer", "https://www.openstreetmap.org/");
    super.finishRequest(builder);
  }

  public static final String COPYRIGHT_BLURB = "© OpenStreetMap contributors";

  @Override
  public List<String> getCopyrightBlurb() {
    return List.of(COPYRIGHT_BLURB);
  }

  static void defineIn(TileContext ctx) {
    new OpenStreetMap(ctx, "osm", null, "Standard",
        "https://tile.openstreetmap.org/*.png");

    // This might not be particularly useful -- railways are not especially
    // visible in this rendering -- but is included to help demonstrate
    // tile provider agility.
    new OpenStreetMap(ctx, "osmhot", "H", "Humanitarian",
        "https://a.tile.openstreetmap.fr/hot/*.png");

    // These alternative layers need API keys. One can get them without
    // much subterfuge by inspecting the web requests made by the slippy
    // map viewer at openstreeetmap.org -- but I'd better not put them
    // in the GitHub repo anyway. It's not like they're particularly
    // important for using the application anyway.
    if( ctx.includeIffyTilesets ) {
      // The Transport layer at openstreetmap.org used to be one's best
      // option for clearly showing railways, but nowadays the default
      // style has been updated to use darker colors for railways, so
      // it's not as needed these days.
      new OpenStreetMap(ctx, "transport", "T", "Transport Map",
          "https://a.tile.thunderforest.com/transport/*.png?apikey=--------------------------------");
    }
    if( ctx.includeIffyTilesets ) {
      // Tracestack won't serve tiles until it gets the right Referer,
      // and it's not really that relevant for this project.
      // Oh, and it seems to provide 512-pixel tiles, so this
      // code won't even work in the current application.
      new OpenStreetMap(ctx, "tracestack", "P", "Tracestack Topo",
          "https://tile.tracestrack.com/topo__/*.png?key=--------------------------------");
    }
  }

}
