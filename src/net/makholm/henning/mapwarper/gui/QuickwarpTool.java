package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.gui.overlays.ArrowOverlay;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.projection.BaseProjection;
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
    BaseProjection base = orig.base();
    Point basePoint;

    if( base.createAffine() == null ) {
      basePoint = orig.local2projected(pos);
    } else {
      if( !isQuickCommand(modifiers) ) return NO_RESPONSE;
      try {
        Point global = translator().local2global(pos);
        base = mapView().makeWarpedProjection();
        basePoint = base.createWorker().global2local(global);
      } catch( CannotWarp e ) {
        return NO_RESPONSE;
      }
    }

    var aff = orig.getAffinoid();
    aff.makeSqueezable(5);
    Projection proj = base.makeQuickwarp(basePoint, altHeld(modifiers), aff);

    if( proj.equals(orig) )
      return NO_RESPONSE;
    return why -> mapView().setProjection(proj);
  }

  @Override
  public ToolResponse dragResponse(Point start, Point end) {
    Projection orig = mapView().projection;

    var arrow = arrowGoesForward(start, end) ? start.to(end) : end.to(start);
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
        var aff = orig.getAffinoid();
        aff.makeSqueezable(5);
        var proj = new QuickWarp(a, dir).apply(aff);
        owner.mapView.setProjection(proj, midLocal);
      }
    };
  }

  private boolean arrowGoesForward(Point a, Point b) {
    Projection p = mapView().projection;
    var aff = p.base().getAffinoid();
    if( !aff.squeezable )
      return a.x < b.x;
    Point aproj = p.local2projected(a);
    Point bproj = p.local2projected(b);
    return (aproj.x < bproj.x) ^ (aff.quadrantsTurned != 0);
  }

}
