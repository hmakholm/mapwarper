package net.makholm.henning.mapwarper.gui.track;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.track.SegKind;

class Smoother {

  static SegmentChain.Smoothed smoothen(SegmentChain chain) {
    if( chain.numNodes <= 1 )
      return trivialSmoothed();

    var smoother = new Smoother(chain);
    smoother.breakIntoSubchains();
    if( smoother.subchains.isEmpty() ) {
      smoother.handleTheAllSlewsCase();
    } else {
      Collections.sort(smoother.subchains);
      smoother.subchains.forEach(smoother::decideSubchain);
    }
    smoother.makeCurves();
    return smoother.wrapUp();
  }

  private static SegmentChain.Smoothed trivialSmoothed() {
    return new SegmentChain.Smoothed(
        new Bezier[0], new double[] {0}, new double[0]);
  }

  private final SegmentChain chain;
  private final UnitVector[] tangents;

  private final Bezier[] curves;
  private final double[] nodeSlews;
  private final double[] segmentSlews;

  /**
   * A sequence of nodes that must have the same tangent direction because
   * they're connected by straight-slew segments.
   */
  private record MultiNode(Smoother owner, int firstNode, int lastNode) {
    UnitVector get() {
      return owner.tangents[firstNode];
    }
    void put(UnitVector dir) {
      for( int i=firstNode; i<=lastNode; i++ )
        owner.tangents[i] = dir;
    }
  }

  /**
   * A sequence of segments that should have tangents decided together
   * because they have segment kinds of equal priority.
   *
   * By the time they're decided, the endpoints may or may not have
   * tangent directions already.
   */
  private record Subchain(int priority,
      List<MultiNode> nodes, List<LineSeg> segments) implements Comparable<Subchain> {
    @Override
    public int compareTo(Subchain o) {
      return priority - o.priority;
    }
  }

  private ArrayList<Subchain> subchains = new ArrayList<>();

  private Smoother(SegmentChain chain) {
    this.chain = chain;
    this.tangents = new UnitVector[chain.numNodes];
    this.curves = new Bezier[chain.numSegments];
    this.nodeSlews = new double[chain.numNodes];
    this.segmentSlews = new double[chain.numSegments];
  }

  private void breakIntoSubchains() {
    ArrayList<MultiNode> mnodes = null;
    ArrayList<LineSeg> segments = null;
    int currentPrio = -2;
    int multiStart = 0;
    for( int node = 0; node < chain.numNodes; node++ ) {
      int thisPrio = segmentPriority(node);
      if( thisPrio == SLEWPRIO ) {
        // include it in the current multinode
        continue;
      }
      MultiNode mnode = new MultiNode(this, multiStart, node);
      multiStart = node+1;
      if( thisPrio != currentPrio ) {
        if( mnodes != null ) {
          mnodes.add(mnode);
          subchains.add(new Subchain(currentPrio, mnodes, segments));
        }
        mnodes = new ArrayList<>();
        segments = new ArrayList<>();
        currentPrio = thisPrio;
      }
      if( node == chain.numNodes-1 ) break;
      mnodes.add(mnode);
      segments.add(chain.nodes.get(node).to(chain.nodes.get(node+1)));
    }
  }

  /**
   * A pseudo segment priority for segments that are never part of subchains.
   */
  private static final int SLEWPRIO = 100;

  private int segmentPriority(int segment) {
    if( segment >= chain.numSegments )
      return -1;
    switch( chain.kinds.get(segment) ) {
    case STRAIGHT:
      // First give directions to endpoints of STRAIGHT segments.
      // Beware that these are not necessarily actually straight --
      // if two of them are neighbors we use the usual algorithm,
      // which results in a shared circular arc rather than straight
      // lines.
      return 1;
    case TRACK:
      return 2;
    case MAGIC:
      return 3;
    case SLEW:
      return SLEWPRIO;
    case BOUND:
      // The easiest way not to spend too much time on bound chains is to
      // lump the entire chain into a multinode initially.
      return SLEWPRIO;
    }
    return 10; // This cannot really happen.
  }

