package net.makholm.henning.mapwarper.tiles;

import java.util.function.LongConsumer;

import org.w3c.dom.Element;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.WebMercatorAddresser;
import net.makholm.henning.mapwarper.util.XmlConfig;

public class NomapTiles extends Tileset {

  protected NomapTiles(TileContext ctx) {
    super(ctx, "nomap", syntheticXml());
  }

  private static Element syntheticXml() {
    Element elt = XmlConfig.freshElement("tileset");
    elt.setAttribute("name", "nomap");
    elt.setAttribute("desc", "Uniform grey background map");
    return elt;
  }


  @Override
  public PixelAddresser makeAddresser(int zoom, Point p) {
    // Always use a WebMercatorAddresser in order to make virtual dispatch
    // easier on the JIT, as long as the user _doesn't_ request weird
    // tilesets.
    return new WebMercatorAddresser(0, 0);
  }

  @Override
  public TileBitmap loadTile(long tile) {
    return TileBitmap.blank(0xFFAAAAAA);
  }

  @Override
  public void downloadTile(long tile, LongConsumer callback) {
    // Do nothing; the tile can always be loaded!
  }

  @Override
  public String tilename(long tile) {
    return "nomap:0x"+Long.toHexString(tile);
  }

}
