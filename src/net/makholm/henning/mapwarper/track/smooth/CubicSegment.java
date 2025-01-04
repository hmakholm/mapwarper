package net.makholm.henning.mapwarper.track.smooth;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.track.TrackSegment;

final class CubicSegment extends TrackSegmentPath {

  private final LineSeg dBC;

  CubicSegment(Smoother creator,
      UnitVector da, TrackSegment seg, UnitVector db, double totalSlew) {
    super(creator, da, seg, db, totalSlew);
    var chord = a.to(b);
    var chordlen = a.to(b).length();
    var ctrllen1 = chordlen/3;
    var ctrllen2 = chordlen/3;
    // The following correction makes the curve approximate a circular arc
    // much better in the case where both ends actually lie on the same arc.
    // The magic constant 0.193 was found experimentally -- it makes the
    // difference from an actual circular arc less than 0.1% of the radius
    // for arcs up to 90°, and still less than 5% at 135°.
    // It _also_ turns out to make the speed along the curve vary less.
    // (TODO: correct at least approximately for that speed rather than just
    // assume the chord length is also the length of the curve!)
    ctrllen1 += 0.193 * (chordlen - chord.dot(da));
    ctrllen2 += 0.193 * (chordlen - chord.dot(db));
    var ctrl1 = a.plus(ctrllen1, da);
    var ctrl2 = b.plus(-ctrllen2, db);
    dBC = ctrl1.to(ctrl2);
  }

  @Override
  public Bezier renderToBezier() {
    return Bezier.cubic(a, dBC.a, dBC.b, b);
  }

}
