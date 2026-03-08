package net.makholm.henning.mapwarper.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.util.AbortRendering;
import net.makholm.henning.mapwarper.util.ListMapper;
import net.makholm.henning.mapwarper.util.SingleMemo;

public class SupersampleDebugger extends Tool {

  protected SupersampleDebugger(Commands owner) {
    super(owner, "supersampleDebugger", "Supersampling debugger");
  }

  private Function<ProjectionWorker, VectorOverlay> currentSample;

  @Override
  public void whenSelected() {
    super.whenSelected();
    if( currentSample == null )
      currentSample = takeNewSamples(mapView().mouseLocal);
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    return new ToolResponse() {
      @Override
      public VectorOverlay previewOverlay() {
        return currentSample==null ? null : currentSample.apply(translator());
      }
      @Override
      public void execute(ExecuteWhy why) {
        currentSample = takeNewSamples(pos);
      }
    };
  }

  @Override
  public ToolResponse outsideWindowResponse() {
    return new ToolResponse() {
      @Override
      public VectorOverlay previewOverlay() {
        return currentSample==null ? null : currentSample.apply(translator());
      }
      @Override
      public void execute(ExecuteWhy why) { }
    };
  }

  private Function<ProjectionWorker, VectorOverlay> takeNewSamples(Point p) {
    var target = new SampleTarget(p);
    var factory = mapView().projection.makeRenderFactory(
        mapView().dynamicMapLayerSpec);
    var worker = factory.makeWorker(target);
    while( worker.priority() > 0 ) {
      try {
        worker.doSomeWork();
      } catch (AbortRendering e) {
        e.printStackTrace(System.out);
        return null;
      }
    }
    target.recordPixelOutlines(translator());
    return SingleMemo.of(ProjectionWorker::projection, target::makeOverlay);
  }

  public static class SampleTarget implements RenderTarget {
    final long left, top;

    SampleTarget(Point local) {
      left = (long)Math.floor(local.x) & -8L;
      top = (long)Math.floor(local.y) & -8L;
    }

    final Map<Long, List<Point>> allSamplePoints = new HashMap<>();
    final Map<Long, Point[]> pixelOutlines = new HashMap<>();
    List<Point> recentSamplePoints;

    @Override public long left() { return left; }
    @Override public long top() { return top; }
    @Override public int columns() { return 8; }
    @Override public int rows() { return 8; }
    @Override public boolean isUrgent() { return false; }
    @Override public void checkCanceled() { }
    @Override public void pokeSchedulerAsync() { }

    public int sample(double x, double y) {
      if( recentSamplePoints == null ) recentSamplePoints = new ArrayList<>();
      recentSamplePoints.add(Point.at(x,y));
      return -1;
    }

    @Override
    public void givePixel(int x, int y, int rgb) {
      if( recentSamplePoints == null ) {
        System.out.println("Oops! No samples for "+x+","+y);
        return;
      }
      Long coord = Coords.wrap((int)(left+x), (int)(top+y));
      if( allSamplePoints.containsKey(coord) )
        System.out.println("Oops! Got pixel "+x+","+y+" multiple times!");
      allSamplePoints.put(coord, recentSamplePoints);
      recentSamplePoints = null;
    }

    @Override
    public void isNowGrownUp() {
      if( recentSamplePoints != null ) {
        System.out.println("Oops! "+recentSamplePoints+
            " samples were not used for anything.");
        recentSamplePoints = null;
      }
    }


    void recordPixelOutlines(ProjectionWorker t) {
      for( var coords : allSamplePoints.keySet() )
        pixelOutlines.put(coords, pixelOutline(coords, t));
    }

    static Point[] pixelOutline(long pos, ProjectionWorker t) {
      var x = Coords.x(pos);
      var y = Coords.y(pos);
      return new Point[] {
          t.local2global(Point.at(x+0.0, y+0.0)),
          t.local2global(Point.at(x+0.3, y+0.0)),
          t.local2global(Point.at(x+0.7, y+0.0)),
          t.local2global(Point.at(x+1.0, y+0.0)),
          t.local2global(Point.at(x+1.0, y+0.3)),
          t.local2global(Point.at(x+1.0, y+0.7)),
          t.local2global(Point.at(x+1.0, y+1.0)),
          t.local2global(Point.at(x+0.7, y+1.0)),
          t.local2global(Point.at(x+0.3, y+1.0)),
          t.local2global(Point.at(x+0.0, y+1.0)),
          t.local2global(Point.at(x+0.0, y+0.7)),
          t.local2global(Point.at(x+0.0, y+0.3)),
      };
    }

    VectorOverlay makeOverlay(ProjectionWorker t) {
      var localSamples = new HashMap<Long, List<Point>>();
      var localOutline = new HashMap<Long, Bezier[]>();

      AxisRect boundingBox = null;
      for( var p : allSamplePoints.keySet() ) {
        List<Point> local =
            ListMapper.map(allSamplePoints.get(p), t::global2local);
        localSamples.put(p, local);
        for( var pp : local )
          boundingBox = new AxisRect(pp).union(boundingBox);
      }
      boundingBox = boundingBox.grow(10);

      for( var p : pixelOutlines.keySet() ) {
        var outline = pixelOutlines.get(p);
        var curves = new Bezier[4];
        for( int i=0; i<4; i++ ) {
          curves[i] = Bezier.through(
              t.global2local(outline[ 3*i+0    ]),
              t.global2local(outline[ 3*i+1    ]),
              t.global2local(outline[ 3*i+2    ]),
              t.global2local(outline[(3*i+3)%12]));
          boundingBox = curves[i].bbox.get().union(boundingBox);
        }
        localOutline.put(p, curves);
      }

      var finalBbox = boundingBox;
      return new VectorOverlay() {
        @Override
        public AxisRect boundingBox() {
          return finalBbox;
        }

        @Override
        public void paint(Graphics2D g) {
          final Path2D.Double path = new Path2D.Double();
          localOutline.forEach((pos, outline) -> {
            g.setColor(new Color(checkerColor(pos), true));
            path.reset();
            path.moveTo(outline[0].p1.x, outline[0].p1.y);
            for( var curve : outline )
              path.curveTo(curve.p2.x, curve.p2.y,
                  curve.p3.x, curve.p3.y,
                  curve.p4.x, curve.p4.y);
            path.closePath();
            g.fill(path);
          });
          g.setStroke(new BasicStroke(OVERLAY_LINEWIDTH));
          localSamples.forEach((pos, samples) -> {
            var rgb = checkerColor(pos);
            rgb -= ((rgb >> 3) & 0x1F1F1F)*3;
            g.setColor(new Color(rgb));
            path.reset();
            for( var p : samples ) {
              path.moveTo(p.x-4, p.y-4);
              path.lineTo(p.x+4, p.y+4);
              path.moveTo(p.x+4, p.y-4);
              path.lineTo(p.x-4, p.y+4);
            }
            g.draw(path);
          });
        }
      };
    }

    private static int checkerColor(long pos) {
      int i = (Coords.y(pos) & 3) ^ (Coords.x(pos) & 1)*2;
      return CHECKER_COLORS[i] | 0x90000000;
    }

  }

  private static final int[] CHECKER_COLORS =
    { 0xFF0000, 0x008800, 0x0000EE, 0xDDDD00 };

}
