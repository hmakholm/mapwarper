package net.makholm.henning.mapwarper.gui.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import net.makholm.henning.mapwarper.geometry.AxisRect;

public final class BoxOverlay implements VectorOverlay {
  public final AxisRect box;
  private final int rgb;

  public BoxOverlay(AxisRect box, int rgb) {
    this.box = box;
    this.rgb = rgb;
  }

  @Override
  public AxisRect boundingBox() { return box.grow(OVERLAY_LINEWIDTH); }

  @Override
  public void paint(Graphics2D g) {
    g.setColor(new Color(rgb));
    g.setStroke(new BasicStroke(OVERLAY_LINEWIDTH));
    g.draw(new Rectangle2D.Double(box.xmin(), box.ymin(),
        box.width(), box.height()));
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof BoxOverlay other &&
        other.box.xmin() == box.xmin() &&
        other.box.xmax() == box.xmax() &&
        other.box.ymin() == box.ymin() &&
        other.box.ymax() == box.ymax() &&
        other.rgb == rgb;
  }
}