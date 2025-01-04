package net.makholm.henning.mapwarper.track.smooth;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.track.TrackSegment;

public final class StraightSegment extends TrackSegmentPath {

  StraightSegment(Smoother creator, TrackSegment seg) {
    this(creator, seg.normalize(), seg, 0);
  }

  StraightSegment(Smoother creator, UnitVector dir, TrackSegment seg,
      double totalSlew) {
    super(creator, dir, seg, dir, totalSlew);
  }

  @Override
  public Bezier renderToBezier() {
    return Bezier.line(a, b);
  }

}
