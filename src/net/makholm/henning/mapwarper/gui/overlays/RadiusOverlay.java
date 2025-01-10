package net.makholm.henning.mapwarper.gui.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;

public final class RadiusOverlay implements VectorOverlay {
  final LineSeg ls;
  final Path2D.Double path;
  final AxisRect bbox;
  final int rgb;

  public RadiusOverlay(LineSeg ls, int rgb) {
    this.ls = ls;
    this.rgb = rgb;

    var turned = ls.turnRight();
    var k = 0.551915;
    var kk = 1-k;

    path = new Path2D.Double();
    moveTo(ls.a);
    lineTo(ls.b);
    moveTo(ls.b.plus(turned));
    curveTo(
        ls.a.plus(turned).plus(kk, ls),
        ls.a.plus(k, turned),
        ls.a);
    curveTo(
        ls.a.plus(-k, turned),
        ls.a.minus(turned).plus(kk, ls),
        ls.b.minus(turned));

    bbox = new AxisRect(path.getBounds2D()).grow(OVERLAY_LINEWIDTH);
  }

  private void moveTo(Point p) { path.moveTo(p.x, p.y); }
  private void lineTo(Point p) { path.lineTo(p.x, p.y); }

  private void curveTo(Point p2, Point p3, Point p4) {
    path.curveTo(p2.x, p2.y, p3.x, p3.y, p4.x, p4.y);
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
    return o instanceof RadiusOverlay other &&
        other.ls.a.is(ls.a) &&
        other.ls.b.is(ls.b) &&
        other.rgb == rgb;
  }
}