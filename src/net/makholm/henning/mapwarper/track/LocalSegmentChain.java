package net.makholm.henning.mapwarper.track;

import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.util.FrozenArray;
import net.makholm.henning.mapwarper.util.Lazy;
import net.makholm.henning.mapwarper.util.TreeList;
import net.makholm.henning.mapwarper.util.XyTree;

public class LocalSegmentChain {

  public final SegmentChain global;
  public final FrozenArray<PointWithNormal> nodes;
  public final FrozenArray<List<Bezier>> curves;
  public final IntPredicate boundDiscarder;

  public final Lazy<XyTree<List<ChainRef<Bezier>>>> segmentTree;
  public final Lazy<XyTree<ChainRef<Point>>> nodeTree;

  LocalSegmentChain(SegmentChain global, ProjectionWorker proj) {
    this.global = global;
    var globalCurves = global.smoothed.get();
    var nodes = new PointWithNormal[global.numNodes];
    var curves = new ArrayList<List<Bezier>>(global.numSegments);

    var globalNode = global.nodes.get(0);
    var localNode = proj.global2local(globalNode);
    Bezier lastLocalCurve = null;
    for( int i=0; i<global.numSegments; i++ ) {
      Bezier globalCurve = globalCurves.get(i);
      List<Bezier> localCurves;
      if( global.kinds.get(i) == SegKind.LBOUND )
        localCurves = List.of(Bezier.line(
            proj.global2local(globalCurve.p1),
            proj.global2local(globalCurve.p4)));
      else
        localCurves = proj.global2local(globalCurve);
      Bezier firstLocalCurve = localCurves.get(0);
      lastLocalCurve = localCurves.get(localCurves.size()-1);

      nodes[i] =
          new PointWithNormal(localNode, firstLocalCurve.dir1().turnRight());
      if( !globalNode.is(globalCurve.p1) ) {
        var line = Bezier.line(localNode, firstLocalCurve.p1);
        localCurves = TreeList.concat(singletonList(line), localCurves);
      }

      globalNode = global.nodes.get(i+1);
      localNode = proj.global2local(globalNode);
      if( !globalNode.is(globalCurve.p4) ) {
        var line = Bezier.line(lastLocalCurve.p4, localNode);
        localCurves = TreeList.concat(localCurves, singletonList(line));
      }

      curves.add(localCurves);
    }
    if( lastLocalCurve != null ) {
      nodes[global.numSegments] =
          new PointWithNormal(localNode, lastLocalCurve.dir4().turnRight());
    } else {
      // We must be a single-point chain ...
      nodes[global.numSegments] =
          new PointWithNormal(localNode, Vector.of(1,0).normalize());
    }

    this.nodes = FrozenArray.of(nodes);
    this.curves = FrozenArray.freeze(curves);
    if( global.chainClass == ChainClass.BOUND )
      boundDiscarder = proj.makeBoundDiscarder(global);
    else
      boundDiscarder = ProjectionWorker.DISCARD_NONE;

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
      for( int i=0; i<nodes.length; i++ ) {
        ChainRef<Point> sourced = ChainRef.of(nodes[i], global, i);
        var singleton = XyTree.singleton(nodes[i], sourced);
        tree = joiner.union(tree, singleton);
      }
      return tree;
    });
  }

}
