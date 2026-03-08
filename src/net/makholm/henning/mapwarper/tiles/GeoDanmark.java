package net.makholm.henning.mapwarper.tiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.w3c.dom.Element;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.CompoundAddresser;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.UTM;
import net.makholm.henning.mapwarper.georaster.WebMercatorAddresser;
import net.makholm.henning.mapwarper.georaster.geotiff.CompoundDecoder;
import net.makholm.henning.mapwarper.georaster.geotiff.TrivialZip;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.KeyedLock;
import net.makholm.henning.mapwarper.util.MathUtil;
import net.makholm.henning.mapwarper.util.NiceError;

public class GeoDanmark extends Tileset {

  private final String urlTemplate;
  private final String extension;
  private final HttpClient http;

  /**
   * The writer side of this lock governs the initial deletion/truncation
   * of the maxitile file before the download starts, but it is then
   * dropped such that we can attempt to load tiles from the partial download.
   */
  private final KeyedLock<Path> truncateLock = new KeyedLock<>();

  protected GeoDanmark(TileContext ctx, String name, Element xml) {
    super(ctx, name, xml);
    urlTemplate = withApikey(stringAttr("tileurl"));
    extension = stringAttr("extension");
    http = makeHttpClient();
    transferOptions.put("Color as delivered", GeoDanmark::RGBItoARGB);
    transferOptions.put("Improved color", transferFunction = GeoDanmark::RGBItoVividColor);
    transferOptions.put("Supersaturated color", GeoDanmark::RGBItoVividerColor);
  }

  private static final UTM UTM32 = UTM.WGS84(32,true);

  @Override
  public PixelAddresser makeAddresser(int zoom, Point globalRefpoint) {
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
        .resolve(String.format(Locale.ROOT, "%02d,%02d%s",
            tilex%100, tiley%100, extension));
  }

  private String urlForMaxitile(long tile) {
    var s = urlTemplate;
    s = s.replaceAll("~X~", ""+CompoundAddresser.tilex(tile));
    s = s.replaceAll("~Y~", ""+CompoundAddresser.tiley(tile));
    return s;
  }

  private final CompoundDecoder decoder = new CompoundDecoder();

  private static int RGBItoARGB(int pixel) {
    return (pixel >> 8) | 0xFF000000;
  }

