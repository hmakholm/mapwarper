package net.makholm.henning.mapwarper.tiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.UTM;
import net.makholm.henning.mapwarper.georaster.WebMercatorAddresser;
import net.makholm.henning.mapwarper.georaster.geotiff.CompoundAddresser;
import net.makholm.henning.mapwarper.georaster.geotiff.CompoundDecoder;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.NiceError;

public class GeoDk2 extends Tileset {

  private final Path cacheRoot;

  protected GeoDk2(TileContext ctx) {
    super(ctx, "geodk", "GeoDanmark orthophotos", null);
    cacheRoot = ctx.tileCache.resolve(name);
  }

  @Override
  public List<String> getCopyrightBlurb() {
    return List.of("Aerophotos cover Denmark only",
        "EXPERIMENTAL; cannot actually download yet.");
  }

  /**
   * A hardcoded bounding box based on what the dataset actually contains
   * photos of.
   */
  private static final AxisRect DENMARK = new AxisRect(
      Point.at(560922560, 324748672),
      Point.at(575166809, 341940864));

  private static final UTM UTM32 = UTM.WGS84(32,true);

  @Override
  public int guiTargetZoom() {
    return 18;
  }

  @Override
  public PixelAddresser makeAddresser(int zoom, Point globalRefpoint) {
    if( zoom == 14 ) zoom = 15;
    if( zoom < 15 || zoom > 19 )
      return WebMercatorAddresser.BLANK;
    return new CompoundAddresser.WithUTM(DENMARK, UTM32, 1000,
        8000 >> (19-zoom), 512, globalRefpoint);
  }

  private Path fileForMaxitile(long tile) {
    int tilex = CompoundAddresser.tilex(tile);
    int tiley = CompoundAddresser.tiley(tile);
    return cacheRoot
        .resolve((tilex/100) + "," + (tiley/100))
        .resolve(String.format(Locale.ROOT, "%02d,%02d.zip",
            tilex%100, tiley%100));
  }

  private static final CompoundDecoder decoder = new CompoundDecoder();

  @Override
  public TileBitmap loadTile(long tile, boolean allowDownload) throws TryDownloadLater {
    var path = fileForMaxitile(tile);
    TileBitmap got;
    try {
      got = decoder.decode(path, tile);
    } catch( IOException e ) {
      throw BadError.of("Could not load tile %s from %s: %s",
          tilename(tile), path, e);
    } catch( NiceError e ) {
      e.printStackTrace();
      throw BadError.of("Could not decode tile %s: %s", tilename(tile), e);
    }
    if( got.numPixels > 1 )
      return got;
    else if( !allowDownload )
      return null;
    else
      return CompoundAddresser.pseudotile(tile, got.pixelByIndex(0));
  }

  @Override
  public String tilename(long tile) {
    return name+":"+CompoundAddresser.tilename(tile);
  }

  public static void defineIn(TileContext ctx) {
    new GeoDk2(ctx);
  }

}
