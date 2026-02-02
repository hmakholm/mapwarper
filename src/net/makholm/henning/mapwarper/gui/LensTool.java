package net.makholm.henning.mapwarper.gui;

import java.awt.Cursor;
import java.util.function.Function;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.overlays.BoxOverlay;
import net.makholm.henning.mapwarper.gui.projection.WarpedProjection;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.gui.swing.Tool;

public class LensTool extends Tool {
  protected LensTool(Commands owner) {
    super(owner, "lens", "Lens");
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    var dragAction = startDrag(pos);
    if( dragAction != null ) {
      return new ToolResponse() {
        @Override
        public Cursor cursor() {
          return dragAction.cursor();
        }
        @Override
        public void execute(ExecuteWhy why) {
          if( dragAction.cursor() == null )
            mapView().cancelLens();
        }
      };
    } else {
      var r = ctrlHeld(modifiers) ? lensMinusCommand() : lensPlusCommand();
      return r == null ? NO_RESPONSE : why -> r.run();
    }
  }

  public Runnable lensPlusCommand() {
    if( (mapView().lensRect == null ||
        mapView().lensZoom >= mapView().naturalLensZoom()) )
      return null;
    else if( mapView().lensZoom >= 20 )
      return SwingUtils::beep;
    else
      return () -> {
        var lensRect = mapView().lensRect;
        if( lensRect != null && !mapView().isExportLens() ) {
          AxisRect visible = new AxisRect(mapView().visibleArea);
          AxisRect intersection = lensRect.box.intersect(visible);
          if( intersection == null ) {
            mapView().cancelLens();
          } else if( intersection != lensRect.box ) {
            mapView().lensRect = createBox(intersection);
          }
        }
        mapView().lensZoom++;
        System.err.println("Lens zoom now "+mapView().lensZoom);
        owner.swing.invalidateToolResponse();
      };
  }

  public Runnable lensMinusCommand() {
    if( mapView().lensZoom <= 10 )
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
    var dragAction0 = startDrag(pos0);
    mapView().cancelLens();
    var dragAction = dragAction0 != null ? dragAction0 : startDrag(pos0);
    return (pos1, modifiers1) -> {
      AxisRect chosen = dragAction.makeRect.apply(pos1);
      chosen = shrinkForExport(chosen);
      var overlay = createBox(chosen);
      return new ToolResponse() {
        @Override
        public BoxOverlay previewOverlay() {
          return overlay;
        }
        @Override
        public Cursor cursor() {
          return dragAction.cursor();
        }
        @Override
        public void execute(ExecuteWhy why) {
          mapView().setLens(overlay);
        }
      };
    };
  }

  private AxisRect shrinkForExport(AxisRect chosen) {
    var proj = mapView().projection;
    if( mapView().isExportLens() &&
        proj.base() instanceof WarpedProjection warp ) {
      var boxp = proj.local2projected(chosen);
      var boxp2 = warp.shrinkToMargins(boxp, proj.scaleAlong());
      if( boxp2 != boxp )
        return proj.projected2local(boxp2);
    }
    return chosen;
  }

  private record DragAction(Cursor cursor, Function<Point, AxisRect> makeRect) {}

  private DragAction startDrag(Point p1) {
    if( mapView().lensRect != null ) {
      var box = mapView().lensRect.box;
      int code = 10*placecode(p1.x, box.xmin(), box.xmax()) +
          placecode(p1.y, box.ymin(), box.ymax());
      switch( code ) {
      case 11:
        return new DragAction(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR),
            p2 -> new AxisRect(p2, Point.at(box.xmax(), box.ymax())));
      case 31:
        return new DragAction(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR),
            p2 -> new AxisRect(p2, Point.at(box.xmin(), box.ymax())));
      case 13:
        return new DragAction(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR),
            p2 -> new AxisRect(p2, Point.at(box.xmax(), box.ymin())));
      case 33:
        return new DragAction(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR),
            p2 -> new AxisRect(p2, Point.at(box.xmin(), box.ymin())));
      case 21:
        return new DragAction(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR),
            p2 -> new AxisRect(Point.at(box.xmin(), p2.y), Point.at(box.xmax(), box.ymax())));
      case 23:
        return new DragAction(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR),
            p2 -> new AxisRect(Point.at(box.xmin(), box.ymin()), Point.at(box.xmax(), p2.y)));
      case 12:
        return new DragAction(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR),
            p2 -> new AxisRect(Point.at(p2.x, box.ymin()), Point.at(box.xmax(), box.ymax())));
      case 32:
        return new DragAction(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR),
            p2 -> new AxisRect(Point.at(box.xmin(), box.ymin()), Point.at(p2.x, box.ymax())));
      case 22:
        return null;
      default:
        // fall through
      }
    }
    return new DragAction(null, p2 -> new AxisRect(p1, p2));
  }

  private static final int GRABWIDTH = 3;

  private static int placecode(double val, double min, double max) {
    if( val < min - GRABWIDTH ) return 0;
    if( val > max + GRABWIDTH ) return 4;
    if( val > (min+max)/2 ) {
      if( val > max - GRABWIDTH ) return 3;
    } else {
      if( val < min + GRABWIDTH ) return 1;
    }
    return 2;
  }

  public static BoxOverlay createBox(AxisRect lensBox) {
    return new BoxOverlay(lensBox, 0xCC00FF);
  }

}
