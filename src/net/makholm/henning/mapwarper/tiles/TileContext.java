package net.makholm.henning.mapwarper.tiles;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.makholm.henning.mapwarper.util.XmlConfig;

public class TileContext {

  public final XmlConfig config;
  public final CacheDirLocator caches;
  public final HttpClient http;

  public boolean includeIffyTilesets;
  public final Map<String, Tileset> tilesets = new LinkedHashMap<>();
  public final Tileset nomapTileset;

  public AtomicInteger diskCacheHits = new AtomicInteger(0);
  public AtomicInteger downloadedTiles = new AtomicInteger(0);
  public AtomicLong downloadedBytes = new AtomicLong(0);

  public final TileCache ramCache = new TileCache();
  public final TileDownloader downloader = new TileDownloader();

  public TileContext(XmlConfig config, HttpClient http) {
    this.config = config;
    this.caches = new CacheDirLocator(config);
    this.http = http;
    this.nomapTileset = new NomapTiles(this);
  }

  public void forgetUnusableTilesets() {
    for( var it = tilesets.values().iterator(); it.hasNext(); ) {
      var tiles = it.next();
      if( !tiles.okayToUse() )
        it.remove();
    }
  }

}
