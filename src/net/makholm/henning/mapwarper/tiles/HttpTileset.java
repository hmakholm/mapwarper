package net.makholm.henning.mapwarper.tiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.w3c.dom.Element;

public abstract class HttpTileset extends DiskCachedTileset {

  protected HttpTileset(TileContext ctx, String name, Element xml) {
    super(ctx, name, xml);
    http = makeHttpClient();
  }

  public abstract String tileUrl(long tile);

  protected void finishRequest(HttpRequest.Builder request) {
    // Nothing by default
  }

  protected final HttpClient http;

  @Override
  public final void produceTileInFile(long tile, Path dest)
      throws IOException, TryDownloadLater {
    System.err.println(" (download "+tilename(tile)+")");
    String url = tileUrl(tile);
    var uri = URI.create(url);
    var request = HttpRequest.newBuilder(uri);
    request.header("User-Agent", "Mapwarper/0.1");
    request.GET();
    finishRequest(request);

    HttpResponse.BodyHandler<Path> handler = rspInfo -> {
      if( rspInfo.statusCode() == 200 ) {
        return HttpResponse.BodySubscribers.ofFile(dest);
      } else {
        System.err.println("Got "+rspInfo.statusCode()+" when fetching "+url);
        System.err.println(rspInfo.headers());
        Path bodyFile = Paths.get("httpErrorBody");
        tryDeleteFile(bodyFile);
        return HttpResponse.BodySubscribers.ofFile(bodyFile);
      }
    };
    try {
      var response = http.send(request.build(), handler);
      if( response.statusCode() != 200 ) {
        throw new IOException("Tile fetching failed for "+url);
      }
      if( !Files.isRegularFile(dest) ) {
        throw new IOException("Got 200 for "+url+" but no file!");
      }
      long length = Files.size(dest);
      if( length == 0 ) {
        throw new IOException("Got 200 for "+url+" but zero bytes!");
      }
      context.downloadedTiles.incrementAndGet();
      context.downloadedBytes.addAndGet(length);
    } catch( InterruptedException e ) {
      throw new RuntimeException("This shouldn't happen", e);
    } catch( IOException e ) {
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

}