package net.makholm.henning.mapwarper.tiles;

import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.WebMercatorAddresser;

/**
 * Orthophotos from GeoDenmark, covering Denmark only.
 *
 * https://datafordeler.dk/dataoversigt/geodanmark-ortofoto/ortofoto-foraar-web-mercator-wmts/
 * says it's going to be discontinued in mid 2026, unfortunately.
 */
class GeoDanmark extends CommonWebTileset {

  /**
   * https://confluence.sdfi.dk/pages/viewpage.action?pageId=13665234
   *
   * BEMÆRK
   *
   * Via WebGIS er det muligt at se brugernavn/adgangskode for tjenestebrugere
   * ved hjælp af udviklerværktøjerne i en browser. Da det er offentlige frie
   * data, så betyder det ikke rettighedsmæssigt noget, at brugernavn og
   * adgangskode for tjenestebrugeren bliver vist.
   *
   * Brug en unik eller en autogenereret adgangskode til hver tjenestebruger.
   * Du skal ikke genbruge adgangskoder fra andre systemer eller enheder.
   */
  private static final String PWD = "6qKAh-QH364c";
  private static final String USER = "IBJZYUIBGO";

  protected GeoDanmark(TileContext ctx) {
    super(ctx, "geodanmark", "GeoDanmark orthophotos", ".jpg",
        "https://www.geodanmark.dk/home/vejledninger/vilkaar-for-data-anvendelse/");
  }

  @Override
  public String tileUrl(int zoom, int tilex, int tiley) {
    return "https://services.datafordeler.dk/GeoDanmarkOrto/"+
        "orto_foraar_webm/1.0.0/WMTS/" +
        "orto_foraar_webm/default/DFD_GoogleMapsCompatible/" +
        zoom + "/" + tiley + "/" + tilex + ".jpg" +
        "?username=" + USER + "&password=" + PWD + "&";
  }

  @Override
  public int guiTargetZoom() {
    return 18;
  }

  @Override
  public List<String> getCopyrightBlurb() {
    return List.of("Aerophotos cover Denmark only",
        "From www.geodanmark.dk -- CC-BY-4.0");
  }

  static void defineIn(TileContext ctx) {
    new GeoDanmark(ctx);
  }

  /**
   * A hardcoded bounding box based on what the dataset actually contains
   * photos of.
   */
  private static final AxisRect DENMARK = new AxisRect(
      Point.at(560922560, 324748672),
      Point.at(575166809, 341940864));

  @Override
  public TileBitmap loadTile(long tile, boolean allowDownload)
      throws TryDownloadLater {
    // Don't even try to download tiles that don't overlap with
    // Denmark (without Bornholm).
    if( !DENMARK.intersects(WebMercatorAddresser.rectOf(tile)) ) {
      int tilex = WebMercatorAddresser.tilex(tile);
      int tiley = WebMercatorAddresser.tiley(tile);
      return TileBitmap.blank((tilex^tiley) % 2 == 0 ? 0xFFCCBBBB : 0xFFCCAAAA);
    } else
      return super.loadTile(tile, allowDownload);
  }

  // ---------------------------------------------------------------------
  // hack to disable HTTPS cert validation

  @Override
  protected HttpClient makeHttpClient(TileContext ctx) {
    var builder = HttpClient.newBuilder();

    TrustManager[] alwaysTrustAll = new TrustManager[] {
        new X509TrustManager() {
          @Override
          public X509Certificate[] getAcceptedIssuers() { return null; }
          @Override
          public void checkClientTrusted(X509Certificate[] certs,
              String authType) { }
          @Override
          public void checkServerTrusted(X509Certificate[] certs,
              String authType) { }
        }
    };
    try {
      var sc = SSLContext.getInstance("SSL");
      sc.init(null, alwaysTrustAll, null);
      builder = builder.sslContext(sc);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      e.printStackTrace();
    }

    return builder.build();
  }

}
