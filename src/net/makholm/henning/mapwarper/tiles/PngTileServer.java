package net.makholm.henning.mapwarper.tiles;

/**
 * Common root class for tile services that construct their URLs
 * with the same parameter structure as OpenStreetMap does.
 */
public abstract class PngTileServer extends CommonWebTileset {

  public final String urlBase;
  public final String urlTail;

  protected PngTileServer(TileContext ctx, String name, String desc,
      String urlTemplate, String webUrlTemplate) {
    super(ctx, name, desc, ".png", webUrlTemplate);
    int i = urlTemplate.indexOf('*');
    if( i < 0 ) {
      urlBase = urlTemplate;
      urlTail = "";
    } else {
      urlBase = urlTemplate.substring(0, i);
      urlTail = urlTemplate.substring(i+1);
    }
  }

  @Override
  protected final String tileUrl(int zoom, int tilex, int tiley) {
    return urlBase+zoom+"/"+tilex+"/"+tiley+urlTail;
  }

}
