package net.makholm.henning.mapwarper.tiles;

import java.nio.file.Path;
import java.util.Locale;

import org.w3c.dom.Element;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.WebMercatorAddresser;
import net.makholm.henning.mapwarper.util.NiceError;

public class CommonWebTileset extends HttpTileset {

  protected final int logTilesize;
  protected final String urlTemplate;

  protected CommonWebTileset(TileContext ctx, String name, Element xml) {
    super(ctx, name, xml);

    urlTemplate = withApikey(xml.getAttribute("tileurl"));
    if( urlTemplate == null )
      throw NiceError.of("No tile URL defined for tileset %s", name);

    int tilesize = intAttr("tilesize", 256);
    logTilesize = Integer.numberOfTrailingZeros(tilesize);
    if( tilesize != (1 << logTilesize) )
      throw NiceError.of("Tile size %d for %s is not a power of 2",
          tilesize, name);
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

  protected String tileUrl(int zoom, int tilex, int tiley) {
    int i = urlTemplate.indexOf('*');
    if( i >= 0 ) {
      return urlTemplate.substring(0,i) +
          zoom + "/" + tilex + "/" + tiley +
          urlTemplate.substring(i+1);
    } else {
      String s = urlTemplate;
      s = s.replace("[Z]", ""+zoom);
      s = s.replace("[X]", ""+tilex);
      s = s.replace("[Y]", ""+tiley);
      return s;
    }
  }

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

  @Override
  public TileBitmap loadTile(long tile, boolean allowDownload)
      throws TryDownloadLater {
    if( boundingBox != null &&
        !boundingBox.intersects(WebMercatorAddresser.rectOf(tile)) ) {
      int tilex = WebMercatorAddresser.tilex(tile);
      int tiley = WebMercatorAddresser.tiley(tile);
      return TileBitmap.blank((tilex^tiley) % 2 == 0 ? 0xFFCCBBBB : 0xFFCCAAAA);
    } else {
      return super.loadTile(tile, allowDownload);
    }
  }

}
