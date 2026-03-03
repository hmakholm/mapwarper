package net.makholm.henning.mapwarper.track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.BezierChain;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.util.FrozenArray;
import net.makholm.henning.mapwarper.util.Lazy;
import net.makholm.henning.mapwarper.util.TreeList;
import net.makholm.henning.mapwarper.util.XyTree;

public class LocalSegmentChain {

  /**
   * Beware, elements in this can be null for nodes that are not visible
   * in the current projection.
   */
  public final FrozenArray<LocalNode> nodes;

  public final FrozenArray<List<Bezier>> curves;
  public final IntPredicate boundDiscarder;

  public final Lazy<XyTree<List<ChainRef<Bezier>>>> segmentTree;
  public final Lazy<XyTree<ChainRef<Point>>> nodeTree;

  public static class LocalNode extends PointWithNormal {
    LocalNode(Point p, UnitVector n) {
      super(p.x, p.y, n);
    }
    public double neighborDist = 1000;
    /** Direction of adjoining track lines, if any */
    public UnitVector dIn, dOut;
  }

  static LocalSegmentChain make(SegmentChain global, ProjectionWorker proj) {
    var globalCurves = global.smoothed.get();
    var nodes = new LocalNode[global.numNodes];

    var curves = new ArrayList<List<Bezier>>(global.numSegments);

    var localNode = proj.global2local(global.nodes.get(0));
    for( int i=0; i<global.numSegments; i++ ) {
      var nextLocalNode = proj.global2local(global.nodes.get(i+1));
      var globalCurve = globalCurves.get(i);

      if( global.kinds.get(i) == SegKind.SLEW &&
          globalCurve.displacement.sqnorm() < 0.09*0.09 ) {
        // Special case for very short slews, possibly ones that were
        // prevented from going backwards
        var g1 = i==0 ? global.nodes.get(i) : globalCurves.get(i-1).p4;
        var g2 = i==global.numSegments-1 ?
            global.nodes.get(i+1) : globalCurves.get(i+1).p1;
        var c = Bezier.line(proj.global2local(g1), proj.global2local(g2));
        curves.add(List.of(c));
        localNode = nextLocalNode;
        continue;
      }

      BezierChain localCurve;
      if( global.kinds.get(i).showStraightDespiteWarp() )
        localCurve = Bezier.line(localNode, nextLocalNode);
      else
        localCurve = proj.global2local(globalCurve);
      List<Bezier> toDraw = localCurve.curves();

      Bezier first = localCurve.firstCurveOrNull();
      if( first != null ) {
        if( nodes[i] == null )
          nodes[i] = new LocalNode(localNode, first.dir1().turnRight());
        if( !globalCurve.p1.is(global.nodes.get(i)) ) {
          first = Bezier.line(localNode, first.p1);
          toDraw = TreeList.concat(List.of(first), toDraw);
        }
        nodes[i].dOut = first.dir1();
      }

      localNode = nextLocalNode;

      Bezier last = localCurve.lastCurveOrNull();
      if( last != null ) {
        nodes[i+1] = new LocalNode(localNode, last.dir4().turnRight());
        if( !globalCurve.p4.is(global.nodes.get(i+1)) ) {
          last = Bezier.line(last.p4, localNode);
          toDraw = TreeList.concat(toDraw, List.of(last));
        }
        nodes[i+1].dIn = last.dir4();
      }

      curves.add(toDraw);
    }
    for( int i=0; i<nodes.length; i++ ) {
      if( nodes[i] == null ) {
        Point local = proj.global2local(global.nodes.get(i));
        if( nodes.length == 1 || proj.isGoodLocalPoint(local))
          nodes[i] = new LocalNode(local, UnitVector.RIGHT);
      }
    }
    calculateCrosshairDistances(nodes);

    IntPredicate boundDiscarder;
    if( global.chainClass == ChainClass.BOUND )
      boundDiscarder = proj.makeBoundDiscarder(global);
    else
      boundDiscarder = ProjectionWorker.DISCARD_NONE;

    return new LocalSegmentChain(global, FrozenArray.of(nodes),
        FrozenArray.freeze(curves), boundDiscarder);
  }

  private static void calculateCrosshairDistances(LocalNode[] nodes) {
    LocalNode prev = null;
    for( var n : nodes ) {
      if( n != null ) {
        if( prev != null ) {
          n.neighborDist = n.dist(prev);
          prev.neighborDist = Math.min(prev.neighborDist, n.neighborDist);
        }
        prev = n;
      }
    }
  }

  private LocalSegmentChain(SegmentChain global,
      FrozenArray<LocalNode> nodes,
      FrozenArray<List<Bezier>> curves,
      IntPredicate discarder) {
    this.nodes = nodes;
    this.curves = curves;
    this.boundDiscarder = discarder;

    segmentTree = Lazy.of(() -> {
      var joiner = XyTree.<ChainRef<Bezier>>concatJoin();
      var tree = joiner.empty();
      for( int i=0; i<this.curves.size(); i++ ) {
        for( var curve : this.curves.get(i) ) {
          var sourced = ChainRef.of(curve, global, i);
          List<ChainRef<Bezier>> onelist = Collections.singletonList(sourced);
          var singleton = XyTree.singleton(curve.bbox.get(), onelist);
          tree = joiner.union(tree, singleton);
        }
      }
      return tree;
    });

    nodeTree = Lazy.of(() -> {
      var joiner = XyTree.<ChainRef<Point>>leftWinsJoin();
      var tree = joiner.empty();
      for( int i=0; i<nodes.size(); i++ ) {
        var node = nodes.get(i);
        if( node != null ) {
          ChainRef<Point> sourced = ChainRef.of(node, global, i);
          var singleton = XyTree.singleton(node, sourced);
          tree = joiner.union(tree, singleton);
        }
      }
      return tree;
    });
  }

  /**
   * This is used as a symbol for openable track chains that would otherwise
   * show as very small.
   *
   * A diamond is chosen because it is easy to define <em>and</em> cheap
   * to draw.
   */
  static LocalSegmentChain diamond(SegmentChain global, Point local) {
    double size = 7;
    var p1 = local.plus(-size, UnitVector.DOWN);
    var p2 = local.plus(size, UnitVector.RIGHT);
    var p3 = local.plus(size, UnitVector.DOWN);
    var p4 = local.plus(-size, UnitVector.RIGHT);
    var curves = List.of(
        Bezier.line(p1,p2),
        Bezier.line(p2,p3),
        Bezier.line(p3,p4),
        Bezier.line(p4,p1));
    var pwn = new LocalNode(p1, UnitVector.RIGHT);
    return new LocalSegmentChain(global,
        FrozenArray.of(pwn, pwn),
        FrozenArray.of(curves),
        ProjectionWorker.DISCARD_NONE);
  }

}
