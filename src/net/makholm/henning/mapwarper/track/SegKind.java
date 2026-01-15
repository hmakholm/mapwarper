package net.makholm.henning.mapwarper.track;

public enum SegKind {
  STRONG  (ChainClass.TRACK, 0xABCDFF, "straight", "strong track"),
  TRACK   (ChainClass.TRACK, 0xFFEEDD, "track",    "curved track"),
  WEAK    (ChainClass.TRACK, 0xFFAA33, "weak",     "weak track"),
  SLEW    (ChainClass.TRACK, 0x0080DD, "slew",     "displacement joiner"),
  MAGIC   (ChainClass.TRACK, 0xDD5500, "connect",  "arc joiner"),
  BOUND   (ChainClass.BOUND, 0x99F488, "bounds",   "bound line"),
  LBOUND  (ChainClass.BOUND, 0x99F488, "local",    "locally straight bound");

  public final ChainClass klass;
  public final int rgb;
  public final String keyword;
  public final String desc;

  SegKind(ChainClass klass, int rgb, String keyword, String desc) {
    this.klass = klass;
    this.keyword = keyword;
    this.rgb = rgb;
    this.desc = desc;
  }

  public ChainClass chainClass() {
    return klass;
  }

}
