package net.makholm.henning.mapwarper.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.gui.maprender.FrozenLayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.gui.swing.Command;
import net.makholm.henning.mapwarper.util.AbortRendering;

class Exporter extends Command {

  public Exporter(Commands owner) {
    super(owner, "export", "Export current view (or lens) ...");
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
    long ymin = Math.round(rect.ymin());
    long ymax = Math.round(rect.ymax());
    int height = (int)(ymax - ymin);

    long xmin = Math.round(rect.xmin());
    long xmax = Math.round(rect.xmax());
    int width = (int)(xmax - xmin);

    long npixels = (long)height * width;

    if( width <=0 || height <= 0 |
        ymax != ymin+height ||
        xmax != xmin+width ||
        npixels > 300_000_000 ) {
      owner.window.showErrorBox("Exported image would be %dx%d",
          width, height);
      return;
    }

    var factory = mapView().projection.makeRenderFactory(spec);

    var fc = owner.files.locatedFileChooser();
    fc.setFileFilter(new FileNameExtensionFilter("PNG file", "png"));
    fc.setDialogTitle("Export "+width+" × "+height+" pixels");
    if( fc.showSaveDialog(owner.window) != JFileChooser.APPROVE_OPTION )
      return;
    File outfile = fc.getSelectedFile();

    new Thread("Exporter") {
      boolean done;
      int x0;

      @Override
      public void run() {
        System.err.printf("Exporting %dx%d to %s\n", width, height, outfile);

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

            @Override
            public void givePixel(int x, int y, int rgb) {
              bitmap.setRGB(x0+x, y, rgb);
            }

            @Override
            public void isNowGrownUp() {
              done = true;
            }

            @Override
            public void pokeSchedulerAsync() {}
          });
          done = false;
          try {
            while( worker.priority() > 1 )
              worker.doSomeWork();
          } catch( AbortRendering e ) {
            e.printStackTrace();
            return;
          }
          if( !done ) {
            System.err.println("Huh, the renderer doesn't think it is done yet");
          }

          x0 += w0;
          System.err.println("Rendered "+x0+" of "+width+" columns.");
        }

        System.err.println("Writing to "+outfile+" ...");
        try {
          ImageIO.write(bitmap, "PNG", outfile);
          System.err.println("Done");
        } catch( IOException e ) {
          System.err.println("Writing failed: "+e.getMessage());
        }
      }
    }.start();
  }

}
