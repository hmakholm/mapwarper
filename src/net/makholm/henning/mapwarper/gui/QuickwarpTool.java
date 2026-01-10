package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.gui.overlays.ArrowOverlay;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.gui.projection.QuickWarp;
import net.makholm.henning.mapwarper.gui.projection.WarpedProjection.CannotWarp;
import net.makholm.henning.mapwarper.gui.swing.Command;

public class QuickwarpTool extends ProjectionSwitchingTool {

  protected QuickwarpTool(Commands owner) {
    super(owner, "quickwarp", "Quick-warp");
  }

  public Command quickLinear() {
    return bareQuickCommand("Linear warp extrapolation");
  }

  public Command quickCircle() {
    return altQuickCommand("Circular warp extrapolation");
  }

  @Override
  protected ToolResponse clickResponse(Point pos, int modifiers) {
    Projection orig = mapView().projection;
    if( orig.base().createAffine() != null ) {
      if( !isQuickCommand(modifiers) ) return NO_RESPONSE;
      try {
        Point global = translator().local2global(pos);
        orig = mapView().makeScaledWarpedProjection(null);
        pos = orig.createWorker().global2local(global);
      } catch( CannotWarp e ) {
        return NO_RESPONSE;
      }
    }
    Projection proj= orig.makeQuickwarp(pos, altHeld(modifiers));
    if( proj.equals(orig) )
      return NO_RESPONSE;
    return why -> {
      mapView().setProjection(proj);
      enableSameKeyCancel();
    };
  }

  @Override
  public ToolResponse dragResponse(Point start, Point end) {
    Projection orig = mapView().projection;

    Point projStart = start;
    Point projEnd = end;
    if( orig.scaleAndSqueezeSimilarly(orig.base()).equals(orig) ) {
      projStart = orig.local2projected(projStart);
      projEnd = orig.local2projected(projEnd);
    }
    if( projStart.x > projEnd.x ) {
      Point tmp = start;
      start = end;
      end = tmp;
    }

    var arrow = start.to(end);
    if( arrow.length() < 5 )
      return NO_RESPONSE;

    var overlay = new ArrowOverlay(arrow, 0x0066FF);
    return new ToolResponse() {
      @Override public VectorOverlay previewOverlay() { return overlay; };

      @Override
      public void execute(ExecuteWhy why) {
        var translator = owner.mapView.translator();
        var midLocal = arrow.a.interpolate(0.5, arrow.b);
        Point a = translator.local2global(arrow.a);
        Point b = translator.local2global(arrow.b);
        UnitVector dir = a.to(b).normalize();
        var quickwarp = new QuickWarp(a, dir);
        var proj = orig.scaleAndSqueezeSimilarly(quickwarp);
        if( proj.getSqueeze() < 5 )
          proj = proj.withSqueeze(5);
        owner.mapView.setProjection(proj, midLocal);
        enableSameKeyCancel();
      }
    };
  }

}
