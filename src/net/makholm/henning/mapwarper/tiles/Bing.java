package net.makholm.henning.mapwarper.tiles;

import java.util.List;

import net.makholm.henning.mapwarper.georaster.Tile;

class Bing extends HttpTileset {

  private static final String WEBURL =
      "https://www.bing.com/maps?v=2&cp=[LAT]%7E[LON]&style=l&lvl=[Z]";
  private static final String TILEURL =
      "https://t.ssl.ak.tiles.virtualearth.net/tiles/a*.jpeg?g=14736&n=z&prx=1";

  private Bing(TileContext ctx) {
    super(ctx, "bing", "Bing Maps aerophotos", ".jpg", WEBURL);
  }

  @Override
  public String tileUrl(Tile tile) {
    // The parameter is a "quadkey", as described on
    // https://learn.microsoft.com/en-us/bingmaps/articles/bing-maps-tile-system
    char[] quadkey = new char[tile.zoom];
    for( int i=0; i<quadkey.length; i++ ) {
      int shift = tile.zoom - i - 1;
      int xbit = (tile.tilex >> shift) & 1;
      int ybit = (tile.tiley >> shift) & 1;
      quadkey[i] = (char)('0' + 2*ybit + xbit);
    }
    return TILEURL.replace("*", new String(quadkey));
  }

  @Override
  public int guiTargetZoom() {
    return 18;
  }

  @Override
  public List<String> getCopyrightBlurb() {
    return List.of("© Microsoft, I suppose ...");
  }

  static void defineIn(TileContext ctx) {
    new Bing(ctx);
  }

}
