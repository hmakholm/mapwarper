package net.makholm.henning.mapwarper.track;

public enum ChainClass {
  TRACK, BOUND;

  public SegKind defaultKind() {
    switch( this ) {
    case TRACK: return SegKind.TRACK;
    case BOUND: return SegKind.BOUND;
    }
    return null;
  }
}
