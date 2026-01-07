package net.makholm.henning.mapwarper.tiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.makholm.henning.mapwarper.georaster.Tile;

public abstract class HttpTileset extends DiskCachedTileset {

  protected HttpTileset(TileContext ctx, String name, String desc,
      String extension, String webUrlTemplate) {
    super(ctx, name, desc, extension, webUrlTemplate);
  }

  public abstract String tileUrl(Tile tile);

  protected void finishRequest(HttpRequest.Builder request) {
    // Nothing by default
  }

  @Override
  public final void produceTileInFile(Tile tile, Path dest)
      throws IOException, TryDownloadLater {
    System.err.println(" (download "+name+":"+tile+")");
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
        return HttpResponse.BodySubscribers.ofFile(Paths.get("httpErrorBody"));
      }
    };
    try {
      var response = context.http.send(request.build(), handler);
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