  private void handleTheAllSlewsCase() {
    // We get here if the chain consists only of SLEW, or alternatively
    // for a bound chain. In that case, just give everything the same
    // tangent.
    var dir = chain.nodes.get(0).to(chain.nodes.last()).normalize();
    if( Double.isNaN(dir.x) )
      dir = Vector.of(1,0).normalize();
    Arrays.fill(tangents, dir);
  }

  private void decideSubchain(Subchain sc) {
    int nsegs = sc.segments.size();
    var firstnode = sc.nodes.get(0);
    var lastnode = sc.nodes.get(nsegs);
    var first1arc = singleArc(firstnode.get(), sc.segments.get(0));
    var last1arc = singleArc(lastnode.get(), sc.segments.get(nsegs-1));

    if( sc.segments.size() == 1 ) {
      if( firstnode.get() == null ) {
        if( lastnode.get() == null ) {
          // This special case produces a straight line
          var dir = sc.segments.get(0).normalize();
          firstnode.put(dir);
          lastnode.put(dir);
        } else {
          firstnode.put(last1arc);
        }
      } else {
        if( lastnode.get() == null ) {
          lastnode.put(first1arc);
        } else {
          // both ends are already fixed, nothing to do
        }
      }
      return;
    }

    UnitVector[][] triples = new UnitVector[sc.nodes.size()][];
    for( int i=1; i<triples.length-1; i++ )
      triples[i] = doubleArc(sc.segments.get(i-1), sc.segments.get(i));
    if( first1arc != null )
      triples[0] = new UnitVector[] { null, null, first1arc };
    if( last1arc != null )
      triples[triples.length-1] = new UnitVector[] { last1arc, null, null };

    for( int i=1; i<triples.length-1; i++ ) {
      var dir = triples[i][1];
      if( triples[i-1] != null && triples[i+1] != null )
        dir = combineTangents(triples[i-1][2], dir, triples[i+1][0]);
      sc.nodes.get(i).put(dir);
    }
    if( firstnode.get() == null )
      firstnode.put(triples[1][0]);
    if( lastnode.get() == null )
      lastnode.put(triples[triples.length-2][2]);
  }

  /**
   * Get the direction of the far end of an arc whose endpoints are known,
   * and where the direction at one end is already known.
   */
  private static UnitVector singleArc(
      UnitVector straight, LineSeg curved) {
    if( straight == null ) return null;
    // Connect the straight line to the third point with a circular arc.
    // The outgoing direction is the straight direction _mirrored in_
    // the chord.
    // We can compute that without trig, by considering the directions
    // complex numbers: mirrored = chord^2 / straight
    var c2x = curved.x*curved.x - curved.y*curved.y;
    var c2y = 2 * curved.x * curved.y;
    var mx = c2x*straight.x + c2y*straight.y;
    var my = c2y*straight.x - c2x*straight.y;
    return Vector.of(mx,my).normalize();
  }

