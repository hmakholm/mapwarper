package net.makholm.henning.mapwarper.track.smooth;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.track.TrackSegment;

public abstract class TrackSegmentPath {

  final int index;

  public final Point a, b;

  public final double slewBefore;
  public final double slewAfter;

  public final TrackSegment segment;

  TrackSegmentPath(Smoother creator,
      UnitVector da, TrackSegment segment, UnitVector db, double totalSlew) {
    this.index = creator.segmentCollector.size();
    this.slewBefore = totalSlew/2;
    this.slewAfter = totalSlew/2;
    UnitVector na = da.turnRight(), nb = db.turnRight();
    this.a = segment.a.plus(slewBefore, na);
    this.b = segment.b.plus(-slewAfter, nb);

    this.segment = segment;
    creator.segmentCollector.add(this);
  }

  public abstract Bezier renderToBezier();

}
