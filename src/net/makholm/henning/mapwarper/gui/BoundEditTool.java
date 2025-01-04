package net.makholm.henning.mapwarper.gui;

import java.util.List;

import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.gui.track.ChainRef;
import net.makholm.henning.mapwarper.gui.track.SegmentChain;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.util.TreeList;

class BoundEditTool extends EditTool {

  protected BoundEditTool(Commands owner) {
    super(owner, SegKind.BOUND, "bound line");
  }

  private static final int SNAP_DISTANCE = 10;

  /**
   * New nodes snap to bound nodes from this or other visible files.
   */
  @Override
  protected TrackNode local2node(Point local) {
    ChainRef<?> found = FindClosest.point(
        activeFileContent().nodeTree(translator()), ChainRef::data,
        SNAP_DISTANCE, local);
    if( found != null && found.chain().chainClass == SegKind.BOUND ) {
      return found.chain().nodes.get(found.index());
    }

    found = FindClosest.point(
        mapView().currentVisible.otherBoundNodeTree.apply(translator()),
        ChainRef::data, SNAP_DISTANCE, local);
    if( found != null )
      return found.chain().nodes.get(found.index());

    return super.local2node(local);
  }

  /**
   * Alt-dragging a fresh node enables a magic tangent interpolation mode.
   */
  @Override
  protected ProposedAction actionInFreeSpace(
      Point l1, int mod1, Point l2, int mod2) {
    if( !altHeld(mod2) )
      return super.actionInFreeSpace(l1, mod1, l2, mod2);
    var chain = mapView().editingChain;
    if( l1.dist(l2) < 5 || chain.numSegments == 0 )
      return null;

    LineSeg gdragged = new LineSeg(
        translator().local2global(l1),
        translator().local2global(l2));
    var got = internalTangent(chain, gdragged);
    if( got != null ) return got;

    if( gdragged.a.dist(chain.nodes.get(0))
        < gdragged.a.dist(chain.nodes.last()) )
      return extendFirst(chain, l1, gdragged, l2);
    else
      return extendLast(chain, l1, gdragged, l2);
  }

  private ProposedAction internalTangent(SegmentChain chain, LineSeg dragged) {
    // the drawn line is not really "normal" to anything, but calling it a
    // normal makes code for intersecting the line with existing segments
    // available...
    var pwna = new PointWithNormal(dragged.a, dragged.normalize());

    double closest = Double.POSITIVE_INFINITY;
    int closestIndex = -1;
    for( int i=0; i<chain.numSegments; i++ ) {
      double cut = pwna.intersectWithNormal(lineSeg(chain, i));
      if( Math.abs(cut) < Math.abs(closest) ) {
        closest = cut;
        closestIndex = i;
      }
    }
    if( closestIndex < 0 ) return null;

    if( closest > 0 ) {
      closest = -closest;
      pwna = pwna.reverse();
    }
    int insertAt;
    double distPrev = pwna.intersectWithNormal(lineSeg(chain, closestIndex-1));
    double distNext = pwna.intersectWithNormal(lineSeg(chain, closestIndex+1));
    if( distPrev > 0 && distPrev < distNext ) {
      distNext = closest;
      insertAt = closestIndex;
    } else if( distNext > 0 && distNext < distPrev ) {
      distPrev = closest;
      insertAt = closestIndex+1;
    } else {
      return null;
    }

    var nodes = TreeList.concat(
        chain.nodes.subList(0, insertAt),
        List.of(super.global2node(pwna.pointOnNormal(distPrev)),
            super.global2node(pwna.pointOnNormal(distNext))),
        chain.nodes.subList(insertAt+1, chain.numNodes));
    var kinds = TreeList.concat(
        chain.kinds.subList(0, closestIndex+1),
        chain.kinds.subList(closestIndex, chain.numSegments));
    var newChain = new SegmentChain(nodes, kinds, chainClass);
    return new ProposedAction("Interpolate @", null,
        global2node(pwna, 0),
        null, newChain);
  }

  private ProposedAction extendFirst(SegmentChain chain,
      Point l1, LineSeg dragged, Point l2) {
    var n0 = chain.nodes.get(0);
    var n1 = chain.nodes.get(1);
    var pwn1 = new PointWithNormal(n1, n1.to(n0).normalize());
    var dist = pwn1.intersectInfiniteLineWithNormal(dragged);
    if( dist <= 0 ) return null;
    var newInnerNode = global2node(pwn1.pointOnNormal(dist));

    if( dragged.a.to(newInnerNode).dot(dragged) > 0 )
      l2 = l1;
    var newEndNode = local2node(l2);

    var nodes = TreeList.concat(
        List.of(newEndNode, newInnerNode),
        chain.nodes.subList(1, chain.numNodes));
    var kinds = TreeList.concat(List.of(chain.kinds.get(0)), chain.kinds);
    var newChain = new SegmentChain(nodes, kinds, chainClass);
    return new ProposedAction("Extrapolate @", null, newEndNode, null, newChain);
  }

  private ProposedAction extendLast(SegmentChain chain,
      Point l1, LineSeg dragged, Point l2) {
    var n8 = chain.nodes.get(chain.nodes.size()-2);
    var n9 = chain.nodes.get(chain.nodes.size()-1);
    var pwn8 = new PointWithNormal(n8, n8.to(n9).normalize());
    var dist = pwn8.intersectInfiniteLineWithNormal(dragged);
    if( dist <= 0 ) return null;
    var newInnerNode = global2node(pwn8.pointOnNormal(dist));

    if( dragged.a.to(newInnerNode).dot(dragged) > 0 )
      l2 = l1;
    var newEndNode = local2node(l2);

    var nodes = TreeList.concat(
        chain.nodes.subList(0, chain.numNodes-1),
        List.of(newInnerNode, newEndNode));
    var kinds = TreeList.concat(chain.kinds, List.of(chain.kinds.last()));
    var newChain = new SegmentChain(nodes, kinds, chainClass);
    return new ProposedAction("Extrapolate @", null, newEndNode, null, newChain);
  }

  private LineSeg lineSeg(SegmentChain chain, int index) {
    if( index < 0 || index >= chain.numSegments )
      return null;
    else
      return new LineSeg(chain.nodes.get(index), chain.nodes.get(index+1));
  }

}
