package net.makholm.henning.mapwarper.tiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.makholm.henning.mapwarper.util.NiceError;
import net.makholm.henning.mapwarper.util.XmlConfig;

public class CacheDirLocator {

  private final XmlConfig config;
  private Path tileCache;
  public final Path root;

  public CacheDirLocator(XmlConfig config) {
    this.config = config;
    if( !findRoot() )
      throw NiceError.of("Could not find tile cache");
    root = tileCache;
  }

  public Path forTileset(Tileset tiles) {
    String s = config.string("tilecache", tiles.name);
    if( s != null )
      return Path.of(s);
    else
      return root.resolve(tiles.name);
  }

  private static final String[] xdgCacheAddress = { "mapwarper" };
  private static final String[] macCacheAddress = { "net.makholm.henning.mapwarper" };
  private static final String[] winCacheAddress = { "Mapwarper" };

  private boolean findRoot() {
    String fromConfig = config.string("tilecache", "");
    if( fromConfig != null )
      return useOrCreateTileCache(Path.of(fromConfig));

    List<Path> deferred = new ArrayList<>();

    if( tryCacheDir(System.getenv("XDG_CACHE_HOME"), xdgCacheAddress, null) )
      return true;

    if( tryCacheDir(System.getenv("LOCALAPPDATA"), winCacheAddress, null) )
      return true;

    String s = System.getProperty("user.home");
    Path home = s == null || s.isEmpty() ? null : Path.of(s);
    if( home != null ) {
      // Try the Mac and Windows locations first -- it's more likely that one of
      // those will have a spurious ~/.cache than the other way around.

      // https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/FileSystemOverview/FileSystemOverview.html
      if( tryCacheDir(home.resolve("Library/Caches"), macCacheAddress, deferred) ) return true;

      // Web rumors say this is the equivalent for Windows; untested!
      if( tryCacheDir(home.resolve("AppData").resolve("Local"), winCacheAddress, deferred) ) return true;

      if( tryCacheDir(home.resolve(".cache"), xdgCacheAddress, deferred) ) return true;
    }

    if( useTileCacheIfExists(Path.of("tilecache").toAbsolutePath()) )
      return true;

    for( var dp : deferred )
      if( useOrCreateTileCache(dp) )
        return true;
    return false;
  }

  private boolean tryCacheDir(String s, String[] addr, List<Path> fallbacks) {
    if( s != null && !s.isEmpty() )
      return tryCacheDir(Path.of(s), addr, fallbacks);
    else
      return false;
  }

  /**
   * @param fallbacks is null if we're so sure it's a good directory <em>name</em>
   * that we'll create our cache directory there even if the enclosing directory
   * doesn't exist yet.
   */
  private boolean tryCacheDir(Path cache, String[] addr, List<Path> fallbacks) {
    Path p = cache;
    for( var component : addr )
      p = p.resolve(component);
    p = p.resolve("tilecache");
    if( useTileCacheIfExists(p) ) {
      return true;
    } else if( fallbacks == null ) {
      return useOrCreateTileCache(p);
    } else if( Files.exists(cache) ) {
      fallbacks.add(p);
      return false;
    } else if( cache.getFileName().toString().equals(".cache") ) {
      // Always be prepared to use ~/.cache as fallback
      fallbacks.add(p);
      return false;
    } else {
      return false;
    }
  }

  private boolean useOrCreateTileCache(Path p) {
    if( useTileCacheIfExists(p) ) {
      return true;
    } else if( Files.exists(p) ) {
      System.err.println(p+" exists but is not a directory");
      return false;
    } else {
      try {
        Files.createDirectories(p);
        System.err.println("Created new tile cache at "+p);
        tileCache = p;
        return true;
      } catch( IOException e ) {
        System.err.println("Cannot create tile cache at "+p+": "+e);
        return false;
      }
    }
  }

  private boolean useTileCacheIfExists(Path p) {
    if( Files.isDirectory(p) ) {
      tileCache = p;
      return true;
    } else  {
      return false;
    }
  }


}
