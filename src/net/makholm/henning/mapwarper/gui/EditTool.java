package net.makholm.henning.mapwarper.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.track.ChainClass;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.util.TreeList;

class EditTool extends Tool implements StandardAction.Context {
  protected final SegKind kind;
  protected final List<SegKind> kindx1;
  protected final String kindDescription;
  protected final ChainClass chainClass;

  protected EditTool(Commands owner, SegKind kind, String kindDescription) {
    super(owner, kind.toString(), "Draw "+kindDescription+"s");
    this.kind = kind;
    this.kindx1 = List.of(kind);
    this.kindDescription = kindDescription;
    this.chainClass = kind.chainClass();

    toolCursor = loadCursor("crosshairCursor.png");
  }

  @Override
  public final void activeFileChanged() {
    super.activeFileChanged();
    sanitizeEditingStateWhenSelected();
  }

  @Override
  protected boolean canEscapeBackTo(Tool other) {
    return other instanceof EditTool;
  }

  @Override
  public void anActionHasExecuted() {
    enableSameKeyCancel();
  }

  @Override
  public ToolResponse mouseResponse(Point local, int modifiers) {
    var action = decideAction(local, modifiers, local, modifiers);
    if( action == null )
      action = noopAction(local);
    if( shiftHeld(modifiers) || mouseHeld(modifiers) )
      action = action.withPreview();
    return action.freeze();
  }

  @Override
  public MouseAction drag(Point p1, int mod1) {
    var clickAction = decideAction(p1, mod1, p1, mod1);
    if( clickAction != null && clickAction.executeIfSelectingChain() )
      mapView().collectVisibleTrackData();
    return (p2, mod2) -> {
      var action = decideAction(p1, mod1, p2, mod2);
      if( action == null )
        action = noopAction(p2);
      var response = action.withPreview().freeze();
      if( !new AxisRect(mapView().visibleArea).contains(p2) ) {
        return new WrappedToolResponse(response) {
          @Override public void execute(ExecuteWhy why) { SwingUtils.beep(); }
        };
      } else
        return response;
    };
  }

  private static final int PICK_CURVE_TOLERANCE = 4;
  private static final int PICK_START_DISTANCE = 10;

  protected ProposedAction decideAction(Point p1, int mod1, Point p2, int mod2) {
    // If you point near the current editing chain, that wins
    if( editingChain() != null ) {
      var localChain = editingChain().localize(translator());
      ChainRef<?> found = FindClosest.point(
          localChain.nodeTree.get(), ChainRef::data,
          PICK_START_DISTANCE, p1);
      if( found != null )
        return actionFromEditingPoint(found.index(), p1, mod1, p2, mod2);

      found = FindClosest.curve(
          localChain.segmentTree.get(), ChainRef::data,
          PICK_START_DISTANCE, p1, PICK_CURVE_TOLERANCE);
      if( found != null )
        return actionFromEditingSegment(found.index(), p1, mod1, p2, mod2);
    }

    if( ctrlHeld(mod1) ) {
      // everything else is not a deletion action
      return null;
    }

    // Else, if you point to another chain _of the right class_
    // in the open file, use that
    ChainRef<?> found = FindClosest.point(
        activeFileContent().nodeTree(translator()), ChainRef::data,
        PICK_START_DISTANCE, p1);
    if( found != null && found.chain().chainClass == chainClass )
      return actionFromOtherPoint(found, p1, mod1, p2, mod2);

    found = FindClosest.curve(
        activeFileContent().segmentTree.apply(translator()), ChainRef::data,
        PICK_START_DISTANCE, p1, PICK_CURVE_TOLERANCE);
    if( found != null && found.chain().chainClass == chainClass )
      return actionFromOtherSegment(found, p1, mod1, p2, mod2);

    if( editingChain() == null )
      return actionWithNoEditingChain(p1, mod1, p2, mod2);
    else
      return actionInFreeSpace(p1, mod1, p2, mod2);
  }

