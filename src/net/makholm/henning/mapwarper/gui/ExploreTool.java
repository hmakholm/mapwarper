package net.makholm.henning.mapwarper.gui;

import java.awt.Cursor;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.files.TrackHidingTool;
import net.makholm.henning.mapwarper.track.VisibleTrackData;

final class ExploreTool extends TrackHidingTool {

  protected ExploreTool(Commands owner) {
    super(owner, "explore", "Explore");
    toolCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
  }

  @Override
  public int retouchDisplayFlags(int orig) {
    return orig & ~Toggles.DARKEN_MAP.bit();
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    if( shiftHeld(modifiers) ) return NO_RESPONSE;
    return myResponse;
  }

  @Override
  public ToolResponse outsideWindowResponse() {
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
