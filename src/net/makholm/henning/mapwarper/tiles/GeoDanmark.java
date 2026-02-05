package net.makholm.henning.mapwarper.tiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import org.w3c.dom.Element;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.UTM;
import net.makholm.henning.mapwarper.georaster.WebMercatorAddresser;
import net.makholm.henning.mapwarper.georaster.geotiff.CompoundAddresser;
import net.makholm.henning.mapwarper.georaster.geotiff.CompoundDecoder;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.NiceError;

public class GeoDanmark extends Tileset {

  protected GeoDanmark(TileContext ctx, String name, Element xml) {
    super(ctx, name, xml);
  }

  private static final UTM UTM32 = UTM.WGS84(32,true);

  @Override
  public PixelAddresser makeAddresser(int zoom, Point globalRefpoint) {
    if( zoom == 14 ) zoom = 15;
    if( zoom < 15 || zoom > 19 )
      return WebMercatorAddresser.BLANK;
    return new CompoundAddresser.WithUTM(boundingBox, UTM32, 1000,
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

}
