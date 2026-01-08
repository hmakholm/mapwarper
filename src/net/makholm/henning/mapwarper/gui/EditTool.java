package net.makholm.henning.mapwarper.gui;

import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.gui.overlays.CircleOverlay;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.track.ChainClass;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.track.VisibleTrackData;
import net.makholm.henning.mapwarper.util.TreeList;

class EditTool extends Tool {
  protected final SegKind kind;
  protected final List<SegKind> kindx1;
  protected final String kindDescription;
  protected final ChainClass chainClass;

  protected EditTool(Commands owner, SegKind kind, String kindDescription) {
    super(owner, kind.toString(), "Draw "+kindDescription+"s");
    this.kind = kind;
    this.kindx1 = singletonList(kind);
    this.kindDescription = kindDescription;
    this.chainClass = kind.chainClass();

    toolCursor = loadCursor("crosshairCursor.png");
  }

  @Override
  public void invoke() {
    super.invoke();
    ensureAppropriateEditingChain();
  }

  @Override
  public final void activeFileChanged() {
    super.activeFileChanged();
    ensureAppropriateEditingChain();
  }

  record ProposedAction(String undoDesc,
      TrackHighlight highlight, Point newGlobal,
      Set<SegmentChain> fileContent, SegmentChain editingChain) {}

  @Override
  public ToolResponse mouseResponse(Point local, int modifiers) {
    var action = decideAction(local, modifiers, local, modifiers);
    if( action == null )
      action = noopAction(local);
    boolean preview = shiftHeld(modifiers) || mouseHeld(modifiers);
    return new EditToolResponse(action, preview, true, modifiers);
  }

