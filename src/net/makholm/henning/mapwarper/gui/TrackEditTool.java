package net.makholm.henning.mapwarper.gui;

import java.util.ArrayList;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackNode;

class TrackEditTool extends EditTool {

  protected TrackEditTool(Commands owner,
      SegKind kind, String kindDescription) {
    super(owner, kind, kindDescription);
  }

  @Override
  protected ProposedAction actionForDraggingNode(int index, int mod1,
      Point p2, int mod2) {
    if( !altHeld(mod2) )
      return super.actionForDraggingNode(index, mod1, p2, mod2);
    var chain = editingChain();
    var smoothed = chain.smoothed.get();
    TrackNode gp1 = chain.nodes.get(index);
    Point gp2 = translator().local2global(p2);

    // Alt-dragging can be used either to slide a single node along in the
    // direction of the track, or to offset (part of a chain) in a direction
    // perpendicular to the track. We find out which is which by projecting
    // the mouse position onto both _global_ lines, and then seeing which
    // one is closest in _local_ coordinates.

    UnitVector dirAlong = smoothed.direction(index);
    UnitVector dirAcross = smoothed.direction(index).turnRight();

    double distAlong = dirAlong.dot(gp1.to(gp2));
    double distAcross = dirAcross.dot(gp1.to(gp2));

    Point gpAlong = gp1.plus(distAlong, dirAlong);
    Point gpAcross = gp1.plus(distAcross, dirAcross);

    Point lpAlong = translator().global2localWithHint(gpAlong, p2);
    Point lpAcross = translator().global2localWithHint(gpAcross, p2);

    if( lpAlong.sqDist(p2) < lpAcross.sqDist(p2) ) {
      TrackNode n2 = global2node(gpAlong);
      var nodes = new ArrayList<>(chain.nodes);
      nodes.set(index, n2);
      var newChain = new SegmentChain(nodes, chain.kinds, chainClass);
      return rewriteTo("Slide node", newChain).with(n2);
    }

    // We offset the node _plus_ its neighbor nodes, until/unless we reach
    // a segment type that slews.
    // The offsetting distance can snap to values where the slew distance
    // at one of the ends is zero.

    double snap = Double.POSITIVE_INFINITY;
    int first, last;
    for( first=index; first > 0; first-- ) {
      if( isSlewing(chain.kinds.get(first-1)) ) {
        snap = smoothed.nodeSlew(first-1)-smoothed.nodeSlew(first);
        break;
      }
    }
    for( last=index; last < chain.numNodes-1; last++ ) {
      if( isSlewing(chain.kinds.get(last)) ) {
        var snap2 = smoothed.nodeSlew(last+1)-smoothed.nodeSlew(last);
        if( Math.abs(snap2-distAcross) < Math.abs(snap-distAcross) )
          snap = snap2;
        break;
      }
    }

    var snapdist = 5 * mapView().projection.scaleAcross();
    var doSnap = Math.abs(snap-distAcross) < snapdist;
    if( doSnap )
      distAcross = snap;

    var nodes = new ArrayList<>(chain.nodes);
    for( int i=first; i<=last; i++ ) {
      TrackNode g = nodes.get(i);
      UnitVector d = smoothed.direction(i).turnRight();
      nodes.set(i, global2node(g.plus(distAcross, d)));
    }
    var newChain = new SegmentChain(nodes, chain.kinds, chainClass);
    String undoDesc;
    if( first == last )
      undoDesc = "Offset node";
    else if( first+1 == last )
      undoDesc = "Offset segment";
    else if( first == 0 && last == chain.numNodes-1 )
      undoDesc = "Offset chain";
    else
      undoDesc = "Offset "+(last+1-first)+" segments";
    return rewriteTo(undoDesc, newChain).with(nodes.get(index), doSnap);
  }

  private static boolean isSlewing(SegKind kind) {
    return kind == SegKind.SLEW || kind == SegKind.MAGIC;
  }

}
