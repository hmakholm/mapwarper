package net.makholm.henning.mapwarper.track;

public enum SegKind {
  TRACK   (ChainClass.TRACK, 0xFFEEDD, "track"),
  WEAK    (ChainClass.TRACK, 0xFFAA33, "weak"),
  STRAIGHT(ChainClass.TRACK, 0xABCDFF, "straight"),
  SLEW    (ChainClass.TRACK, 0x0080DD, "slew"),
  MAGIC   (ChainClass.TRACK, 0xDD5500, "connect"),
  BOUND   (ChainClass.BOUND, 0x99F488, "bounds");

  public final ChainClass klass;
  public final int rgb;
  public final String keyword;

  SegKind(ChainClass klass, int rgb, String keyword) {
    this.klass = klass;
    this.keyword = keyword;
    this.rgb = rgb;
  }

  public ChainClass chainClass() {
    return klass;
  }

}
