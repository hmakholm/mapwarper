package net.makholm.henning.mapwarper.gui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.gui.maprender.FrozenLayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.gui.swing.Command;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.gui.swing.TrackPainter;
import net.makholm.henning.mapwarper.util.AbortRendering;
import net.makholm.henning.mapwarper.util.BackgroundThread;

public class Exporter extends Command {

  private final boolean withTracks;

  public Exporter(Commands owner,
      String codename, String nicename, boolean withTracks) {
    super(owner, codename, nicename);
    this.withTracks = withTracks;
  }

  @Override
  public void invoke() {
    AxisRect rect;
    FrozenLayerSpec spec;
    if( mapView().lensRect != null ) {
      rect = mapView().lensRect.box;
      spec = new FrozenLayerSpec(mapView().dynamicLensSpec);
    } else {
      rect = new AxisRect(mapView().visibleArea);
      spec = new FrozenLayerSpec(mapView().dynamicMapLayerSpec);
    }

    TrackPainter tracks;
    if( !withTracks )
      tracks = null;
    else
      tracks = new TrackPainter(mapView(), mapView().currentVisible);

    var renderThread = new ExportRenderThread(spec, tracks, rect);

    var sizeTrouble = renderThread.sizeTrouble();
    if( sizeTrouble != null ) {
      owner.window.showErrorBox("%s", sizeTrouble);
      return;
    }

    var fc = owner.files.locatedFileChooser();
    fc.setFileFilter(new FileNameExtensionFilter("PNG file", "png"));
    fc.setDialogTitle("Export "+renderThread.width+" × "+renderThread.height+" pixels");
    if( fc.showSaveDialog(owner.window) != JFileChooser.APPROVE_OPTION )
      return;
    Path outfile = fc.getSelectedFile().toPath();

    String lastname = outfile.getFileName().toString();
    if( !lastname.endsWith(".png") )
      outfile = outfile.resolveSibling(lastname+".png");

    renderThread.start(outfile);
  }

  public static class ExportRenderThread extends BackgroundThread {
    private final LayerSpec spec;
    private final AxisRect rect;
    private final TrackPainter tracks;
    private final RenderFactory factory;
    private final long xmin, xmax, ymin, ymax;
    public final int width, height;
    private File outfile;

    boolean done;
    int x0;

    public ExportRenderThread(LayerSpec spec, TrackPainter tracks, AxisRect rect) {
      super("Export render");
      this.spec = spec;
      this.tracks = tracks;
      this.rect = rect;

      this.factory = spec.projection().makeRenderFactory(spec);

      ymin = Math.round(rect.ymin());
      ymax = Math.round(rect.ymax());
      height = (int)(ymax - ymin);

      xmin = Math.round(rect.xmin());
      xmax = Math.round(rect.xmax());
      width = (int)(xmax - xmin);
    }

    public String sizeTrouble() {
      long npixels = (long)height * width;
      if( width <=0 || height <= 0 |
          ymax != ymin+height ||
          xmax != xmin+width ||
          npixels > 300_000_000 ) {
        return String.format(Locale.ROOT, "Exported image would be %dx%d",
            width, height);
      } else
        return null;
    }

    public void start(Path outfile) {
      this.outfile = outfile.toFile();
      start();
    }

    @Override
    public void run() {
      System.err.printf("Exporting %dx%d to %s\n", width, height, outfile);

      var semaphore = new Semaphore(0);
      var bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      for( x0 = 0; x0 < width; ) {
        int w0 = x0+250 >= width ? width-x0 : 200 ;
        var worker = factory.makeWorker(new RenderTarget() {
          @Override public long left() { return xmin+x0; }
          @Override public long top() { return ymin; }
          @Override public int columns() { return w0; }
          @Override public int rows() { return height; }
          @Override public boolean isUrgent() { return true; }
          @Override public boolean eagerDownload() { return true; }
          @Override public void checkCanceled() { }

          boolean darken = Toggles.DARKEN_MAP.setIn(spec.flags()) &&
              tracks != null;
          int darkenMask = darken ? 0x003F3F3F : 0;

          @Override
          public void givePixel(int x, int y, int rgb) {
            bitmap.setRGB(x0+x, y, rgb - (darkenMask & (rgb >> 2)));
          }

          @Override
          public void pokeSchedulerAsync() {
            semaphore.release();
          }

          @Override public void isNowGrownUp() {}
        });
        try {
          while(true) {
            int priority = worker.priority();
            if( priority > 0 )
              worker.doSomeWork();
            else if( priority == 0 )
              semaphore.acquire();
            else
              break;
          }
        } catch( AbortRendering | InterruptedException e ) {
          e.printStackTrace();
          return;
        }

        x0 += w0;
        System.err.println("Rendered "+x0+" of "+width+" columns.");
      }

      if( tracks != null ) {
        Graphics2D g = SwingUtils.startPaint(bitmap.getGraphics());
        g.translate(-xmin, -ymin);
        tracks.paint(g, rect);
      }

      System.err.println("Writing to "+outfile+" ...");
      try {
        ImageIO.write(bitmap, "PNG", outfile);
        System.err.println("Done");
      } catch( IOException e ) {
        System.err.println("Writing failed: "+e.getMessage());
      }
      whenDone();
    }

    protected void whenDone() {
      // Nothing by default
    }
  }

}
