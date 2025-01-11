package net.makholm.henning.mapwarper.gui;

import java.util.ArrayList;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackNode;

class SlewingEditTool extends EditTool {

  protected SlewingEditTool(Commands owner,
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
    UnitVector dir1 = smoothed.direction(index).turnRight();
    Point gp2 = translator().local2global(p2);
    double movedist = dir1.dot(gp1.to(gp2));

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
        if( Math.abs(snap2-movedist) < Math.abs(snap-movedist) )
          snap = snap2;
        break;
      }
    }

    if( Math.abs(snap-movedist) < 5 * mapView().projection.scaleAcross() )
      movedist = snap;

    var nodes = new ArrayList<>(chain.nodes);
    for( int i=first; i<=last; i++ ) {
      TrackNode g = nodes.get(i);
      UnitVector d = smoothed.direction(i).turnRight();
      nodes.set(i, global2node(g.plus(movedist, d)));
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
    return new ProposedAction(undoDesc, null, nodes.get(index), null, newChain);
  }

  private static boolean isSlewing(SegKind kind) {
    return kind == SegKind.SLEW || kind == SegKind.MAGIC;
  }

}
