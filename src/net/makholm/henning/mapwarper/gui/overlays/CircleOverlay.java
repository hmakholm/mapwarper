package net.makholm.henning.mapwarper.gui.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;

public class CircleOverlay implements VectorOverlay {
  final Point localCenter;
  final int rgb;
  final int diameter;

  public CircleOverlay(int rgb, int diameter, Point localCenter) {
    this.rgb = rgb;
    this.diameter = diameter;
    this.localCenter = localCenter;
  }

  @Override
  public AxisRect boundingBox() {
    return new AxisRect(localCenter).grow(diameter/2+OVERLAY_LINEWIDTH);
  }

  @Override
  public void paint(Graphics2D g) {
    g.setColor(new Color(rgb));
    g.setStroke(new BasicStroke(OVERLAY_LINEWIDTH));
    g.draw(new Ellipse2D.Double(
        localCenter.x - 0.5*diameter,
        localCenter.y - 0.5*diameter,
        diameter, diameter));
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof CircleOverlay cc &&
        cc.localCenter.is(localCenter) &&
        cc.rgb == rgb &&
        cc.diameter == diameter;
  }
}