  @Override
  public MouseAction drag(Point p1, int mod1) {
    return (p2, mod2) -> {
      var action = decideAction(p1, mod1, p2, mod2);
      if( action == null )
        action = noopAction(p2);
      var enable = new AxisRect(mapView().visibleArea).contains(p2);
      return new EditToolResponse(action, true, enable, mod1);
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
        return new ProposedAction("Delete chain",
            new TrackHighlight(chain, DELETE_HIGHLIGHT), null, null, null);
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
        return new ProposedAction("Delete node",
            new TrackHighlight(chain, index-1, index+1, kind.rgb), null,
            null, newChain);
      }
    } else if( p1.dist(p2) < 3 ) {
      // This is probably just a plain click. If you want to actually move
      // a node this little, zoom in first!
      return new ProposedAction("!Don't move node",
          new TrackHighlight(editingChain(), index, 0xFFFFFF), null,
          null, editingChain());
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
    return new ProposedAction("Move node", null, n2, null, newChain);
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
        return new ProposedAction("Delete chain", highlight, null,
            null, null);
      } else if( index == 0 ) {
        return new ProposedAction("Delete segment", highlight, null,
            null, chain.subchain(1,chain.numSegments));
      } else if( index == chain.numSegments-1 ) {
        return new ProposedAction("Delete segment", highlight, null,
            null, chain.subchain(0, index));
      } else {
        var front = chain.subchain(0, index);
        var back = chain.subchain(index+1, chain.numSegments);
        var allChains = activeFileContent().chainsCopy();
        allChains.remove(chain);
        allChains.add(front);
        allChains.add(back);
        var localNodes = chain.localize(translator()).nodes;
        var frontDist = localNodes.get(index).dist(p2);
        var backDist = localNodes.get(index+1).dist(p2);
        return new ProposedAction("Delete segment", highlight, null,
            allChains, frontDist < backDist ? front : back);
      }
    } else if( chain.kinds.get(index) == kind ) {
      return actionInFreeSpace(p1, mod1, p2, mod2);
    } else {
      var kinds = new ArrayList<>(chain.kinds);
      kinds.set(index, kind);
      var newChain = new SegmentChain(chain.nodes, kinds, chainClass);
      return new ProposedAction("Change to @",
          new TrackHighlight(chain, index, index+1, kind.rgb), null,
          null, newChain);
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

    var fileContent = activeFileContent().chainsCopy();
    fileContent.remove(a);
    fileContent.remove(b);
    fileContent.add(joined);

    TrackHighlight highlight =
        new TrackHighlight(joined, a.numSegments, a.numSegments+1, kind.rgb);

    return new ProposedAction("Join segment chains", highlight, null,
        fileContent, joined);
  }

  protected ProposedAction actionFromOtherSegment(ChainRef<?> which,
      Point p1, int mod1, Point p2, int mod2) {
    return selectEditingChain(which, p2);
  }

  protected ProposedAction selectEditingChain(ChainRef<?> which, Point p2) {
    var theChain = which.chain();
    return new ProposedAction("Select active chain",
        new TrackHighlight(theChain, 0xEEEE99), local2node(p2),
        activeFileContent().chainsCopy(), theChain);
  }

  protected ProposedAction actionWithNoEditingChain(
      Point p1, int mod1, Point p2, int mod2) {
    if( p1.dist(p2) < 5 ) {
      TrackNode n = local2node(p1);
      return new ProposedAction("Create new node", null, n,
          null, singletonChain(n));
    } else {
      TrackNode n1 = local2node(p1);
      TrackNode n2 = local2node(p2);
      var chain = new SegmentChain(Arrays.asList(n1, n2), kindx1);
      return new ProposedAction("Draw new @", null, n2, null, chain);
    }
  }

  protected ProposedAction actionInFreeSpace(
      Point p1, int mod1, Point p2, int mod2) {
    SegmentChain chain = editingChain();
    if( chain.numNodes == 1 ) {
      TrackNode n1 = chain.nodes.get(0);
      TrackNode n2 = local2node(p2);
      var newChain = new SegmentChain(Arrays.asList(n1, n2), kindx1);
      return new ProposedAction("Draw new @", null, n2, null, newChain);
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
            TreeList.concat(singletonList(n), chain.nodes),
            TreeList.concat(kindx1, chain.kinds));
        return new ProposedAction("Append @", null, n, null, newChain);
      } else {
        insertBefore = false;
      }
    } else if( i == chain.numNodes-1 ) {
      if( shouldAppend(gp1, chain.nodes.get(i), chain.nodes.get(i-1)) ) {
        var newChain = new SegmentChain(
            TreeList.concat(chain.nodes, singletonList(n)),
            TreeList.concat(chain.kinds, kindx1));
        return new ProposedAction("Append @", null, n, null, newChain);
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
    return new ProposedAction("Insert @", null, n, null, newChain);
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
    return new ProposedAction("!Do nothing", null, local2node(p2),
        null, editingChain());
  }

  private void ensureAppropriateEditingChain() {
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
    return new SegmentChain(
        Collections.singletonList(theNode), Collections.emptyList(),
        chainClass);
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

  class EditToolResponse implements ToolResponse {
    final boolean enableExecute;
    final ProposedAction action;
    final VisibleTrackData tracks;
    final VectorOverlay cursor;

    EditToolResponse(ProposedAction action,
        boolean preview, boolean enable, int mod1) {
      this.enableExecute = enable;
      this.action = action;
      if( preview ) {
        tracks = mapView().currentVisible.clone();
        if( action.fileContent != null ) {
          tracks.setCurrentChains(action.fileContent);
        } else {
          tracks.removeCurrentChain(editingChain());
        }
        tracks.setEditingChain(action.editingChain);
        tracks.freeze();
        cursor = circleCursor(mapView().mouseLocal, action.newGlobal, mod1);
      } else if( action.highlight != null ) {
        tracks = mapView().currentVisible.clone();
        tracks.setHighlight(action.highlight());
        tracks.freeze();
        cursor = null;
      } else {
        tracks = null;
        cursor = circleCursor(mapView().mouseLocal, action.newGlobal, mod1);
      }
    }

    @Override
    public VectorOverlay previewOverlay() {
      return cursor;
    }
    @Override
    public VisibleTrackData previewTrackData() {
      return tracks;
    }
    @Override
    public void execute(ExecuteWhy why) {
      if( !enableExecute ) {
        SwingUtils.beep();
        return;
      }
      if( action.fileContent != null ||
          !activeFileContent().contains(action.editingChain) ) {
        Set<SegmentChain> chains = action.fileContent;
        if( chains == null ) {
          chains = activeFileContent().chainsCopy();
          chains.remove(editingChain());
          if( action.editingChain != null &&
              action.editingChain.numNodes > 1 )
            chains.add(action.editingChain);
        }
        FileContent newContent = activeFileContent().withChains(chains);
        String undoDesc = action.undoDesc.replace("@", kindDescription);
        System.err.println("  Executing edit: "+undoDesc);
        owner.files.activeFile().rewriteContent(
            mapView().undoList, undoDesc, c->newContent);
        mapView().selectEditingTool();
      }
      mapView().setEditingChain(action.editingChain);
    }
  }

  private CircleOverlay circleCursor(Point local, Point global, int mod1) {
    int rgb = ctrlHeld(mod1) ? DELETE_HIGHLIGHT : kind.rgb;
    if( global instanceof TrackNode node ) {
      int circleDiameter = diameterAt(node);
      if( circleDiameter > 15) {
        return new CircleOverlay(rgb, circleDiameter,
            translator().global2localWithHint(node, local));
      }
    } else if( global != null ) {
      return new CircleOverlay(rgb, 10,
          translator().global2localWithHint(global, local));
    }
    return new CircleOverlay(rgb, 10, Point.at(local.x+10, local.y+10));
  }

  private int cachedDiameter;
  private double cachedDiameterScale;
  private double cachedDiameterMinY;
  private double cachedDiameterMaxY;

  private int diameterAt(Point global) {
    double scale = owner.mapView.projection.scaleAcross();
    if( scale != cachedDiameterScale ||
        global.y < cachedDiameterMinY ||
        global.y > cachedDiameterMaxY ) {
      double unitsPerMeter = WebMercator.unitsPerMeter(global.y);
      cachedDiameter = (int)Math.round(1.435 * unitsPerMeter  / scale);
      // a rough estimate of the distance in global coordinates it
      // takes until the cached value is one pixel off
      double validity = Coords.EARTH_SIZE / (2 * Math.PI) / cachedDiameter;
      cachedDiameterScale = scale;
      cachedDiameterMinY = global.y - validity;
      cachedDiameterMaxY = global.y + validity;
    }
    return cachedDiameter;
  }

}
