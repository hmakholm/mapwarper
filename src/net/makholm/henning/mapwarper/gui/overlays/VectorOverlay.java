package net.makholm.henning.mapwarper.gui.overlays;

import java.awt.Graphics2D;

import net.makholm.henning.mapwarper.geometry.AxisRect;

public interface VectorOverlay {

  public AxisRect boundingBox();
  public void paint(Graphics2D g);

  public static final float OVERLAY_LINEWIDTH = 1;

}
