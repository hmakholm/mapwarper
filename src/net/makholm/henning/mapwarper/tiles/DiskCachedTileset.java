package net.makholm.henning.mapwarper.tiles;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.LongConsumer;

import javax.imageio.ImageIO;

import org.w3c.dom.Element;

import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.util.KeyedLock;
import net.makholm.henning.mapwarper.util.NiceError;

/**
 * These are "ordinary" tilesets that represent each tile as a single
 * file in the disk cache.
 */
public abstract class DiskCachedTileset extends Tileset {

  public final String extension;

  private final KeyedLock<Path> downloadLock = new KeyedLock<>();

  protected DiskCachedTileset(TileContext ctx, String name, Element xml) {
    super(ctx, name, xml);
    if( xml.hasAttribute("extension") ) {
      extension = xml.getAttribute("extension");
    } else {
      String s = xml.getAttribute("tileurl");
      if( s == null ) s = "(missing)";
      int i = s.indexOf('?');
      if( i < 0 ) i = s.length();
      int j = s.lastIndexOf('.', i);
      if( j < 0 )
        throw NiceError.of("Cannot derive extension for %s from '%s'",
            name, s);
      extension = s.substring(j, i);
    }
  }

  /** Most providers use 256-pixel tiles, but this can be overridden. */
  protected int tilesize(long shortcode) {
    return 256;
  }

  /**
   * This should be thread safe with respect to producing <em>different</em>
   * tiles, but the caller must guard against calls to produce the
   * <em>same</em> tile happening in parallel.
   */
  public abstract void produceTileInFile(long tile, Path dest)
      throws IOException, TryDownloadLater;

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

  private BufferedImage produceTileInRam(long tile) throws IOException {
    Path file = fileForTile(tile);
    if( Files.isRegularFile(file) ) {
      context.diskCacheHits.incrementAndGet();
      try( var locked = downloadLock.tryReader(file) ) {
        if( locked == null ) return null;
        return readFromFile(file);
      } catch( IOException e ) {
        tryDeleteFile(file);
        throw e;
      }
    }
    return null;
  }

  @Override
  public TileBitmap loadTile(long tile) throws IOException {
    var rawBitmap = produceTileInRam(tile);
    if( rawBitmap == null ) return null;
    var tilesize = tilesize(tile);
    if( rawBitmap.getWidth() != tilesize || rawBitmap.getHeight() != tilesize )
      throw NiceError.of("Got %dx%d tile for %s; expected %dx%d",
          rawBitmap.getWidth(), rawBitmap.getHeight(), tilename(tile),
          tilesize, tilesize);
    return TileBitmap.of(rawBitmap);
  }

  @Override
  public void downloadTile(long tile, LongConsumer callback)
      throws IOException, TryDownloadLater {
    Path file = fileForTile(tile);
    try( var locked = downloadLock.takeWriter(file) ) {
      file.getParent().toFile().mkdirs();
      produceTileInFile(tile, file);
    } catch( IOException | TryDownloadLater e ) {
      tryDeleteFile(file);
      throw e;
    }
  }

  protected static void tryDeleteFile(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch( IOException e ) {
      // Ignores
    }
  }

}
