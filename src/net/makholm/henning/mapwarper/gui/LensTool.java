package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.maprender.FallbackChain;
import net.makholm.henning.mapwarper.gui.overlays.BoxOverlay;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.gui.swing.Tool;

public class LensTool extends Tool {
  protected LensTool(Commands owner) {
    super(owner, "lens", "Lens");
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    BoxOverlay currentLens = mapView().lensRect;
    if( currentLens == null )
      return NO_RESPONSE;
    else if( currentLens.box.contains(pos) ) {
      var r = ctrlHeld(modifiers) ? lensMinusCommand() : lensPlusCommand();
      return r == null ? NO_RESPONSE : () -> r.run();
    } else {
      return () -> mapView().cancelLens();
    }
  }

  private boolean zoomMattersAnyway() {
    return mapView().currentTool instanceof TilecacheDebugTool;
  }

  public Runnable lensPlusCommand() {
    if( (mapView().lensRect == null || mapView().lensZoom >= naturalZoom())
        && !zoomMattersAnyway() )
      return null;
    else if( mapView().lensZoom >= 20 )
      return SwingUtils::beep;
    else
      return () -> {
        var lensRect = mapView().lensRect;
        if( lensRect != null ) {
          AxisRect visible = new AxisRect(mapView().visibleArea);
          AxisRect intersection = lensRect.box.intersect(visible);
          if( intersection == null ) {
            mapView().cancelLens();
          } else if( intersection != lensRect.box ) {
            mapView().lensRect = createBox(intersection);
          }
        }
        mapView().lensZoom++;
        System.err.println("zoom now "+mapView().lensZoom);
        owner.swing.invalidateToolResponse();
      };
  }

  public Runnable lensMinusCommand() {
    if( mapView().lensZoom <= 10 && !zoomMattersAnyway() )
      return null;
    else
      return () -> {
        mapView().lensZoom--;
        System.err.println("zoom now "+mapView().lensZoom);
        owner.swing.invalidateToolResponse();
      };
  }

  @Override
  public MouseAction drag(Point pos0, int modifiers0) {
    mapView().cancelLens();
    return (pos1, modifiers1) -> {
      AxisRect chosen = new AxisRect(pos0, pos1);
      var overlay = createBox(chosen);
      return new ToolResponse() {
        @Override
        public BoxOverlay previewOverlay() {
          return overlay;
        }
        @Override
        public void execute() {
          var visible = mapView().visibleArea;
          if( chosen.xmin() >= visible.left &&
              chosen.xmax() <= visible.right &&
              chosen.ymin() >= visible.top &&
              chosen.ymax() <= visible.bottom ) {
            mapView().lensRect = overlay;
            mapView().lensZoom = Math.min(naturalZoom(),
                mapView().lensTiles.guiTargetZoom());
          } else {
            SwingUtils.beep();
          }
        }
      };
    };
  }

  public int naturalZoom() {
    return FallbackChain.naturalZoom(
        mapView().projection.scaleAcross(), mapView().lensTiles);
  }

  public static BoxOverlay createBox(AxisRect lensBox) {
    return new BoxOverlay(lensBox, 0xCC00FF);
  }

}
