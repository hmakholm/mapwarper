package net.makholm.henning.mapwarper.gui.swing;

import static java.awt.Cursor.getPredefinedCursor;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.Map;

class SwingCursor {

  private final Component target;
  private final Map<String, Cursor> loaded = new LinkedHashMap<>();

  private String current = "DEFAULT";

  SwingCursor(Component target) {
    this.target = target;
  }

  void set(String want) {
    if( want == null )
      want = "DEFAULT";
    if( !want.equals(current) ) {
      current = want;
      target.setCursor(loaded.computeIfAbsent(want, this::fromString));
    }
  }

  private Cursor fromString(String s) {
    switch( s ) {
    case "DEFAULT": return null;
    case "CROSSHAIR": s = "crosshairCursor"; break;
    case "CLOSED_HAND":
    case "MOVE": return getPredefinedCursor(Cursor.MOVE_CURSOR);
    case "HAND": return getPredefinedCursor(Cursor.HAND_CURSOR);
    case "N_RESIZE": return getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
    case "S_RESIZE": return getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
    case "E_RESIZE": return getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
    case "W_RESIZE": return getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
    case "NW_RESIZE": return getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
    case "NE_RESIZE": return getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
    case "SW_RESIZE": return getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
    case "SE_RESIZE": return getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
    }

    var img = SwingUtils.loadBundledImage(true, s+".png").orElse(null);
    if( img == null ) return null;
    return target.getToolkit().createCustomCursor(img,
        new Point(img.getWidth()/2, img.getHeight()/2), s);
  }

}
