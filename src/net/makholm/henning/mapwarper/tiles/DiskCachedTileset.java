package net.makholm.henning.mapwarper.tiles;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageIO;

import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.Tile;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.NiceError;

public abstract class DiskCachedTileset extends Tileset {

  public final int logTilesize;
  public final int tilesize;

  protected DiskCachedTileset(TileContext ctx, String name, String desc,
      String extension, String webUrlTemplate) {
    super(ctx, name, desc, webUrlTemplate);
    this.cacheRoot = ctx.tileCache.resolve(name);
    this.extension = extension;

    this.logTilesize = logTilesize();
    this.tilesize = 1 << logTilesize;
  }

  /** Most providers use 256-pixel tiles, but this can be overridden. */
  @Override
  public int logTilesize() {
    return 8;
  }

  @Override
  public final int logPixsize2tilezoom(int logPixsize) {
    return Coords.BITS - logPixsize - logTilesize;
  }

  /**
   * This should be thread safe with respect to producing <em>different</em>
   * tiles, but the caller must guard against calls to produce the
   * <em>same</em> tile happening in parallel.
   */
  public abstract void produceTileInFile(Tile tile, Path dest)
      throws IOException, TryDownloadLater;

  private final Path cacheRoot;
  public final String extension;

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
  private Path fileForTile(Tile tile) {
    return cacheRoot
        .resolve(Integer.toString(tile.zoom))
        .resolve((tile.tilex / 100) + "," + (tile.tiley / 100))
        .resolve(String.format(Locale.ROOT, "%02d,%02d%s",
            tile.tilex%100, tile.tiley%100, extension));
  }

  @Override
  public boolean isDiskCached(Tile tile) {
    return Files.exists(fileForTile(tile));
  }

  private static BufferedImage readFromFile(Path file) throws IOException {
    BufferedImage javaImage = ImageIO.read(file.toFile());
    if( javaImage == null )
      throw BadError.of("Failed to read "+file);
    return javaImage;
  }

  private BufferedImage produceTileInRam(Tile tile, boolean allowDownload)
      throws TryDownloadLater {
    Path file = fileForTile(tile);
    if( Files.isRegularFile(file) ) {
      context.diskCacheHits.incrementAndGet();
      try {
        return readFromFile(file);
      } catch( IOException e ) {
        System.err.println("Failed to read cached "+file+
            "; attempting redownload");
        tryDeleteFile(file);
      }
    }
    if( !allowDownload )
      return null;
    try {
      file.getParent().toFile().mkdirs();
      produceTileInFile(tile, file);
    } catch( IOException e ) {
      tryDeleteFile(file);
      e.printStackTrace();
      String msg = e.getMessage();
      if( msg == null || msg.isEmpty() )
        msg = e.getClass().getName();
      throw NiceError.of("Failed to download %s: %s",
          tilename(tile), msg);
    } catch( TryDownloadLater e ) {
      tryDeleteFile(file);
      throw e;
    }
    try {
      return readFromFile(file);
    } catch( IOException e ) {
      tryDeleteFile(file);
      throw NiceError.of("Could not read freshly downloaded %s: %s",
          tilename(tile), e.getMessage());
    }
  }

  @Override
  public TileBitmap loadTile(Tile tile, boolean allowDownload) throws TryDownloadLater {
    var rawBitmap = produceTileInRam(tile, allowDownload);
    if( rawBitmap == null ) return null;
    if( rawBitmap.getWidth() != tilesize || rawBitmap.getHeight() != tilesize )
      throw NiceError.of("Tile provider '%s' made %dx%d tile for %s; expected %dx%d",
          name, rawBitmap.getWidth(), rawBitmap.getHeight(), tile, tilesize, tilesize);
    return TileBitmap.of(rawBitmap, tile);
  }

  protected static void tryDeleteFile(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch( IOException e ) {
      // Ignores
    }
  }

}
