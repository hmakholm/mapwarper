package net.makholm.henning.mapwarper.tiles;

import java.util.List;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.WebMercatorAddresser;

public class NomapTiles extends Tileset {

  protected NomapTiles(TileContext ctx) {
    super(ctx, "nomap", "Uniform grey background map", null);
  }

  @Override
  public int logPixsize2tilezoom(int logPixsize) {
    return 0;
  }

  @Override
  public int logTilesize() {
    return 0;
  }

  @Override
  public List<String> getCopyrightBlurb() {
    return List.of();
  }

  @Override
  public PixelAddresser makeAddresser(int zoom, Point p) {
    // Always use a WebMercatorAddresser in order to make virtual dispatch
    // easier on the JIT, as long as the user _doesn't_ request weird
    // tilesets.
    return new WebMercatorAddresser(0, 0);
  }

  @Override
  public TileBitmap loadTile(long tile, boolean allowDownload) {
    return TileBitmap.blank(0xFFAAAAAA);
  }

  @Override
  public String tilename(long tile) {
    return "nomap:0x"+Long.toHexString(tile);
  }

}
