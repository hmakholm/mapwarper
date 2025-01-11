package net.makholm.henning.mapwarper.gui;

import java.awt.Cursor;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.track.VisibleTrackData;

public interface MouseAction {

  /**
   * This is generally called before a click actually happens, to give
   * the tool an opportunity to respond to mouse moves with a preview.
   *
   * The click itself actually triggers {@link ToolReponse#execute()}.
   */
  public ToolResponse mouseResponse(Point pos, int modifiers);

  /**
   * For all other responses to Shift than a projection change, use an
   * appropriate ToolResponse. (The difference is that a projection change
   * only happens at shift-down events, and not at subsequent mouse moves).
   */
  public default Runnable shiftDownProjectionSwitcher(Point pos, int modifiers) {
    return null;
  }

  public interface ToolResponse {
    default public VisibleTrackData previewTrackData() { return null; }

    default public VectorOverlay previewOverlay() { return null; }

    default public Cursor cursor() { return null; }

    public void execute();
  }

}
