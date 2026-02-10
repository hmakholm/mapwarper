package net.makholm.henning.mapwarper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.function.DoubleSupplier;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.gui.Exporter;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.files.FSCache;
import net.makholm.henning.mapwarper.gui.files.VectFile;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.projection.Affinoid;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.gui.projection.WarpedProjection;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.util.NiceError;
import net.makholm.henning.mapwarper.util.Regexer;

class WarpCommand extends Mapwarper.Command {

  public static final Path DEFAULT_OUTPUT = Path.of("warped.png");

  WarpCommand(Mapwarper common) {
    super(common);
  }

  double wantWarpFactor = 5;
  int wantMm = -1;
  double wantPx = 1;
  String backmap = "nomap";

  @Override
  protected boolean offerSpecialWord(Regexer re) {
    if( re.match(Regexer.cUnsigned + "x") ) {
      wantWarpFactor = re.dgroup(1);
      return true;
    } else if( re.match(Regexer.cNat + "mm") ) {
      wantMm = re.igroup(1);
      return true;
    } else if( re.match(Regexer.cUnsigned + "px") ) {
      wantPx = re.dgroup(1);
      return true;
    } else if( re.match("backmap=([a-z]+)") ) {
      backmap = re.group(1);
      return true;
    } else {
      return super.offerSpecialWord(re);
    }
  }

  @Override
  protected void run(Deque<String> words) {
    if( words.isEmpty() || !words.peekFirst().endsWith(VectFile.EXTENSION) )
      throw NiceError.of("Missing .vect file name");
    Path vectfileName = Path.of(words.removeFirst());
    if( !Files.isRegularFile(vectfileName) )
      throw NiceError.of("%s does not exist", vectfileName);

    theseShouldAllBeSpecial(words);

    FSCache fs = new FSCache();
    VectFile vf = fs.getFile(vectfileName);
    WarpedProjection wp;
    try {
      wp = new WarpedProjection(vf, fs);
    } catch( WarpedProjection.CannotWarp e ) {
      throw NiceError.of("Cannot use this for warping: %s", e.getMessage());
    }

    int zoom = common.wantedZoom.orElse(18);
    var tiles = common.tilesWithDefault("google");
    var fallbackTiles = common.tileContext.nomapTileset;

    Affinoid aff = new Affinoid();
    if( wantMm > 0 ) {
      aff.scaleAcross = wantMm * 0.001 *
          WebMercator.unitsPerMeter(vf.content().nodeTree.get().center().y);
    } else {
      aff.scaleAcross = wantPx * Coords.zoom2pixsize(zoom);
    }
    aff.squeeze = wantWarpFactor;
    aff.squeezable = true;

    var proj = wp.apply(aff);
    var inf = Double.POSITIVE_INFINITY;
    var rect = new AxisRect(Point.at(-inf, -inf), Point.at(inf, inf));
    rect = wp.shrinkToMargins(rect, proj.scaleAlong());
    rect = proj.projected2local(rect);

    int renderFlags =
        Toggles.SUPERSAMPLE.bit() |
        Toggles.DOWNLOAD.bit() |
        Toggles.BLANK_OUTSIDE_MARGINS.bit();

    LayerSpec ls = new LayerSpec() {
      @Override public DoubleSupplier windowDiagonal() { return () -> 1; }
      @Override public int targetZoom() { return zoom; }
      @Override public Projection projection() { return proj; }
      @Override public Tileset mainTiles() { return tiles; }
      @Override public int flags() { return renderFlags; }
      @Override public Tileset fallbackTiles() { return fallbackTiles; }
    };

    var done = new Semaphore(0);
    var renderThread = new Exporter.ExportRenderThread(ls, null, rect) {
      @Override
      protected void whenDone() {
        done.release();
      }
    };
    var sizeTrouble = renderThread.sizeTrouble();
    if( sizeTrouble != null )
      throw NiceError.of("%s", sizeTrouble);

    System.err.println("Warping "+renderThread.width+"x"+renderThread.height+" ...");
    var outfile = common.wantedOutput.orElse(DEFAULT_OUTPUT);
    renderThread.start(outfile);
    try {
      done.acquire();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

}