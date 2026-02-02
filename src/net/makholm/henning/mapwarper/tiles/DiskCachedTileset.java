package net.makholm.henning.mapwarper.tiles;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
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
  public abstract void produceTileInFile(long tile, Path dest)
      throws IOException, TryDownloadLater;

  protected final Path cacheRoot;
  public final String extension;

  /**
   * This methods should implicitly use the {@link #cacheRoot} field
   * to construct the path.
   */
  protected abstract Path fileForTile(long tile);

  private static BufferedImage readFromFile(Path file) throws IOException {
    BufferedImage javaImage = ImageIO.read(file.toFile());
    if( javaImage == null )
      throw new IOException("Failed to read "+file+" with no further information");
    return javaImage;
  }

  private BufferedImage produceTileInRam(long tile, boolean allowDownload)
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
  public TileBitmap loadTile(long tile, boolean allowDownload) throws TryDownloadLater {
    var rawBitmap = produceTileInRam(tile, allowDownload);
    if( rawBitmap == null ) return null;
    if( rawBitmap.getWidth() != tilesize || rawBitmap.getHeight() != tilesize )
      throw NiceError.of("Tile provider '%s' made %dx%d tile for %s; expected %dx%d",
          name, rawBitmap.getWidth(), rawBitmap.getHeight(), tile, tilesize, tilesize);
    return TileBitmap.of(rawBitmap);
  }

  protected static void tryDeleteFile(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch( IOException e ) {
      // Ignores
    }
  }

}
