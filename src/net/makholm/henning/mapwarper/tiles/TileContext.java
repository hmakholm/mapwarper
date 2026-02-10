package net.makholm.henning.mapwarper.tiles;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.util.BackgroundThread;
import net.makholm.henning.mapwarper.util.XmlConfig;

public class TileContext {

  public final XmlConfig config;
  public final CacheDirLocator caches;
  public final HttpClient http;

  public final Map<String, Tileset> tilesets = new LinkedHashMap<>();
  public final Tileset nomapTileset;

  public final TileCache ramCache = new TileCache();

  public volatile Point downloadFocus = Point.at(Coords.EARTH_SIZE/2, Coords.EARTH_SIZE/2);

  public final Executor progressiveLoader =
      BackgroundThread.executor("Progressive tile loader");

  public TileContext(XmlConfig config, HttpClient http) {
    this.config = config;
    this.caches = new CacheDirLocator(config);
    this.http = http;
    this.nomapTileset = new NomapTiles(this);
    tilesets.put(nomapTileset.name, nomapTileset);
  }

}
