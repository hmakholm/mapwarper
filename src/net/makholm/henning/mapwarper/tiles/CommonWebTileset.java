package net.makholm.henning.mapwarper.tiles;

import java.nio.file.Path;
import java.util.Locale;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.WebMercatorAddresser;

public abstract class CommonWebTileset extends HttpTileset {

  protected int logTilesize = 8;

  protected CommonWebTileset(TileContext ctx, String name, String desc, String extension, String webUrlTemplate) {
    super(ctx, name, desc, extension, webUrlTemplate);
  }

  @Override
  public PixelAddresser makeAddresser(int zoom, Point refpoint) {
    return new WebMercatorAddresser(zoom - (logTilesize-8), logTilesize);
  }

  @Override
  protected final int tilesize(long tile) {
    return 1 << logTilesize;
  }

  /**
   * This methods implicitly decides the directory layout for the tile
   * cache.
   *
   * For the time being there's a directory level for the overall
   * zoom level, one for each 100×100 tile square, and then one for
   * the last two digits of each coordinate. This makes it easy to
   * get from tile coordinates to file names even by hand.
   *
   * (And given that there's a RAM cache in front, the filename
   * construction is not a hot code path, so performance of
   * the decimal representation is a non-issue.)
   *
   * The fanout at the bottom level can theoretically be 10,000 tiles
   * per directory, but it's unlikely we'll ever need that many. Suppose
   * the largest zoom that we'll need any kind of <em>area</em> coverage
   * for is z19, and we'll need that in a band 700 m wide (that's the
   * with of the Maschen classification yard), corresponding to 16 tile
   * sidelength.
   *
   * A band of width 16 drawn <em>diagonally</em> across a 100×100 square
   * will hit at most about 2300 of the tiles, which ought to be okay for
   * a single directory.
   *
   * The number of 100×100 directories themselves probably won't be excessive
   * either. As an empirical data point, after warping 30 German stations,
   * some of them pretty large, I still have only 76 of these directories
   * for google18 tiles.
   */
  @Override
  protected Path fileForTile(long tile) {
    var zoom = WebMercatorAddresser.zoom(tile);
    var tilex = WebMercatorAddresser.tilex(tile);
    var tiley = WebMercatorAddresser.tiley(tile);
    return cacheRoot
        .resolve(Integer.toString(zoom))
        .resolve((tilex / 100) + "," + (tiley / 100))
        .resolve(String.format(Locale.ROOT, "%02d,%02d%s",
            tilex%100, tiley%100, extension));
  }

  protected abstract String tileUrl(int zoom, int tilex, int tiley);

  @Override
  public final String tileUrl(long tile) {
    return tileUrl(WebMercatorAddresser.zoom(tile),
        WebMercatorAddresser.tilex(tile),
        WebMercatorAddresser.tiley(tile));
  }

  @Override
  public String tilename(long tile) {
    return name+":"+
        WebMercatorAddresser.zoom(tile)+"/"+
        WebMercatorAddresser.tilex(tile)+"/"+
        WebMercatorAddresser.tiley(tile);
  }

}
