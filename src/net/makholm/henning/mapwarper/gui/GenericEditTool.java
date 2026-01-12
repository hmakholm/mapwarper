package net.makholm.henning.mapwarper.gui;

import java.util.List;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.util.XyTree;

/**
 * Common code for editing tools that might not be bound to a particular
 * segment kind.
 */
abstract class GenericEditTool extends Tool implements StandardAction.Context {

  GenericEditTool(Commands owner, String codename, String niceName) {
    super(owner, codename, niceName);
  }

  static final int DELETE_HIGHLIGHT = 0xFF0000;

  @Override
  protected boolean canEscapeBackTo(Tool other) {
    return other instanceof EditTool;
  }

  // -------------------------------------------------------------------------

  private static final int PICK_CURVE_TOLERANCE = 4;
  private static final int PICK_START_DISTANCE = 10;

  static ChainRef<Point> pickNode(Point p, XyTree<ChainRef<Point>> tree) {
    return FindClosest.point(tree, ChainRef::data, PICK_START_DISTANCE, p);
  }

  ChainRef<Point> pickNodeInActive(Point local) {
    if( editingChain() == null ) return null;
    return pickNode(local,
        editingChain().localize(translator()).nodeTree.get());
  }

  static ChainRef<Bezier> pickSegment(Point p,
      XyTree<List<ChainRef<Bezier>>> tree) {
    return FindClosest.curve(tree, ChainRef::data,
        PICK_START_DISTANCE, p, PICK_CURVE_TOLERANCE);
  }

  ChainRef<Bezier> pickSegmentInActive(Point local) {
    if( editingChain() == null ) return null;
    return pickSegment(local,
        editingChain().localize(translator()).segmentTree.get());
  }

  ChainRef<?> pickSegmentAnyChain(Point local) {
    return pickSegment(local,
        activeFileContent().segmentTree.apply(translator()));
  }

  // -------------------------------------------------------------------------

  protected TrackNode local2node(Point local) {
    return global2node(translator().local2global(local));
  }

  protected TrackNode global2node(Point global) {
    long x = Math.round(global.x);
    long y = Math.round(global.y);
    int mask = Coords.EARTH_SIZE-1;
    return new TrackNode((int)x&mask, (int)y&mask);
  }

  // -------------------------------------------------------------------------

  protected final StandardAction noopAction(String undoDesc) {
    return StandardAction.noop(this, undoDesc);
  }

  protected final StandardAction rewriteTo(String undoDesc,
      SegmentChain newChain) {
    return StandardAction.simple(this, undoDesc, newChain);
  }

  protected final StandardAction killTheChain(String undoDesc) {
    return StandardAction.common(this, undoDesc, List.of(), null);
  }

  protected final StandardAction createChain(String undoDesc,
      SegmentChain newChain) {
    return StandardAction.join(this, undoDesc, List.of(), newChain);
  }

  protected ProposedAction selectEditingChain(ChainRef<?> which) {
    var theChain = which.chain();
    return StandardAction.switchChain(mapView(), theChain);
  }

}
