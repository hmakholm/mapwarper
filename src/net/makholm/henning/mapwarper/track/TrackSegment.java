package net.makholm.henning.mapwarper.track;

import net.makholm.henning.mapwarper.geometry.LineSeg;

public final class TrackSegment extends LineSeg {
  public final TrackNode a,b;
  public SegKind kind;

  public TrackSegment(TrackNode a, SegKind kind, TrackNode b) {
    super(a, b);
    this.a = a;
    this.b = b;
    this.kind = kind;
  }
}