  /**
   * Get the directions of an arc through three known points (specified
   * as the differences p2-p1 and p3-p2) <em>at</em> those three known parts.
   */
  private static UnitVector[] doubleArc(Vector d1, Vector d2) {
    Vector d1r = d1.turnRight();
    Vector sum = d1.plus(d2);
    double t = sum.dot(d2) / d1r.dot(d2);
    if( Math.abs(t) > 1e6 ) {
      var dir = sum.normalize();
      return new UnitVector[] { dir, dir, dir };
    }
    Vector c = d1.scale(-1).plus(t, d1r);
    var res = new UnitVector[] {
        c.plus(2,d1).normalize(),
        c.normalize(),
        c.plus(-2,d2).normalize() };
    if( t > 0 ) {
      for( int i=0; i<3; i++ ) res[i] = res[i].turnLeft();
    } else {
      for( int i=0; i<3; i++ ) res[i] = res[i].turnRight();
    }
    return res;
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

  // -----------------------------------------------------------------------

  double slewBefore, slewAfter;

  private void makeCurves() {
    double accumulatedSlew = 0;
    for( int seg=0; seg<chain.numSegments; seg++ ) {
      slewBefore = slewAfter = 0;
      curves[seg] = makeCurve(
          chain.nodes.get(seg),
          tangents[seg],
          chain.kinds.get(seg),
          tangents[seg+1],
          chain.nodes.get(seg+1));
      accumulatedSlew += slewBefore;
      segmentSlews[seg] = accumulatedSlew;
      accumulatedSlew += slewAfter;
      nodeSlews[seg+1] = accumulatedSlew;
    }
  }

  private Bezier makeCurve(
      Point p1, UnitVector t1, SegKind kind, UnitVector t4, Point p4) {
    var chord = p1.to(p4);
    switch( kind ) {
    case STRAIGHT:
    case TRACK:
      break;
    case SLEW:
      if( t1.dot(chord) <= 0 || t4.dot(chord) <= 0 ) {
        // This slew seems to be moving backwards. That's rather bogus,
        // but will be confusing in warped view -- so represent it as a
        // conspicuous non-slew curve instead
        double len = chord.length();
        return Bezier.cubic(p1, p1.plus(len, t1), p4.plus(-len, t4), p4);
      }
      // else fall through to common slew code, which works well for the
      // straight case too.
    case MAGIC:
      double prod = t1.dot(t4);
      if( prod < -0.5 ) {
        // The formula gets unstable when the tangents are close to
        // opposite. (When they are exactly opposite, there are
        // either no or infinitely many ways to draw the two concentric
        // circles anyway). So if you try to autoslew more than 120°,
        // you get a normal cubic segment with no slew.
        break;
      } else {
        // Draw concentric circles through the two tangent points.
        // What is the difference in radius? The following neat and _exact_
        // formula drops out of a flurry of trig and vector algebra:
        var n1 = t1.turnRight();
        var n4 = t4.turnRight();
        double radialDist = (chord.dot(n1) + chord.dot(n4)) / (1 + prod);
        if( Math.abs(radialDist) > 1 ) {
          slewBefore = radialDist/2;
          slewAfter = radialDist-slewBefore;
          p1 = p1.plus(slewBefore, n1);
          p4 = p4.plus(-slewAfter, n4);
          chord = p1.to(p4);
        }
        // use the general nice-curve code the rest of the way ..
        break;
      }
    case BOUND:
      return Bezier.line(p1, p4);
    }

    // When we reach here, we want a nice cubic curve with the already
    // computed endpoints and tangent directions.

    if( t1 == t4 && Math.abs(chord.dot(t1.turnRight())) < 1 )
      return Bezier.line(p1, p4);
    var chordlen = chord.length();
    var ctrllen2 = chordlen/3;
    var ctrllen3 = chordlen/3;
    // The following correction makes the curve approximate a circular arc
    // much better in the case where both ends actually lie on the same arc.
    // The magic constant 0.193 was found experimentally -- it makes the
    // difference from an actual circular arc less than 0.1% of the radius
    // for arcs up to 90°, and still less than 5% at 135°.
    // (It _also_ turns out to make the speed along the curve vary less,
    // useful as long as the warper doesn't do its own speed correction).
    ctrllen2 += 0.193 * (chordlen - chord.dot(t1));
    ctrllen3 += 0.193 * (chordlen - chord.dot(t4));
    var p2 = p1.plus(ctrllen2, t1);
    var p3 = p4.plus(-ctrllen3, t4);
    return Bezier.cubic(p1, p2, p3, p4);
  }

  private SegmentChain.Smoothed wrapUp() {
    return new SegmentChain.Smoothed(curves, nodeSlews, segmentSlews);
  }

}
