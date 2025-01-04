package net.makholm.henning.mapwarper.gui;

import java.util.ArrayList;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.gui.track.SegmentChain;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.TrackNode;

class StraightEditTool extends EditTool {

  protected StraightEditTool(Commands owner) {
    super(owner, SegKind.STRAIGHT, "straight track");
  }

  @Override
  protected ProposedAction actionForDraggingNode(int index, int mod1,
      Point p2, int mod2) {
    if( !altHeld(mod2) )
      return super.actionForDraggingNode(index, mod1, p2, mod2);

    var chain = editingChain();

    TrackNode orig = chain.nodes.get(index);
    UnitVector direction = chain.smoothed.get().direction(index);
    Point globalP2 = translator().local2global(p2);
    double movedist = direction.dot(orig.to(globalP2));
    Point useGlobal = orig.plus(movedist, direction);

    TrackNode n2 = global2node(useGlobal);
    var nodes = new ArrayList<>(chain.nodes);
    nodes.set(index, n2);
    var newChain = new SegmentChain(nodes, chain.kinds, chainClass);
    return new ProposedAction("Slide node", null, n2, null, newChain);
  }

}
