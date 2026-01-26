package net.makholm.henning.mapwarper.tiles;

import java.util.List;

import net.makholm.henning.mapwarper.georaster.Tile;
import net.makholm.henning.mapwarper.georaster.TileBitmap;

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
  public TileBitmap loadTile(Tile tile, boolean allowDownload) {
    return TileBitmap.blank(0xFFAAAAAA, tile);
  }

}
