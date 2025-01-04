package net.makholm.henning.mapwarper.track;

import java.util.Arrays;
import java.util.List;

import net.makholm.henning.mapwarper.rgb.RGB;

public enum SegKind {

  TRACK("track", RGB.TRACK_SEGMENT),
  STRAIGHT("straight", RGB.STRAIGHT_SEGMENT),
  SLEW("slew", RGB.STRAIGHT_SLEW),
  MAGIC("connect", RGB.CURVED_SLEW),
  BOUND("bound", RGB.BOUND_SEGMENT);

  public final int rgb;
  public final String keyword;

  SegKind(String keyword, int rgb) {
    this.keyword = keyword;
    this.rgb = rgb;
  }

  public SegKind chainClass() {
    return isTrackVariant() ? TRACK : this;
  }

  public boolean isTrackVariant() {
    return this == TRACK || this == STRAIGHT ||
        this == SLEW || this == MAGIC;
  }

  public static final List<SegKind> classes = Arrays.asList(TRACK, BOUND);

}
