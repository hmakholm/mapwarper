package net.makholm.henning.mapwarper.gui;

import java.awt.Cursor;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.track.VisibleTrackData;

final class ExploreTool extends Tool {

  protected ExploreTool(Commands owner) {
    super(owner, "explore", "Explore");
    toolCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
  }

  @Override
  public void invoke() {
    if( mapView().currentTool == this )
      owner.move.invoke();
    else
      super.invoke();
  }

  @Override
  public void escapeAction() {
    mapView().selectTool(owner.move);
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    if( shiftHeld(modifiers) ) return NO_RESPONSE;
    return myResponse;
  }

  @Override
  public MouseAction drag(Point pos, int modifiers) {
    return DRAG_THE_MAP;
  }

  private static final ToolResponse myResponse = new ToolResponse() {
    final VisibleTrackData noVectorLayers = new VisibleTrackData().freeze();
    @Override
    public VisibleTrackData previewTrackData() { return noVectorLayers; }
    @Override
    public void execute(ExecuteWhy why) { }
  };

}
