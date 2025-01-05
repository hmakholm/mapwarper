package net.makholm.henning.mapwarper.track.smooth;

import java.util.ArrayList;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.TrackPath;
import net.makholm.henning.mapwarper.track.TrackSegment;

public class Smoother {

  private final TrackPath segments;

  final List<TrackSegmentPath> segmentCollector = new ArrayList<>();

  public static List<TrackSegmentPath> smoothen(TrackPath path) {
    if( path == null )
      return null;
    else
      return new Smoother(path).smoothen();
  }

  private Smoother(TrackPath path) {
    this.segments = path;
  }

  public List<TrackSegmentPath> smoothen() {
    for( int i=0; i<segments.size; i++ ) {
      TrackSegment seg = segments.seg(i);
      UnitVector da = tangent(i).normalize();
      UnitVector db = tangent(i+1).normalize();
      switch( seg.kind ) {
      case TRACK:
        if( da.is(db) && da.is(seg.normalize()) ) {
          new StraightSegment(this, seg);
        } else {
          new CubicSegment(this, da, seg, db, 0);
        }
        break;
      case STRAIGHT:
      case TWO_SIDED_BOUND:
      case BOUND:
        new StraightSegment(this, seg);
        break;
      case SLEW:
        if( da.dot(seg) <= 0 || db.dot(seg) <= 0 ) {
          // This slew seems to be moving backwards. That's rather bogus,
          // but will be confusing in warped view -- so represent it as
          // an equally bogus but more visible straight segment instead.
          new StraightSegment(this, seg);
          break;
        }
        // else fall through, since the tangent calculation already
        // has made sure we'll end with a straight line.
      case MAGIC:
        double prod = da.dot(db);
        if( prod < -0.5 ) {
          // The formula gets unstable when the tangents are close to
          // opposite. (When they are exactly opposite, there are
          // either no or infinitely many ways to draw the two concentric
          // circles anyway). So if you try to autoslew more than 120°,
          // you get a normal cubic segment with no slew.
          new CubicSegment(this, da, seg, db, 0);
        } else if( da.is(db) ) {
          // The general formula works fine in this case, but we can
          // create a straight segment instead of a cubic one
          new StraightSegment(this, da, seg, seg.dot(da.turnRight()));
        } else {
          // Draw concentric circles through the two tangent points.
          // What is the difference in radius? The following neat and _exact_
          // formula drops out of a flurry of trig and vector algebra:
          var na = da.turnRight();
          var nb = db.turnRight();
          double radialDist = (seg.dot(na) + seg.dot(nb)) / (1 + prod);
          new CubicSegment(this, da, seg, db, radialDist);
        }
        break;
      }
    }

    return segmentCollector;
  }

  private Vector tangent(int node) {
    int b = findTangentRelevantSegment(node-1, -1);
    int a = findTangentRelevantSegment(b-1, -1);
    int c = findTangentRelevantSegment(node, +1);
    int d = findTangentRelevantSegment(c+1, +1);

    if( b >= 0 ) {
      if( c >= 0 ) {
        Vector[] bc = tangentTriple(b, c);
        if( a >= 0 && d >= 0 ) {
          Vector[] ab = tangentTriple(a, b);
          Vector[] cd = tangentTriple(c, d);
          return combineTangents(
              ab[2].normalize(), bc[1].normalize(), cd[0].normalize());
        } else {
          return bc[1];
        }
      } else {
        // have b but no c
        if( a >= 0 ) {
          return tangentTriple(a, b)[2];
        } else {
          return segments.seg(b);
        }
      }
    } else if( c >= 0 ) {
      // have c but no b
      if( d >= 0 ) {
        return tangentTriple(c, d)[0];
      } else {
        return segments.seg(c);
      }
    } else {
      // neither side has relevant tangent segments -- this may be something
      // with magic slews on both sides. Do the best we can...
      if( segments.kind(node) != null ) {
        if( segments.kind(node-1) != null ) {
          return tangentTriple(node-1, node)[1];
        } else {
          return segments.seg(node);
        }
      } else if( node > 0 ) {
        return segments.seg(node-1);
      } else {
        return segments.seg(node);
      }
    }
  }

