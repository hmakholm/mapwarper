package net.makholm.henning.mapwarper.gui;

import java.util.ArrayList;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.gui.overlays.CircleOverlay;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.util.TreeList;

class BoundEditTool extends BoundSnappingTool {

  protected BoundEditTool(Commands owner) {
    super(owner, SegKind.BOUND);
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
    return rewriteTo("Slide bounds node", newChain).with(n2);
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
    return rewriteTo("Extrapolate @", newChain).with(newEndNode);
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
    return rewriteTo("Extrapolate @", newChain).with(newEndNode);
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
    var pivot = new CircleOverlay(kind.rgb, 10, dragged.a);
    return rewriteTo("Interpolate @", newChain).with(pivot);
  }

  private LineSeg lineSeg(SegmentChain chain, int index) {
    if( index < 0 || index >= chain.numSegments )
      return null;
    else
      return new LineSeg(chain.nodes.get(index), chain.nodes.get(index+1));
  }

  @Override
  protected ProposedAction actionFromEditingSegment(int index,
      Point p1, int mod1, Point p2, int mod2) {
    if( !altHeld(mod2) )
      return super.actionFromEditingSegment(index, p1, mod1, p2, mod2);
    var chain = editingChain();
    if( index <= 0 || index >= chain.numSegments-1 )
      return null;
    TrackNode n1 = chain.nodes.get(index-1);
    TrackNode n2 = chain.nodes.get(index);
    TrackNode n3 = chain.nodes.get(index+1);
    TrackNode n4 = chain.nodes.get(index+2);
    LineSeg n12 = n1.to(n2), n23 = n2.to(n3), n34 = n3.to(n4);

    var pwn1 = new PointWithNormal(n1, n12.normalize());
    var dist1 = pwn1.intersectInfiniteLineWithNormal(n34);
    var pwn4 = new PointWithNormal(n4, n34.reverse().normalize());
    var dist4 = pwn4.intersectInfiniteLineWithNormal(n12);

    var maxdist = n12.length() + n23.length() + n34.length();
    if( dist1 < -4 || dist1 > maxdist || dist4 < -4 || dist4 > maxdist )
      return null;

    List<TrackNode> mnodes;
    List<SegKind> mkinds;
    if( dist1 <= 4 || dist4 <= 4 ) {
      // The intersection point practically equals n1, so the first of the
      // three segments disappears
      mnodes = List.of();
      mkinds = List.of(chain.kinds.get(index-1));
    } else if( dist4 <= 4 ) {
      mnodes = List.of();
      mkinds = List.of(chain.kinds.get(index+1));
      // we should replace the three segments with a _single_ one
      mnodes = List.of();
      mkinds = List.of(chain.kinds.get(index));
    } else {
      mnodes = List.of(global2node(pwn1.pointOnNormal(dist1)));
      mkinds = List.of(chain.kinds.get(index-1), chain.kinds.get(index+1));
    }
    var nodes = TreeList.concat(
        chain.nodes.subList(0, index),
        mnodes,
        chain.nodes.subList(index+2, chain.numNodes));
    var kinds = TreeList.concat(
        chain.kinds.subList(0, index-1),
        mkinds,
        chain.kinds.subList(index+2, chain.numSegments));
    var newChain = new SegmentChain(nodes, kinds, chainClass);

    var highlight = new TrackHighlight(chain, index, index+1, kind.rgb);
    return rewriteTo("Contract bound segment", newChain)
        .with(highlight).withPreview();
  }

}
