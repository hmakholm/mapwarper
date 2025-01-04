package net.makholm.henning.mapwarper.tiles;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TileContext {

  public final Path tileCache;
  public final HttpClient http;

  public boolean includeIffyTilesets;
  public final Map<String, Tileset> tilesets = new LinkedHashMap<>();
  public final Tileset nomapTileset;

  public AtomicInteger diskCacheHits = new AtomicInteger(0);
  public AtomicInteger downloadedTiles = new AtomicInteger(0);
  public AtomicLong downloadedBytes = new AtomicLong(0);

  public final TileCache ramCache = new TileCache();
  public final TileDownloader downloader = new TileDownloader();

  public TileContext(Path tileCache, HttpClient http) {
    this.tileCache = tileCache;
    this.http = http;
    this.nomapTileset = new NomapTiles(this);
  }

}
