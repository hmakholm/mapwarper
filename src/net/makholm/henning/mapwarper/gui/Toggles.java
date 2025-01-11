package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.gui.swing.ToggleCommand;

public enum Toggles {

  SUPERSAMPLE(true, false, "supersample", "Supersample warped maps"),
  DARKEN_MAP(true, false, "darkenMap", "Darken base map"),
  CURVATURE(false, true, "showCurvature", "Show track curvature"),
  CROSSHAIRS(false, true, "showCrosshairs", "Show crosshairs"),
  MAIN_TRACK(false, true, "showTrack", "Show main track"),
  EXT_BOUNDS(false, false, "showBounds", "Show indirect bounds"),
  SHOW_LABELS(false, true, "showLabels", "Show track labels"),
  LENS_MAP(true, false, ":lensMapFlagBit", null),
  NO_MAIN_TILES_OUTSIDE_MARGINS(true, false, ":clearMarginsFlagBit", null);
  ;

  public final String codename;
  public final String niceName;

  private boolean forMap;
  private boolean forVectors;

  Toggles(boolean forMap, boolean forVectors,
      String codename, String niceName) {
    this.forMap = forMap;
    this.forVectors = forVectors;
    this.codename = codename;
    this.niceName = niceName;
  }

  public ToggleCommand command(Commands logic) {
    return logic.toggle(this);
  }

  public int bit() {
    return 1 << ordinal();
  }

  public boolean setIn(int bitmap) {
    return ((bitmap >> ordinal()) & 1) != 0;
  }

  public static final int MAP_MASK;
  public static final int VECTOR_MASK;
  static {
    int mapMask = 0;
    int vectorMask = 0;
    for( Toggles t: values() ) {
      if( t.forMap ) mapMask |= t.bit();
      if( t.forVectors ) vectorMask |= t.bit();
    }
    MAP_MASK = mapMask;
    VECTOR_MASK = vectorMask;
  }

}
