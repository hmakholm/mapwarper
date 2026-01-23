package net.makholm.henning.mapwarper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.CoordsParser;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.tiles.TileContext;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.NiceError;
import net.makholm.henning.mapwarper.util.Regexer;

public class Mapwarper {

  public final TileContext tileContext;

  public Tileset wantedTiles;
  public Optional<Integer> wantedZoom = Optional.empty();
  public Optional<Path> wantedOutput = Optional.empty();

  private String explicitTileCacheArg;
  private Path tileCache;

  private final Command command;
  private final Deque<String> words = new ArrayDeque<>();

  private Mapwarper(String[] args) {
    String verb = null;
    int dashDashPos = -1;
    for( int i = 0; i<args.length; i++ ) {
      Regexer arg = new Regexer(args[i]);
      if( dashDashPos == -1 && arg.find("^-[^0-9]") ) {
        if( arg.is("--") ) {
          dashDashPos = words.size();
        } else if( arg.match("-z([0-9]+)") ) {
          wantedZoom = Optional.of(arg.igroup(1));
        } else if( arg.is("-o") ) {
          wantedOutput = Optional.of(Paths.get(args[++i]));
        } else if( arg.is("--tilecache") ) {
          explicitTileCacheArg = args[++i];
        } else if( arg.match("--tilecache=(.+)") ) {
          explicitTileCacheArg = arg.group(1);
        } else {
          throw NiceError.of("Unknown option: %s", arg);
        }
      } else if( verb == null ) {
        verb = arg.full;
      } else {
        words.add(arg.full);
      }
    }
    if( dashDashPos == -1 ) dashDashPos = words.size();

    if( !findTileCache() )
      System.exit(1);

    var http = HttpClient.newBuilder();
    tileContext = new TileContext(tileCache, http.build());
    Tileset.defineStandardTilesets(tileContext);

    if( verb == null ) {
      command = new GuiCommand(this);
    } else if( !VERBS.containsKey(verb) ) {
      command = new GuiCommand(this);
      words.addFirst(verb);
    } else {
      command = VERBS.get(verb).apply(this);
    }

    while( dashDashPos > 0 &&
        command.offerSpecialWord(new Regexer(words.getFirst())) ) {
      words.removeFirst();
      dashDashPos--;
    }
  }

  private static final String[] xdgCacheAddress = { "mapwarper" };
  private static final String[] macCacheAddress = { "net.makholm.henning.mapwarper" };
  private static final String[] winCacheAddress = { "Mapwarper" };

  private boolean findTileCache() {
    if( explicitTileCacheArg != null )
      return useOrCreateTileCache(Path.of(explicitTileCacheArg));

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
      if( explicitTileCacheArg == null )
        System.err.println("Using tile cache at "+p);
      return true;
    } else  {
      return false;
    }
  }

  private boolean perhapsSetWantedTileset(String word) {
    if( tileContext.tilesets.containsKey(word) ) {
      wantedTiles = tileContext.tilesets.get(word);
      return true;
    }
    return false;
  }

  static abstract class Command {
    protected final Mapwarper common;
    Command(Mapwarper common) {
      this.common = common;
    }

    protected boolean offerSpecialWord(Regexer re) {
      if( common.perhapsSetWantedTileset(re.full) )
        return true;

      if( re.match("([a-z]+)([0-9]+)") &&
          common.perhapsSetWantedTileset(re.group(1)) ) {
        if( common.wantedZoom.isEmpty() )
          common.wantedZoom = Optional.of(re.igroup(2));
        return true;
      }

      return false;
    }

    protected void theseShouldAllBeSpecial(Deque<String> words) {
      while( !words.isEmpty() ) {
        String s = words.removeFirst();
        if( !offerSpecialWord(new Regexer(s)) )
          throw NiceError.of("Unrecognized option: '%s'", s);
      }
    }

    protected abstract void run(Deque<String> words);
  }

  private static Map<String, Function<Mapwarper, Command>> VERBS =
      new LinkedHashMap<>();
  static {
    VERBS.put("warp", WarpCommand::new);
    VERBS.put("maplink", MaplinkCommand::new);
    VERBS.put("tileurl", TileurlCommand::new);
    VERBS.put("downloadtile", DownloadtileCommand::new);
    VERBS.put("gui", GuiCommand::new);
  }

  public static void main(String[] args) {
    try {
      Mapwarper me = new Mapwarper(args);
      try {
        me.command.run(me.words);
      } finally {
        if( me.tileContext.diskCacheHits.get() != 0 ) {
          System.err.println(me.tileContext.diskCacheHits +
              " tiles hit in disk cache");
        }
        if( me.tileContext.downloadedTiles.get() != 0 ) {
          System.err.println("Downloaded "+
              me.tileContext.downloadedBytes + " bytes in "+
              me.tileContext.downloadedTiles + " tiles.");
        }
      }
    } catch( NiceError e ) {
      System.err.println(e.getMessage());
      System.exit(1);
    } catch( BadError e ) {
      e.printStackTrace();
    }
  }

  long parsePoint(Collection<String> words) {
    String input = "";
    for( String word : words )
      input = input == "" ? word : input + " " + word;

    double[] latlon = CoordsParser.parseGeoreference(input);
    if( latlon == null )
      throw NiceError.of("Could not parse point spec \"%s\"", input);
    if( latlon.length >= 3 && wantedZoom.isEmpty() )
      wantedZoom = Optional.of((int)latlon[2]);
    long pos = WebMercator.fromLatlon(latlon);

    int z = wantedZoom.orElse(22);
    String[] decimal = Coords.signedRoundedDegrees(z, latlon);
    System.err.println("Parsed point as: "+Coords.roundedDMS(z, latlon));
    System.err.println("                 "+decimal[0]+", "+decimal[1]);
    System.err.println("                 "+Coords.wprint(pos));
    return pos;
  }

  // -----------------------------------------------------------------------

  public Tileset tilesWithDefault(String defaultName) {
    if( wantedTiles != null ) {
      return wantedTiles;
    } else if( defaultName == null ) {
      throw NiceError.of("A tileset must be specified");
    } else if( tileContext.tilesets.containsKey(defaultName) ) {
      return tileContext.tilesets.get(defaultName);
    } else {
      throw BadError.of("Missing default tileset '%s'", defaultName);
    }
  }

}
