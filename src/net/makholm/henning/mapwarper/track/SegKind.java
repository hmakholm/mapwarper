package net.makholm.henning.mapwarper.track;

import net.makholm.henning.mapwarper.rgb.RGB;

public enum SegKind {

  TRACK(ChainClass.TRACK, "track", RGB.TRACK_SEGMENT),
  WEAK(ChainClass.TRACK, "weak", RGB.WEAK_SEGMENT),
  STRAIGHT(ChainClass.TRACK, "straight", RGB.STRAIGHT_SEGMENT),
  SLEW(ChainClass.TRACK, "slew", RGB.STRAIGHT_SLEW),
  MAGIC(ChainClass.TRACK, "connect", RGB.CURVED_SLEW),
  BOUND(ChainClass.BOUND, "bounds", RGB.BOUND_SEGMENT);

  public final ChainClass klass;
  public final int rgb;
  public final String keyword;

  SegKind(ChainClass klass, String keyword, int rgb) {
    this.klass = klass;
    this.keyword = keyword;
    this.rgb = rgb;
  }

  public ChainClass chainClass() {
    return klass;
  }

}
