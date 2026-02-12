package net.makholm.henning.mapwarper.gui.overlays;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.util.BadError;

public class TextOverlay implements VectorOverlay {

  public static class Style implements Cloneable {
    int backgroundColor = 0xDDEECCAA;
    int textColor = 0xFF000000;
    Font font = defaultFont;
    int margin = 3;
    int xpadding = 2;
    int ypadding = 1;

    @Override
    public Style clone() {
      try {
        return (Style)super.clone();
      } catch (CloneNotSupportedException e) {
        throw BadError.of("But this _is_ cloneable!");
      }
    }

    @Override
    public boolean equals(Object o) {
      return this == o ||
          o instanceof Style other &&
          backgroundColor == other.backgroundColor &&
          textColor == other.textColor &&
          font.equals(other.font) &&
          xpadding == other.xpadding &&
          ypadding == other.ypadding &&
          margin == other.margin;
    }
  }

  private static final Font defaultFont =
      SwingUtils.getANiceDefaultFont().deriveFont(13.0f);

  public static TextOverlay of(Component c, String... text) {
    return of(c, new Style(), Point.ORIGIN, Arrays.asList(text));
  }

  public static TextOverlay of(Component c, List<String> text) {
    return of(c, new Style(), Point.ORIGIN, text);
  }

  public static TextOverlay of(Component c, Style style,
      Point nw, List<String> text) {
    style = style.clone();
    nw = round(nw);
    var metrics = c.getFontMetrics(style.font);
    int width = 0;
    int height = text.size()*metrics.getHeight();
    for( var s : text )
      width = Math.max(width, metrics.stringWidth(s));
    width += 2*style.xpadding + 2*style.margin;
    height += 2*style.ypadding + 2*style.margin;
    return new TextOverlay(style, text, new AxisRect(
        nw, nw.plus(Vector.of(width, height))));
  }

  public TextOverlay at(Point newNw) {
    return new TextOverlay(style, text,
        bbox.translate(bbox.nwCorner().to(round(newNw))));
  }

  public TextOverlay moveUp() {
    return new TextOverlay(style, text,
        bbox.translate(Vector.of(0, -bbox.height())));
  }

  public TextOverlay moveLeft() {
    return new TextOverlay(style, text,
        bbox.translate(Vector.of(-bbox.width(),0)));
  }

  final Style style;
  final List<String> text;
  final AxisRect bbox;

  private static Point round(Point p) {
    return Point.at(Math.round(p.x), Math.round(p.y));
  }

  private TextOverlay(Style style, List<String> text, AxisRect bbox) {
    this.style = style;
    this.text = text;
    this.bbox = bbox;
  }

  @Override
  public AxisRect boundingBox() {
    return bbox;
  }

  @Override
  public void paint(Graphics2D g) {
    g = (Graphics2D)g.create();
    g.setFont(style.font);
    var metrics = g.getFontMetrics();
    g.translate(bbox.xmin()+style.margin, bbox.ymin()+style.margin);
    g.setColor(new Color(style.backgroundColor, true));
    g.fill(new Rectangle2D.Double(0, 0,
        bbox.width()-2*style.margin,
        bbox.height()-2*style.margin));
    g.setColor(new Color(style.textColor, false));
    var x = style.xpadding;
    var y = style.ypadding + metrics.getAscent();
    for( var s : text ) {
      g.drawString(s, x, y);
      y += metrics.getHeight();
    }
  }

  @Override
  public boolean equals(Object o) {
    return o == this ||
        o instanceof TextOverlay other &&
        bbox.equals(other.bbox) &&
        text.equals(other.text) &&
        style.equals(other.style);
  }

}
