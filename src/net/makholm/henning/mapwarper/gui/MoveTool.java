package net.makholm.henning.mapwarper.gui;

import java.awt.Cursor;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.swing.Tool;

final class MoveTool extends Tool {

  protected MoveTool(Commands logic) {
    super(logic, "Mapwarper v3", "Move the map");
    toolCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    return why -> {};
  }

  @Override
  public MouseAction drag(Point pos, int modifiers) {
    return DRAG_THE_MAP;
  }

}
