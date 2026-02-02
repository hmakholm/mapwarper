package net.makholm.henning.mapwarper.tiles;

import java.util.List;

class Bing extends CommonWebTileset {

  private static final String WEBURL =
      "https://www.bing.com/maps?v=2&cp=[LAT]%7E[LON]&style=l&lvl=[Z]";
  private static final String TILEURL =
      "https://t.ssl.ak.tiles.virtualearth.net/tiles/a*.jpeg?g=14736&n=z&prx=1";

  private Bing(TileContext ctx) {
    super(ctx, "bing", "Bing Maps aerophotos", ".jpg", WEBURL);
  }

  @Override
  public String tileUrl(int zoom, int tilex, int tiley) {
    // The parameter is a "quadkey", as described on
    // https://learn.microsoft.com/en-us/bingmaps/articles/bing-maps-tile-system
    char[] quadkey = new char[zoom];
    for( int i=0; i<quadkey.length; i++ ) {
      int shift = zoom - i - 1;
      int xbit = (tilex >> shift) & 1;
      int ybit = (tiley >> shift) & 1;
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