  protected ProposedAction actionFromEditingPoint(int index,
      Point p1, int mod1, Point p2, int mod2) {
    if( ctrlHeld(mod1) ) {
      // Ctrl-click deletes a point -- which deletes an entire segment
      // if it's an endpoint.
      var chain = editingChain();
      if( chain.numSegments <= 1 ) {
        var highlight = new TrackHighlight(chain, DELETE_HIGHLIGHT);
        return killTheChain("Delete chain").with(highlight);
      } else if( index == 0 ) {
        return actionFromEditingSegment(0, p1, mod1, p2, mod2);
      } else if( index == chain.numNodes-1 ) {
        return actionFromEditingSegment(index-1, p1, mod1, p2, mod2);
      } else {
        var nodes = new ArrayList<>(chain.nodes);
        var kinds = new ArrayList<>(chain.kinds);
        nodes.remove(index);
        kinds.remove(index);
        kinds.set(index-1, kind);
        var newChain = new SegmentChain(nodes, kinds, chainClass);
        var highlight = new TrackHighlight(chain, index-1, index+1, kind.rgb);
        return rewriteTo("Delete node", newChain).with(highlight);
      }
    } else if( p1.dist(p2) < 3 ) {
      // This is probably just a plain click. If you want to actually move
      // a node this little, zoom in first!
      var hl = new TrackHighlight(editingChain(), index, 0xFFFFFF);
      return noopAction("!Don't move node").with(hl);
    } else {
      return actionForDraggingNode(index, mod1, p2, mod2);
    }
  }

  protected ProposedAction actionForDraggingNode(int index, int mod1,
      Point p2, int mod2) {
    var chain = editingChain();
    TrackNode n2 = local2node(p2);
    var nodes = new ArrayList<>(chain.nodes);
    nodes.set(index, n2);
    var newChain = new SegmentChain(nodes, chain.kinds, chainClass);
    return rewriteTo("Move node", newChain).with(n2);
  }

  static final int DELETE_HIGHLIGHT = 0xFF0000;

  protected ProposedAction actionFromEditingSegment(int index,
      Point p1, int mod1, Point p2, int mod2) {
    SegmentChain chain = editingChain();
    if( ctrlHeld(mod1) ) {
      // Ctrl-click deletes a segment.
      // This may or may not split the chain into two, or remove it
      // completely.
      TrackHighlight highlight =
          new TrackHighlight(chain, index, index+1, DELETE_HIGHLIGHT);
      if( chain.numSegments <= 1 ) {
        return killTheChain("Delete chain").with(highlight);
      } else if( index == 0 ) {
        return rewriteTo("Delete segment",
            chain.subchain(1,chain.numSegments)).with(highlight);
      } else if( index == chain.numSegments-1 ) {
        return rewriteTo("Delete segment",
            chain.subchain(0, index)).with(highlight);
      } else {
        var front = chain.subchain(0, index);
        var back = chain.subchain(index+1, chain.numSegments);
        return StandardAction.split(this, "Delete segment", front, p2, back)
            .with(highlight);
      }
    } else if( chain.kinds.get(index) == kind ) {
      return actionInFreeSpace(p1, mod1, p2, mod2);
    } else {
      var kinds = new ArrayList<>(chain.kinds);
      kinds.set(index, kind);
      var newChain = new SegmentChain(chain.nodes, kinds, chainClass);
      var highlight = new TrackHighlight(chain, index, index+1, kind.rgb);
      return rewriteTo("Change to @", newChain).with(highlight);
    }
  }

  final protected ProposedAction actionFromOtherPoint(ChainRef<?> which,
      Point p1, int mod1, Point p2, int mod2) {
    if( which.index() == 0 ) {
      ProposedAction join = joinChainsAction(editingChain(), which.chain());
      if( join != null )
        return join;
    } else if( which.index() == which.chain().numNodes-1 ) {
      ProposedAction join = joinChainsAction(which.chain(), editingChain());
      if( join != null )
        return join;
    }
    return selectEditingChain(which, p2);
  }

  protected ProposedAction joinChainsAction(SegmentChain a, SegmentChain b) {
    if( a == null || b == null || a == b ) return null;
    if( a.numNodes >= 2 && cosTurn(
        a.nodes.get(a.numNodes-2),
        a.nodes.get(a.numNodes-1),
        b.nodes.get(0)) < 0.8 ) return null;
    if( b.numNodes >= 2 && cosTurn(
        a.nodes.get(a.numNodes-1),
        b.nodes.get(0),
        b.nodes.get(1)) < 0.8 ) return null;

    SegmentChain joined = new SegmentChain(
        TreeList.concat(a.nodes, b.nodes),
        TreeList.concat(a.kinds, TreeList.concat(kindx1, b.kinds)));

    TrackHighlight highlight =
        new TrackHighlight(joined, a.numSegments, a.numSegments+1, kind.rgb);

    return StandardAction.join(this, "Join segment chains",
        List.of(a,b), joined).with(highlight);
  }

  protected ProposedAction actionFromOtherSegment(ChainRef<?> which,
      Point p1, int mod1, Point p2, int mod2) {
    return selectEditingChain(which, p2);
  }

  protected ProposedAction selectEditingChain(ChainRef<?> which, Point p2) {
    var theChain = which.chain();
    return StandardAction.switchChain(mapView(), theChain);
  }

