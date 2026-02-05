package net.makholm.henning.mapwarper.tiles;

import org.w3c.dom.Element;

class Bing extends CommonWebTileset {

  Bing(TileContext ctx, String name, Element xml) {
    super(ctx, name, xml);
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
    return urlTemplate.replace("*", new String(quadkey));
  }

}
