package net.makholm.henning.mapwarper;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
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

  public final Path basedir;
  public final TileContext tileContext;

  public Tileset wantedTiles;
  public Optional<Integer> wantedZoom = Optional.empty();
  public Optional<Path> wantedOutput = Optional.empty();

  private final Command command;
  private final Deque<String> words = new ArrayDeque<>();

  private Mapwarper(String[] args) {
    basedir = Paths.get(args[0]);
    Path tileCache = basedir.resolve("tilecache");

    String verb = null;
    int dashDashPos = -1;
    for( int i = 1; i<args.length; i++ ) {
      Regexer arg = new Regexer(args[i]);
      if( dashDashPos == -1 && arg.find("^-[^0-9]") ) {
        if( arg.is("--") ) {
          dashDashPos = words.size();
        } else if( arg.match("-z([0-9]+)") ) {
          wantedZoom = Optional.of(arg.igroup(1));
        } else if( arg.is("-o") ) {
          wantedOutput = Optional.of(Paths.get(args[++i]));
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
