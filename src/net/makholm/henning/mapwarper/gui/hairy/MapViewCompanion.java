package net.makholm.henning.mapwarper.gui.hairy;

import net.makholm.henning.mapwarper.gui.MapView;
import net.makholm.henning.mapwarper.gui.overlays.BoxOverlay;
import net.makholm.henning.mapwarper.gui.swing.Command;

/**
 * This object is the framework-specific part of {@link MapView}.
 * There's an 1-to-1 correspondence between instances of the two
 * types.
 */
public interface MapViewCompanion {

  public void refreshScene();
  public void mousePositionAdjusted();
  public void invalidateMapRendering();
  public void invalidateToolResponse();
  public void repaintFromScratch();

  public boolean isCommandKeyDown(Command which);

  public void commitToTempProjection();

  public boolean cancelDrag();
  public void cancelLens(BoxOverlay lensRect);

}
