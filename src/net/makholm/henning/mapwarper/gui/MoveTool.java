package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.Point;

final class MoveTool extends Tool {

  protected MoveTool(Commands logic) {
    super(logic, "move", "Move the map");
    toolCursor = "CLOSED_HAND";
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    return _ -> {};
  }

  @Override
  public MouseAction drag(Point pos, int modifiers) {
    return DRAG_THE_MAP;
  }

}
