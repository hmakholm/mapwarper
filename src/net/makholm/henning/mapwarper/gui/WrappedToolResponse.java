package net.makholm.henning.mapwarper.gui;

import java.awt.Cursor;

import net.makholm.henning.mapwarper.gui.MouseAction.ExecuteWhy;
import net.makholm.henning.mapwarper.gui.MouseAction.ToolResponse;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.track.VisibleTrackData;

abstract class WrappedToolResponse implements ToolResponse {
  private ToolResponse me;

  WrappedToolResponse(ToolResponse toWrap) {
    me = toWrap;
  }

  @Override
  public VisibleTrackData previewTrackData() {
    return me.previewTrackData();
  }

  @Override
  public VectorOverlay previewOverlay() {
    return me.previewOverlay();
  }

  @Override
  public Cursor cursor() {
    return me.cursor();
  }

  @Override
  public void execute(ExecuteWhy why) {
    me.execute(why);
  }
}
