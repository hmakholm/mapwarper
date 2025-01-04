package net.makholm.henning.mapwarper.tiles;

import net.makholm.henning.mapwarper.georaster.Tile;

/**
 * Common root class for tile services that construct their URLs
 * with the same parameter structure as OpenStreetMap does.
 */
public abstract class PngTileServer extends HttpTileset {

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
  public final String tileUrl(Tile tile) {
    return urlBase+tile.zoom+"/"+tile.tilex+"/"+tile.tiley+urlTail;
  }

}
