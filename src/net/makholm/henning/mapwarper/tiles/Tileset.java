package net.makholm.henning.mapwarper.tiles;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.w3c.dom.Element;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.CoordsParser;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.util.NiceError;

public abstract class Tileset {

  public final TileContext context;
  public final Element xmldef;

  public final String name;
  public final String desc;
  public final List<String> blurb = new ArrayList<>();

  public final int guiTargetZoom;
  public final boolean isOverlayMap;
  public final boolean darkenMap;
  public final AxisRect boundingBox;

  protected final Path cacheRoot;
  protected final String webUrlTemplate;
  final TileDownloader downloader;

  /**
   * Create a pixel addresser for a particular resolution and area.
   *
   * The default implementation here must be overridden by tilesets that
   * don't use the usual OpenStreetMap-like tile structure.
   *
   * @param zoom indicates the resolution of the pixels we request. For pixel
   * sources whose underlying raster is in a roughly Mercator-like projection,
   * this indicates that there are 2^(zoom+8) pixels along any circle of latitude.
   * For other sources, the resolution should be the one among the available
   * ones whose scale is closest to one pixel per 2^(18-zoom)/3 meters
   * (These two definitions coincide at latitudes of about 56 degrees).
   *
   * @param globalRefpoint indicates a (global) that the addresser will be
   * used near. Some sources (in particular ones whose underlying raster is
   * already aligned with our global Web-Mercator coordinates) ignore this
   * parameter and produce an addresser that works correctly everywhere.
   * On the other hand, it may also precompute a transformation approximation
   * that may start producing imprecisions when we get far enough away from
   * the source point.
   *
   * (The rough guideline for imprecision is that at a distance of 4,000
   * source pixels from the reference point, the error should be less than
   * about 1/200 of the distance to the reference point. This standard is
   * selected such that rendering 256x256 tiles with squeezes of up to 32
   * should not produce visible jumps between tiles.)
   */
  public abstract PixelAddresser makeAddresser(int zoom, Point globalRefpoint);

  /**
   * <em>Loading</em> a tile should be a fairly cheap operation that involves
   * at most the local disk cache. It generally doesn't happen in the UI
   * thread, but does happen where it can block the map rendering thread.
   * It definitely shouldn't be waiting on network activity.
   *
   * This should be thread safe with respect to producing <em>different</em>
   * tiles, but the caller should guard against calls to produce the
   * <em>same</em> tile happening in parallel.
   *
   * On the other hand it is the tileset's responsibility to gracefully
   * reject attempts to load a tile that is currently being downloaded.
   *
   * @param tile as returned by {@link PixelAddresser#locate(double, double)}
   * of the addressing objects created by {@link #makeAddresser(int, Point)}.
   */
  protected abstract TileBitmap loadTile(long tile) throws IOException;

  /**
   * Downloading is a potentially slow operation. It doesn't return an
   * actual tile, but merely makes it possible to load the tile after
   * it returns.
   *
   * Currently there's only ever one thread doing downloads for a single
   * tileset.
   */
  protected abstract void downloadTile(long tile)
      throws IOException, TryDownloadLater;

  public abstract String tilename(long tile);

  /**
   * This default implementation supports generating a web url by
   * substitutions. It can be overridden for map sources that need
   * ad-hoc parameters.
   */
  public String webUrlFor(int zoom, long pos) {
    if( webUrlTemplate == null )
      return null;
    var xyStrings = WebMercator.signedRoundedDegrees(zoom, pos);
    return webUrlTemplate
        .replace("[LAT]", xyStrings[0])
        .replace("[LON]", xyStrings[1])
        .replace("[Z]", Integer.toString(zoom));
  }

  // -----------------------------------------------------

  public static Tileset create(TileContext ctx, String name, Element xml) {
    String type = xml.getAttribute("type");
    switch(type) {
    case "": return new CommonWebTileset(ctx, name, xml);
    case "bing": return new Bing(ctx, name, xml);
    case "geodk": return new GeoDanmark(ctx, name, xml);
    default: throw new DontUseThisTileset("Unknown tilset type '"+type+"'");
    }
  }

  public static void defineStandardTilesets(TileContext ctx) {
    ctx.config.tagmap("tileset").forEach((name, xml) -> {
      try {
        Tileset tiles = create(ctx, name, xml);
        if( tiles != null )
          ctx.tilesets.put(name, tiles);
      } catch( DontUseThisTileset e ) {
        if( ctx.config.verbose )
          System.err.println("Cannot construct tileset "+name+
              ": "+e.getMessage());
      }
    });
  }

  @SuppressWarnings("serial")
  protected static class DontUseThisTileset extends RuntimeException {
    DontUseThisTileset(String msg) {
      super(msg);
    }
  }

  protected Tileset(TileContext ctx, String name, Element xml) {
    this.context = ctx;
    this.xmldef = xml;
    this.name = name;
    this.desc = xml.getAttribute("desc");
    if( desc.isEmpty() ) throw new DontUseThisTileset("no desciption in XML");

    this.webUrlTemplate = stringAttr("weburl", null);
    this.cacheRoot = ctx.caches.forTileset(this);

    this.guiTargetZoom = intAttr("guiTargetZoom", 16);
    this.isOverlayMap = "true".equals(xml.getAttribute("lensOnly"));
    this.darkenMap = "true".equals(xml.getAttribute("darkenMap"));

    AxisRect bbox = null;
    var content = xml.getChildNodes();
    for( int i=0; i<content.getLength(); i++ ) {
      if( content.item(i) instanceof Element child ) {
        switch( child.getTagName() ) {
        case "blurb":
          blurb.add(child.getTextContent());
          break;
        case "boundingBox":
          var re = new CoordsParser(child.getTextContent().strip());
          if( re.match("([0-9]+)::([0-9]+)") ) {
            bbox = AxisRect.extend(bbox, Point.at(re.igroup(1), re.igroup(2)));
          } else {
            double[] geo = re.parseGeoreference();
            if( geo != null ) {
              var p = Point.at(WebMercator.fromLatlon(geo));
              bbox = AxisRect.extend(bbox, p);
            } else {
              System.err.println("Ignoring unrecognized boundingbox point for "
                  +name+": "+re.full);
            }
          }
          break;
        default:
          break;
        }
      }
    }
    boundingBox = bbox;
    downloader = new TileDownloader(this);
  }

  protected String stringAttr(String attr, String defval) {
    if( !xmldef.hasAttribute(attr) )
      return defval;
    else
      return xmldef.getAttribute(attr);
  }

  protected int intAttr(String attr, int defval) {
    if( !xmldef.hasAttribute(attr) )
      return defval;
    String s = xmldef.getAttribute(attr);
    try {
      return Integer.parseInt(s);
    } catch( NumberFormatException e ) {
      throw NiceError.of("tileset parameter %s.%s is not an integer: '%s'",
          name, attr, s);
    }
  }

  protected String withApikey(String template) {
    if( template == null ) return template;
    int i = template.indexOf("[APIKEY]");
    if( i < 0 ) return template;
    String key = context.config.string("apiKey", name);
    if( key == null )
      throw new DontUseThisTileset("Missing API key");
    else
      return template.substring(0,i)+key+template.substring(i+8);
  }

  protected HttpClient makeHttpClient() {
    if( "true".equals(xmldef.getAttribute("disableHttpsValidation")) ) {
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
    } else {
      return context.http;
    }
  }

}
