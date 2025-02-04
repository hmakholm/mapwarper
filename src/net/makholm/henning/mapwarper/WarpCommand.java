package net.makholm.henning.mapwarper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.function.DoubleSupplier;

import javax.imageio.ImageIO;

import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.files.FSCache;
import net.makholm.henning.mapwarper.gui.files.VectFile;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.gui.projection.WarpedProjection;
import net.makholm.henning.mapwarper.tiles.TileCache;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.util.AbortRendering;
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

    double scaleAcross;
    if( wantMm > 0 ) {
      scaleAcross = wantMm * 0.001 *
          WebMercator.unitsPerMeter(vf.content().nodeTree.get().center().y);
    } else {
      scaleAcross = wantPx * Coords.zoom2pixsize(zoom);
    }

    var proj = wp.withScaleAndSqueeze(scaleAcross,  wantWarpFactor);
    var rect = wp.getMargins(proj.scaleAcross(), proj.scaleAlong());

    int ymin = (int)Math.round(rect.ymin());
    int ymax = (int)Math.round(rect.ymax());
    int width = (int)Math.round(rect.width());
    int height = ymax-ymin;

    System.err.println("Warping "+width+"x"+height+" ...");
    var bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

    TileCache.alwaysDownloadImmediately = true;
    boolean[] done = { false };

    int renderFlags =
        Toggles.SUPERSAMPLE.bit() |
        Toggles.BLANK_OUTSIDE_MARGINS.bit();

    LayerSpec ls = new LayerSpec() {
      @Override public DoubleSupplier windowDiagonal() { return () -> 1; }
      @Override public int targetZoom() { return zoom; }
      @Override public Projection projection() { return proj; }
      @Override public Tileset mainTiles() { return tiles; }
      @Override public int flags() { return renderFlags; }
      @Override public Tileset fallbackTiles() { return fallbackTiles; }
    };

    RenderTarget rt = new RenderTarget() {
      @Override public long left() { return 0; }
      @Override public long top() { return ymin; }
      @Override public int columns() { return width; }
      @Override public int rows() { return height; }
      @Override public boolean isUrgent() { return true; }
      @Override public void checkCanceled() { }

      @Override
      public void givePixel(int x, int y, int rgb) {
        bitmap.setRGB(x, y, rgb);
      }

      @Override
      public void isNowGrownUp() {
        done[0] = true;
      }

      @Override
      public void pokeSchedulerAsync() { }
    };

    var renderWorker = proj.makeRenderFactory(ls).makeWorker(rt);
    try {
      while( renderWorker.priority() > 1 )
        renderWorker.doSomeWork();
    } catch( AbortRendering e ) {
      e.printStackTrace();
      throw NiceError.of("This shouldn't happen!");
    }
    if( !done[0] )
      System.err.println("Huh, the renderer doesn't think it's done yet.");

    var outfile = common.wantedOutput.orElse(DEFAULT_OUTPUT);
    System.err.println("Writing to "+outfile+" ...");
    try {
      ImageIO.write(bitmap, "PNG", outfile.toFile());
    } catch( IOException e ) {
      throw NiceError.of("Writing failed: "+e.getMessage());
    }
  }

}