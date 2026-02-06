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
import java.util.function.LongConsumer;

import org.w3c.dom.Element;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.CompoundAddresser;
import net.makholm.henning.mapwarper.georaster.CompoundShortcode;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.UTM;
import net.makholm.henning.mapwarper.georaster.WebMercatorAddresser;
import net.makholm.henning.mapwarper.georaster.geotiff.CompoundDecoder;
import net.makholm.henning.mapwarper.georaster.geotiff.TrivialZip;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.KeyedLock;
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
        .resolve(String.format(Locale.ROOT, "%02d,%02d%s",
            tilex%100, tiley%100, extension));
  }

  private String urlForMaxitile(long tile) {
    var s = urlTemplate;
    s = s.replaceAll("~X~", ""+CompoundAddresser.tilex(tile));
    s = s.replaceAll("~Y~", ""+CompoundAddresser.tiley(tile));
    return s;
  }

  private static final CompoundDecoder decoder = new CompoundDecoder() {
    @Override
    protected boolean isOtherMinitileWanted(long wantedTile,
        long foundTile, int endOffset) {
      int wantSize = CompoundShortcode.maxisize(wantedTile);
      int foundSize = CompoundShortcode.maxisize(foundTile);
      return foundSize <= wantSize || foundSize <= 1000 ||
          endOffset <= 1048576;
    }
  };

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
  protected void downloadTile(long tile, LongConsumer callback)
      throws IOException, TryDownloadLater {
    var dest = fileForMaxitile(tile);
    // Take the lock so we won't make the file disappear under the feet
    // of any threads that might be trying to load minitiles from it.
    try( var locked = truncateLock.takeWriter(dest) ) {
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
    request.header("User-Agent", "Mapwarper/3.1");
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
        System.err.println(rspInfo.headers());
        Path bodyFile = Paths.get("httpErrorBody");
        DiskCachedTileset.tryDeleteFile(bodyFile);
        return HttpResponse.BodySubscribers.ofFile(bodyFile);
      }
    };
    try {
      var response = http.send(request.build(), handler);
      int code = response.statusCode();
      if( code != 200 && code != 404 ) {
        throw new IOException("Tile fetching failed for "+url);
      }
      if( !Files.isRegularFile(dest) ) {
        throw new IOException("Got "+code+" for "+url+" but no file!");
      }
      long length = Files.size(dest);
      if( length == 0 ) {
        throw new IOException("Got 200 for "+url+" but zero bytes!");
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
          if( msg.indexOf("Connection reset") >= 0 ) {
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
