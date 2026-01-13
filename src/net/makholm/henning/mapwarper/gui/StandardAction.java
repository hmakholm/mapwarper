package net.makholm.henning.mapwarper.gui;

import java.awt.Cursor;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.MouseAction.ExecuteWhy;
import net.makholm.henning.mapwarper.gui.MouseAction.ToolResponse;
import net.makholm.henning.mapwarper.gui.overlays.CircleOverlay;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.track.VisibleTrackData;

class StandardAction implements ProposedAction {

  interface Context {
    final int NO_CURSOR = 0xDEADBEEF;

    MapView mapView();
    default String finalizeUndoDesc(String template) { return template; }
    default boolean isSnappedNode(TrackNode node) { return false; }
    default int circleCursorColor() { return NO_CURSOR; }
  }

  /**
   * This action doesn't do anything, but may have vector feedback added
   * to it.
   */
  static StandardAction noop(Context cxt, String undoDesc) {
    return new StandardAction(cxt, undoDesc,
        null, cxt.mapView().editingChain);
  }

  /**
   * A fairly generic constructor that replaces the editing chain with
   * zero or more new chains. The caller must know which of them, if
   * any, is to be active.
   */
  static StandardAction common(Context cxt, String undoDesc,
      List<SegmentChain> newChains, SegmentChain active) {
    var chains = cxt.mapView().files.activeFile().content().chainsCopy();
    if( !chains.remove(cxt.mapView().editingChain) ) {
      if( newChains.isEmpty() )
        return new StandardAction(cxt, undoDesc, null, active);
    }
    chains.addAll(newChains);
    return new StandardAction(cxt, undoDesc, chains, active);
  }

  static StandardAction simple(Context cxt, String undoDesc,
      SegmentChain chain) {
    return common(cxt, undoDesc, List.of(chain), chain);
  }

  /**
   * This action potentially splits a single chain into two.
   * The one closet to the given local point will end up active.
   */
  static StandardAction split(Context cxt, String undoDesc,
      SegmentChain a, Point local, SegmentChain b) {
    var trans = cxt.mapView().translator();
    var adist = trans.global2local(a.nodes.last()).sqDist(local);
    var bdist = trans.global2local(b.nodes.get(0)).sqDist(local);
    return common(cxt, undoDesc, List.of(a,b), adist < bdist ? a : b);
  }

  /**
   * This action makes a new chain, consuming zero or more old ones.
   *
   * It should be the only of the action types that can create a
   * trivial (one-node) chain.
   */
  static StandardAction join(Context cxt, String undoDesc,
      Collection<SegmentChain> oldChains, SegmentChain newChain) {
    if( oldChains.isEmpty() && newChain.numNodes <= 1 )
      return new StandardAction(cxt, undoDesc, null, newChain);

    var chains = cxt.mapView().files.activeFile().content().chainsCopy();
    chains.removeAll(oldChains);
    if( newChain.numNodes > 1 )
      chains.add(newChain);
    return new StandardAction(cxt, undoDesc, chains, newChain);
  }

  static ProposedAction switchChain(MapView mapView, SegmentChain newChain) {
    return new ProposedAction() {
      @Override
      public boolean executeIfSelectingChain() {
        mapView.setEditingChain(newChain);
        return true;
      }
      @Override
      public ToolResponse freeze() {
        var tracks = mapView.currentVisible.clone();
        tracks.setHighlight(new TrackHighlight(newChain, 0xEEEE99));
        tracks.freeze();
        return new ToolResponse() {
          @Override
          public VisibleTrackData previewTrackData() {
            return tracks;
          }
          @Override
          public void execute(ExecuteWhy why) {
            executeIfSelectingChain();
          }
        };
      }

    };
  }

  StandardAction with(TrackHighlight highlight) {
    this.highlight = highlight;
    return this;
  }

  StandardAction with(VectorOverlay overlay) {
    this.overlay = overlay;
    return this;
  }

  StandardAction with(TrackNode toDisplay) {
    this.newNode = toDisplay;
    this.snapped = cxt.isSnappedNode(toDisplay);
    return this;
  }

  StandardAction with(TrackNode toDisplay, boolean snapped) {
    this.newNode = toDisplay;
    this.snapped = snapped;
    return this;
  }

  @Override
  public StandardAction withPreview() {
    this.preview = true;
    return this;
  }

  // -------------------------------------------------------------------------

  final Context cxt;
  final MapView mapView;
  final String undoDesc;

  final Set<SegmentChain> newFileContent;
  final SegmentChain newActiveChain;
  TrackHighlight highlight;
  VectorOverlay overlay;
  TrackNode newNode;
  boolean snapped;
  boolean preview;

  private StandardAction(Context cxt, String undoDesc,
      Set<SegmentChain> newFileContent, SegmentChain newActiveChain) {
    this.cxt = cxt;
    this.mapView = cxt.mapView();
    this.undoDesc = undoDesc;
    this.newFileContent = newFileContent;
    this.newActiveChain = newActiveChain;
  }

  @Override
  public ToolResponse freeze() {
    Cursor cursor = snapped ? Tool.loadCursor("snapCrosshairs.png") : null;
    if( overlay == null && (highlight == null || newNode != null) )
      overlay = makeCircleCursor();

    VisibleTrackData tracks;
    if( !preview && highlight == null ) {
      tracks = null;
    } else {
      tracks = mapView.currentVisible.clone();
      tracks.setHighlight(highlight);
      if( preview ) {
        if( newFileContent != null )
          tracks.setCurrentChains(newFileContent);
        tracks.setEditingChain(newActiveChain);
      }
      tracks.freeze();
    }

    return new ToolResponse() {
      @Override
      public VectorOverlay previewOverlay() {
        return overlay;
      }
      @Override
      public VisibleTrackData previewTrackData() {
        return tracks;
      }
      @Override
      public Cursor cursor() {
        return cursor;
      }
      @Override
      public void execute(ExecuteWhy why) {
        var currentFile = mapView.files.activeFile();
        if( newFileContent != null ) {
          String desc = cxt.finalizeUndoDesc(undoDesc);
          System.err.println("  Executing edit: "+desc);
          currentFile.rewriteContent(mapView.undoList, desc,
              c -> c.withChains(newFileContent));
        }
        mapView.setEditingChain(newActiveChain);
      }
    };
  }

  private VectorOverlay makeCircleCursor() {
    int rgb = cxt.circleCursorColor();
    if( rgb == Context.NO_CURSOR ) return null;
    Point local = mapView.mouseLocal;
    if( newNode != null ) {
      int diameter = mapView.gaugeInPixels(newNode);
      if( snapped && diameter < 10 ) diameter = 10;
      if( diameter > 15 || snapped ) {
        return new CircleOverlay(rgb, diameter,
            mapView.translator().global2localWithHint(newNode, local));
      }
    }
    return new CircleOverlay(rgb, 10, Point.at(local.x+10, local.y+10));
  }

}

