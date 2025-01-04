package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.swing.Tool;

public abstract class ProjectionSwitchingTool extends Tool {

  protected ProjectionSwitchingTool(Commands owner,
      String codename, String niceName) {
    super(owner, codename, niceName);
  }

  protected abstract ToolResponse clickResponse(Point pos, int modifiers);

  protected abstract ToolResponse dragResponse(Point pos1, Point pos2);

  @Override
  public void invoke() {
    super.invoke();
    mapView().disableTempProjectionsOnShift = false;
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    if( shiftHeld(modifiers) ) {
      return () -> owner.swing.commitToTempProjection();
    } else {
      return clickResponse(pos, modifiers);
    }
  }

  @Override
  public Runnable shiftDownProjectionSwitcher(Point pos, int modifiers) {
    if( mapView().disableTempProjectionsOnShift )
      return null;
    return clickResponse(pos, modifiers)::execute;
  }

  @Override
  public MouseAction drag(Point pos0, int modifiers0) {
    return new MouseAction() {
      @Override
      public ToolResponse mouseResponse(Point pos1, int modifiers1) {
        return dragResponse(pos0, pos1);
      }
      @Override
      public Runnable shiftDownProjectionSwitcher(Point pos1, int modifiers1) {
        return dragResponse(pos0, pos1)::execute;
      }
    };
  }

}