  private int findTangentRelevantSegment(int node, int delta) {
    for(;; node += delta) {
      if( node < 0 || node >= segments.size ) return -1;
      switch( segments.seg(node).kind ) {
      case TRACK:
      case STRAIGHT:
        return node;
      case MAGIC:
        // The magic connector explicitly stops the tangent calculation
        return -100;
      case SLEW:
        // Slew segments are _skipped over_
        continue;
      case TWO_SIDED_BOUND:
      case BOUND:
        // These end the entire path construction
        return -100;
      }
    }
  }

  private UnitVector combineTangents(UnitVector p, UnitVector q, UnitVector r) {
    // This fancy averaging of the three estimated directions gives more
    // weight to two of them that _agree_, and so a better reconstruction
    // if the point was intended to be the dividing point between two
    // circular arcs.
    var sum = Vector.ZERO;
    var dpq = p.plus(-1, q).norm();
    if( dpq == 0 ) return p;
    sum = sum.plus(dpq, r);
    var dpr = p.plus(-1, r).norm();
    sum = sum.plus(dpr, q);
    var dqr = q.plus(-1, r).norm();
    sum = sum.plus(dqr, p);
    return sum.normalize();
  }

  private Vector[] tangentTriple(int i1, int i2) {
    TrackSegment s1 = segments.seg(i1), s2 = segments.seg(i2);
    if( s1.kind == SegKind.STRAIGHT ) {
      if( s2.kind != SegKind.STRAIGHT ) {
        return directionsWithStraight(s1, s2);
      } else
        return new Vector[] { s1, s1.plus(s2), s2 };
    } else if( s2.kind == SegKind.STRAIGHT ) {
      var rev = directionsWithStraight(s2, s1);
      return new Vector[] { rev[2], rev[1], rev[0] };
    }
    return estimateDirections(s1, s2);
  }

  static boolean USE_CIRCUMCENTERS = true;

  /**
   * Estimate the tangent vectors of a smooth curve passing through
   * three point in sequence. The inputs are their <em>differences<em>.
   *
   * Return an array of estimated tangents vectors (of arbitrary
   * magnitude!) at each of the three points.
   */
  private static Vector[] estimateDirections(Vector d1, Vector d2) {
    if( USE_CIRCUMCENTERS ) {
      // Use the tangents of the circle (or line) passing through the
      // three points.
      Vector d1r = d1.turnRight();
      Vector sum = d1.plus(d2);
      double t = sum.dot(d2) / d1r.dot(d2);
      if( Math.abs(t) > 1e6 )
        return new Vector[] { sum, sum, sum };
      Vector c = d1.scale(-1).plus(t, d1r);
      var res = new Vector[] { c.plus(2,d1), c, c.plus(-2,d2) };
      if( t > 0 ) {
        for( int i=0; i<3; i++ ) res[i] = res[i].turnLeft();
      } else {
        for( int i=0; i<3; i++ ) res[i] = res[i].turnRight();
      }
      return res;

    } else {
      // Construct a quadratic spline that passes through the three points
      // parameter intervals corresponding to their straight-line distances.

      // When the points are nearly collinear, this approximates the
      // circumcircle method, and was originally intended as a "simpler"
      // alternative to that calculation. For some (but not all) more
      // winding curves this seems to give more visually pleasing results,
      // in particular at the ends, so I'm keeping it around ...

      double t1 = -d1.length();
      double t3 = d2.length();

      // Now construct c, m and q such that
      // f(t) = c + t·m - (t-t1)(t-t3)·q satisfies
      // f(t1) = -d1   and   f(t2) = 0   and   f(t3) = d2
      Vector m = d1.plus(d2).divide(t3-t1);
      Vector c = d2.plus(-t3, m);
      Vector q = c.divide(t1*t3);

      return new Vector[] {
          m.plus(t3-t1, q),
          m.plus(t1+t3, q),
          m.plus(t1-t3, q)
      };
    }
  }

  private static Vector[] directionsWithStraight(
      LineSeg straight, LineSeg curved) {
    // Connect the straight line to the third point with a circular arc.
    // The outgoing direction is the straight direction _mirrored in_
    // the chord.
    // We can compute that without trig, by considering the directions
    // complex numbers: mirrored = chord^2 / straight
    var c2x = curved.x*curved.x - curved.y*curved.y;
    var c2y = 2 * curved.x * curved.y;
    var mx = c2x*straight.x + c2y*straight.y;
    var my = c2y*straight.x - c2x*straight.y;
    var mdir = Vector.of(mx,my);
    return new Vector[] { straight, straight, mdir };
  }

}
