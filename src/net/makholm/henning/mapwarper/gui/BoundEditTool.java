package net.makholm.henning.mapwarper.gui;

import java.util.ArrayList;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.util.TreeList;

class BoundEditTool extends BoundSnappingTool {

  protected BoundEditTool(Commands owner) {
    super(owner, SegKind.BOUND, "bound line");
  }

  @Override
  protected ProposedAction actionForDraggingNode(int index, int mod1,
      Point p2, int mod2) {
    if( !altHeld(mod2) )
      return super.actionForDraggingNode(index, mod1, p2, mod2);
    var chain = editingChain();

    var n = chain.nodes.get(index);
    var moved = n.to(translator().local2global(p2));

    UnitVector dir = null;
    if( index > 0 ) {
      dir = chain.nodes.get(index-1).to(n).normalize();
    }
    if( index < chain.numNodes-1 ) {
      var dirB = chain.nodes.get(index+1).to(n).normalize();
      if( dir == null ||
          Math.abs(dirB.dot(moved)) > Math.abs(dir.dot(moved)) )
        dir = dirB;
    }
    if( dir == null ) return null;

    var n2 = global2node(n.plus(dir.dot(moved), dir));
    var nodes = new ArrayList<>(chain.nodes);
    nodes.set(index, n2);
    var newChain = new SegmentChain(nodes, chain.kinds, chainClass);
    return new ProposedAction("Slide bounds node", null, n2, null, newChain);
  }


  /**
   * Alt-dragging a fresh node enables a magic tangent interpolation mode.
   */
  @Override
  protected ProposedAction actionInFreeSpace(
      Point l1, int mod1, Point l2, int mod2) {
    if( !altHeld(mod2) )
      return super.actionInFreeSpace(l1, mod1, l2, mod2);
    var chain = editingChain();

    LineSeg gdragged = new LineSeg(
        translator().local2global(l1),
        translator().local2global(l2));

    if( l1.dist(l2) >= 5 ) {
      ChainRef<?> closest = FindClosest.point(
          chain.nodeTree.get(), ChainRef::data,
          Double.POSITIVE_INFINITY,
          gdragged.a);

      if( closest != null ) {
        ProposedAction got;
        if( closest.index() <= 0 )
          got = extendFirst(chain, l1, gdragged, l2);
        else if( closest.index() >= chain.numNodes-1 )
          got = extendLast(chain, l1, gdragged, l2);
        else
          got = internalTangent(chain, closest.index(), gdragged);
        if( got != null ) return got;
      }
    }

    ChainRef<?> closestSeg = FindClosest.curve(
        chain.curveTree.get(), ChainRef::data,
        Double.POSITIVE_INFINITY,
        gdragged.a,
        mapView().projection.scaleAcross());
    if( closestSeg != null )
      return extrapolateSegmentAway(chain, closestSeg.index(), gdragged.b);
    else
      return null;
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

  private ProposedAction internalTangent(SegmentChain chain, int insertAt,
      LineSeg dragged) {
    // the drawn line is not really "normal" to anything, but calling it a
    // normal makes code for intersecting the line with existing segments
    // available...
    var pwna = new PointWithNormal(dragged.a, dragged.normalize());

    double distPrev = pwna.intersectWithNormal(lineSeg(chain, insertAt-1));
    double distNext = pwna.intersectWithNormal(lineSeg(chain, insertAt));
    if( Double.isNaN(distPrev) || Double.isNaN(distNext) ) return null;
    if( (distPrev > 0) == (distNext > 0) ) return null;

    var nodes = TreeList.concat(
        chain.nodes.subList(0, insertAt),
        List.of(super.global2node(pwna.pointOnNormal(distPrev)),
            super.global2node(pwna.pointOnNormal(distNext))),
        chain.nodes.subList(insertAt+1, chain.numNodes));
    var kinds = TreeList.concat(
        chain.kinds.subList(0, insertAt),
        List.of(kind),
        chain.kinds.subList(insertAt, chain.numSegments));
    var newChain = new SegmentChain(nodes, kinds, chainClass);
    return new ProposedAction("Interpolate @", null, pwna, null, newChain);
  }

  private LineSeg lineSeg(SegmentChain chain, int index) {
    if( index < 0 || index >= chain.numSegments )
      return null;
    else
      return new LineSeg(chain.nodes.get(index), chain.nodes.get(index+1));
  }

  private ProposedAction extrapolateSegmentAway(SegmentChain chain,
      int index, Point mouseGlobal) {
    if( index <= 0 || index >= chain.numSegments-1 )
      return null;
    TrackNode n1 = chain.nodes.get(index-1);
    TrackNode n2 = chain.nodes.get(index);
    TrackNode n3 = chain.nodes.get(index+1);
    TrackNode n4 = chain.nodes.get(index+2);
    LineSeg n12 = n1.to(n2), n23 = n2.to(n3), n34 = n3.to(n4);
    boolean rightOfN23 = n23.isPointToTheRight(mouseGlobal);
    if( rightOfN23 == n12.isPointToTheRight(mouseGlobal) ||
        rightOfN23 == n34.isPointToTheRight(mouseGlobal) )
      return null;

    var pwn1 = new PointWithNormal(n1, n12.normalize());
    var dist1 = pwn1.intersectInfiniteLineWithNormal(n34);
    var pwn4 = new PointWithNormal(n4, n34.reverse().normalize());
    var dist4 = pwn4.intersectInfiniteLineWithNormal(n12);

    if( dist1 < 0 || dist4 < 0 )
      return null;

    TrackNode nmid = global2node(pwn1.pointOnNormal(dist1));
    var nodes = TreeList.concat(
        chain.nodes.subList(0, index),
        List.of(nmid),
        chain.nodes.subList(index+2, chain.numNodes));
    var kinds = TreeList.concat(
        chain.kinds.subList(0, index),
        chain.kinds.subList(index+1, chain.numSegments));
    var newChain = new SegmentChain(nodes, kinds, chainClass);

    var highlight = new TrackHighlight(newChain, index-1, index+1, kind.rgb);
    return new ProposedAction("Contract bound node", highlight, null,
        null, newChain);
  }

}
