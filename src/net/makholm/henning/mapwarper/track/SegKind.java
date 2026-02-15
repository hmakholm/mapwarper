package net.makholm.henning.mapwarper.track;

public enum SegKind {
  STRONG  (L.TRACK   | 0xABCDFF, "straight", "strong track"),
  TRACK   (L.TRACK   | 0xFFEEDD, "track",    "curved track"),
  WEAK    (L.TRACK   | 0xFFAA33, "weak",     "weak track"),
  SLEW    (L.TRACK   | 0x0080DD, "slew",     "displacement joiner"),
  MAGIC   (L.TRACK   | 0xDD5500, "connect",  "arc joiner"),
  BOUND   (L.SOLID   | 0x99F488, "bounds",   "bound line"),
  LBOUND  (L.DASHED  | 0x99F488, "local",    "locally straight bound");

  public final ChainClass klass;
  public final int rgb;
  public final int linestyle;
  public final String keyword;
  public final String desc;

  public static class L {
    public static final int TRACK = 0x01 << 24;
    public static final int SOLID = 0x02 << 24;
    public static final int DASHED = 0x04 << 24;
    public static final int DOTTED = 0x80 << 24;
  }

  SegKind(int style, String keyword, String desc) {
    this.klass = (style & L.TRACK) != 0 ? ChainClass.TRACK : ChainClass.BOUND;
    this.keyword = keyword;
    this.linestyle = style;
    this.rgb = style & 0x00FFFFFF;
    this.desc = desc;
  }

  public ChainClass chainClass() {
    return klass;
  }

  public boolean showStraightDespiteWarp() {
    return (linestyle & L.DASHED) != 0;
  }

}
