package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.overlays.BoxOverlay;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.util.MathUtil;

public class ZoomTool extends ProjectionSwitchingTool {

  protected ZoomTool(Commands owner) {
    super(owner, "zoom", "Zoom");
  }

  @Override
  protected ToolResponse clickResponse(Point pos, int modifiers) {
    return qhy -> {
      Runnable got = prepareZoom(ctrlHeld(modifiers) ? 4 : 0.25);
      if( got != null ) {
        got.run();
        enableSameKeyCancel();
      }
    };
  }

  @Override
  protected ToolResponse dragResponse(Point pos0, Point pos1) {
    AxisRect chosen = new AxisRect(pos0, pos1);
    var overlay = new BoxOverlay(chosen, 0x0066FF);
    return new ToolResponse() {
      @Override public BoxOverlay previewOverlay() { return overlay; }

      @Override
      public void execute(ExecuteWhy why) {
        var visible = new AxisRect(mapView().visibleArea);
        if( !visible.contains(pos1) ) {
          SwingUtils.beep();
          return;
        }

        double oldScale = curScale();
        double newScale = oldScale * Math.max(
            chosen.width() / visible.width(),
            chosen.height() / visible.height());
        newScale = Math.max(newScale, getMinScale());
        if( mustBePowerOf2() )
          newScale = Math.scalb(1, 1+Math.getExponent(newScale));
        if( newScale >= oldScale )
          return;

        var newLocalCenter = chosen.center().scale(oldScale/newScale);

        mapView().setProjectionOnly(projection().withScaleAcross(newScale));
        mapView().positionX = (long)(newLocalCenter.x - visible.width()/2);
        mapView().positionY = (long)(newLocalCenter.y - visible.height()/2);
        enableSameKeyCancel();
      }
    };
  }

  public Runnable zoomOutCommand() {
    return prepareZoom(mustBePowerOf2() ? 2 : Math.sqrt(2));
  }

  public Runnable zoomInCommand() {
    return prepareZoom(mustBePowerOf2() ? 0.5 : Math.sqrt(0.5));
  }

  private Runnable prepareZoom(double factor) {
    double oldScale = curScale();
    double newScale;
    if( factor > 1 )
      newScale = Math.min(getMaxScale(), oldScale * factor);
    else
      newScale = Math.max(getMinScale(), oldScale * factor);
    if( newScale == oldScale )
      return null;
    else
      return () -> setScale(newScale);
  }

  void zoom100Command() {
    setScale(Coords.zoom2pixsize(mapView().mainTiles.guiTargetZoom()));
  }

  private Projection projection() {
    return mapView().projection;
  }

  private boolean mustBePowerOf2() {
    return projection().base().isOrtho();
  }

  private double getMinScale() {
    return 1/8.0;
  }

  private double getMaxScale() {
    var va = mapView().visibleArea;
    AxisRect projlimits = projection().maxUnzoom();
    double ideal = curScale() * Math.min(
        projlimits.width() / (va.right - va.left),
        projlimits.height() / (va.bottom - va.top));
    if( mustBePowerOf2() )
      return Math.scalb(1, Math.getExponent(ideal));
    else
      return ideal;
  }

  private double curScale() {
    return projection().scaleAcross();
  }

  private void setScale(double newScale) {
    if( mustBePowerOf2() )
      newScale = Math.scalb(1, (int)Math.round(MathUtil.log2(newScale)));
    mapView().setProjection(projection().withScaleAcross(newScale));
  }

}
