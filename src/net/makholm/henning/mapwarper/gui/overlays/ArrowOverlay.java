package net.makholm.henning.mapwarper.gui.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.LineSeg;

public final class ArrowOverlay implements VectorOverlay {
  final LineSeg ls;
  final Path2D.Double path;
  final AxisRect bbox;
  final int rgb;

  public ArrowOverlay(LineSeg ls, int rgb) {
    this.ls = ls;
    this.rgb = rgb;

    var along = ls.normalize();
    var across = along.turnRight();
    var ah1 = ls.b.plus(-18, along).plus(-8, across);
    var ah2 = ls.b.plus(-18, along).plus(8, across);

    path = new Path2D.Double();
    path.moveTo(ls.a.x, ls.a.y);
    path.lineTo(ls.b.x, ls.b.y);
    path.moveTo(ah1.x, ah1.y);
    path.lineTo(ls.b.x, ls.b.y);
    path.lineTo(ah2.x, ah2.y);

    bbox = new AxisRect(path.getBounds2D()).grow(OVERLAY_LINEWIDTH);
  }

  @Override
  public AxisRect boundingBox() { return bbox; }

  @Override
  public void paint(Graphics2D g) {
    g.setColor(new Color(rgb));
    g.setStroke(new BasicStroke(OVERLAY_LINEWIDTH,
        BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
    g.draw(path);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ArrowOverlay other &&
        other.ls.a.is(ls.a) &&
        other.ls.b.is(ls.b) &&
        other.rgb == rgb;
  }
}