  protected ProposedAction actionWithNoEditingChain(
      Point p1, int mod1, Point p2, int mod2) {
    if( altHeld(mod1) ) return null;
    if( p1.dist(p2) < 5 ) {
      TrackNode n = local2node(p1);
      return createChain("Create new node", singletonChain(n)).with(n);
    } else {
      TrackNode n1 = local2node(p1);
      TrackNode n2 = local2node(p2);
      var chain = new SegmentChain(Arrays.asList(n1, n2), kindx1);
      return createChain("Draw new @", chain).with(n2);
    }
  }

  protected ProposedAction actionInFreeSpace(
      Point p1, int mod1, Point p2, int mod2) {
    if( altHeld(mod1) ) return null;
    SegmentChain chain = editingChain();
    if( chain.numNodes == 1 ) {
      TrackNode n1 = chain.nodes.get(0);
      TrackNode n2 = local2node(p2);
      var newChain = new SegmentChain(Arrays.asList(n1, n2), kindx1);
      return rewriteTo("Draw new @", newChain).with(n2);
    }

    // Use p1 to decide _where_ to insert the point, but p2 for where
    // to actually insert it.
    var gp1 = translator().local2global(p1);
    var n = local2node(p2);
    ChainRef<?> found = FindClosest.point(
        chain.nodeTree.get(), ChainRef::data,
        Double.POSITIVE_INFINITY, gp1);
    int i = found.index();

    boolean insertBefore;
    if( i == 0 ) {
      if( shouldAppend(gp1, chain.nodes.get(0), chain.nodes.get(1)) ) {
        var newChain = new SegmentChain(
            TreeList.concat(List.of(n), chain.nodes),
            TreeList.concat(kindx1, chain.kinds));
        return rewriteTo("Append @", newChain).with(n);
      } else {
        insertBefore = false;
      }
    } else if( i == chain.numNodes-1 ) {
      if( shouldAppend(gp1, chain.nodes.get(i), chain.nodes.get(i-1)) ) {
        var newChain = new SegmentChain(
            TreeList.concat(chain.nodes, List.of(n)),
            TreeList.concat(chain.kinds, kindx1));
        return rewriteTo("Append @", newChain).with(n);
      } else {
        insertBefore = true;
      }
    } else {
      insertBefore = cosTurn(chain.nodes.get(i-1),gp1,chain.nodes.get(i))
          > cosTurn(chain.nodes.get(i),gp1,chain.nodes.get(i+1));
    }

    var newNodes = new ArrayList<>(chain.nodes);
    var newKinds = new ArrayList<>(chain.kinds);
    newNodes.add(insertBefore ? i : i+1, n);
    newKinds.add(i, kind);
    var newChain = new SegmentChain(newNodes, newKinds);
    return rewriteTo("Insert @", newChain).with(n);
  }

  protected static boolean shouldAppend(
      Point newNode, TrackNode endpoint, TrackNode penultimate) {
    return cosTurn(newNode, endpoint, penultimate)
        > cosTurn(endpoint, newNode, penultimate);
  }

  protected static double cosTurn(Point p1, Point p2, Point p3) {
    return p1.to(p2).normalize().dot(p2.to(p3).normalize());
  }

  protected ProposedAction noopAction(Point p2) {
    return noopAction("!Do nothing").with(local2node(p2));
  }

  @Override
  public void sanitizeEditingStateWhenSelected() {
    var logic = mapView();

    // cancel current chain if it has the wrong class
    if( logic.editingChain != null &&
        logic.editingChain.chainClass != chainClass ) {
      // a single-node chain gets converted to a chain of the appropriate
      // type, though.
      if( logic.editingChain.numNodes == 1 ) {
        var theNode = logic.editingChain.nodes.get(0);
        logic.setEditingChain(singletonChain(theNode));
      } else {
        logic.setEditingChain(null);
      }
    }

    // If there is no current chain, see if there's an obvious choice to set
    if( logic.editingChain == null )
      logic.setEditingChain(
          logic.files.activeFile().content().uniqueChain(chainClass));
  }

  public SegmentChain singletonChain(TrackNode theNode) {
    return new SegmentChain(List.of(theNode), List.of(), chainClass);
  }

  protected TrackNode local2node(Point local) {
    return global2node(translator().local2global(local));
  }

  protected TrackNode global2node(Point global) {
    long x = Math.round(global.x);
    long y = Math.round(global.y);
    int mask = Coords.EARTH_SIZE-1;
    return new TrackNode((int)x&mask, (int)y&mask);
  }

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

  @Override
  public String finalizeUndoDesc(String template) {
    return template.replace("@", kindDescription);
  }

  @Override
  public int circleCursorColor() {
    return kind.rgb;
  }

}