  private static int RGBItoVividColor(int pixel) {
    int r = (pixel >> 24) & 0xFF;
    int g = (pixel >> 16) & 0xFF;
    int b = (pixel >> 8) & 0xFF;
    int ir = (pixel) & 0xFF;
    int y = (54*r + 186*g + 18*b) >> 8;
    // (Those coefficients, derived from sRGB -> CIE Y luminance,
    // sum to 257, balancing out the rounding towards zero of >>8).
    r -= y; g -= y; b -= y;
    // In an actual YCrCb conversion we would now rescale r and b to
    // fit in a byte each (and let g be implicit, since r,g,b at this
    // point satisfy 54r+185g+18b=0, up to rounding).
    // But since we'll convert back presently anyway, that will be
    // easier if we just keep them as signed values with weird ranges.
    r += r/4; g += g/4; b += b/4; // inrease saturation a bit
    y = ir < y ? ir : y - (ir-y)/5; // use the IR channel to improve contrast
    r = MathUtil.clamp(0, y+r, 255);
    g = MathUtil.clamp(0, y+g, 255);
    b = MathUtil.clamp(0, y+b, 255);
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  private static int RGBItoVividerColor(int pixel) {
    int r = (pixel >> 24) & 0xFF;
    int g = (pixel >> 16) & 0xFF;
    int b = (pixel >> 8) & 0xFF;
    int ir = (pixel) & 0xFF;
    int y = (54*r + 186*g + 18*b) >> 8;
    r -= y; g -= y; b -= y;
    r = r*7/3; g = g*7/3; b = b*7/3; // jack up saturation even more
    y = ir < y ? ir : y - (ir-y)/5;
    r = MathUtil.clamp(0, y+r, 255);
    g = MathUtil.clamp(0, y+g, 255);
    b = MathUtil.clamp(0, y+b, 255);
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  @Override
  protected TileBitmap loadTile(long tile) throws IOException {
    var path = fileForMaxitile(tile);
    TileBitmap got;
    try( var locked = truncateLock.tryReader(path) ) {
      if( locked == null ) return null;
      got = decoder.decode(path, tile);
    } catch( NiceError e ) {
      e.printStackTrace();
      throw BadError.of("Could not decode tile %s: %s", tilename(tile), e);
    }
    if( got.numPixels > 1 )
      return got;
    switch( got.pixelByIndex(0) ) {
    case CompoundDecoder.EMPTY_ZIP:
      return CompoundAddresser.pseudotile(tile, got.pixelByIndex(0));
    default:
      return null;
    }
  }

  @Override
  protected void downloadTile(long tile, DownloadCallback callback)
      throws IOException, TryDownloadLater {
    var dest = fileForMaxitile(tile);
    // Take the lock so we won't make the file disappear under the feet
    // of any threads that might be trying to load minitiles from it.
    try( var _ = truncateLock.takeWriter(dest) ) {
      DiskCachedTileset.tryDeleteFile(dest);
      Files.createDirectories(dest.getParent());
    }
    String maxiname = name+":"+CompoundAddresser.tilex(tile) +
        "/"+CompoundAddresser.tiley(tile);
    System.err.println("  (download "+maxiname+")");
    long starttime = System.nanoTime();
    var url = urlForMaxitile(tile);
    var uri = URI.create(url);
    var request = HttpRequest.newBuilder(uri);
    request.header("User-Agent", "Mapwarper/3.2");
    request.GET();

    HttpResponse.BodyHandler<Path> handler = rspInfo -> {
      if( rspInfo.statusCode() == 200 ) {
        var writer = HttpResponse.BodySubscribers.ofFile(dest);
        return decoder.new HttpDownloadSnooper<>(writer, tile, callback);
      } else if( rspInfo.statusCode() == 404 ) {
        System.err.println("Got 404 for "+maxiname);
        try {
          TrivialZip.writeEmptyZip(dest);
        } catch( IOException e ) {
          throw BadError.of("Failed to write empty %s: %s", dest, e);
        }
        return HttpResponse.BodySubscribers.<Path>replacing(null);
      } else {
        System.err.println("Got "+rspInfo.statusCode()+" when fetching "+maxiname);
        dump(System.err, rspInfo.headers());
        Path bodyFile = Paths.get("httpErrorBody");
        DiskCachedTileset.tryDeleteFile(bodyFile);
        return HttpResponse.BodySubscribers.ofFile(bodyFile);
      }
    };
    try {
      var response = http.send(request.build(), handler);
      int code = response.statusCode();
      switch( code ) {
      case 200:
      case 404:
        break;
      case 500:
        throw new TryDownloadLater("Got HTTP response "+code);
      default:
        throw new IOException("Tile fetching failed for "+url);
      }
      if( !Files.isRegularFile(dest) ) {
        throw new IOException("Got "+code+" for "+url+" but no file!");
      }
      long length = Files.size(dest);
      if( length == 0 ) {
        throw new IOException("Got "+code+" for "+url+" but zero bytes!");
      }
    } catch( InterruptedException e ) {
      throw new RuntimeException("This shouldn't happen", e);
    } catch( IOException e ) {
      if( e.getCause() instanceof CompoundDecoder.GotEverythingWanted ) {
        // Very well.
      } else {
        Files.deleteIfExists(dest);
        String msg = e.getMessage();
        if( msg != null ) {
          if( msg.indexOf("Connection reset") >= 0 ||
              msg.indexOf("GOAWAY received") >= 0 ) {
            throw new TryDownloadLater(e);
          }
        }
        throw e;
      }
    }
    var seconds = (System.nanoTime()-starttime)/1e9;
    System.err.printf(Locale.ROOT,"  (got %s with %d bytes in %.1f seconds)\n",
        maxiname, Files.size(dest),seconds);
  }

  @Override
  public String tilename(long tile) {
    return name+":"+CompoundAddresser.tilename(tile);
  }

}
