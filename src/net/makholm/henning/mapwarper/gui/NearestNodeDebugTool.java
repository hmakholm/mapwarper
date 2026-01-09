package net.makholm.henning.mapwarper.gui;

import java.util.List;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.overlays.ArrowOverlay;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.track.VisibleTrackData;
import net.makholm.henning.mapwarper.util.XyTree;

public class NearestNodeDebugTool extends Tool {

  protected NearestNodeDebugTool(Commands owner) {
    super(owner, "nearestNode", "Show nearest node");
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    ToolResponse segresponse = closestSegmentResponse(pos, modifiers);
    if( segresponse != NO_RESPONSE ) return segresponse;

    Point global = translator().local2global(pos);
    ChainRef<TrackNode> found = FindClosest.point(
        owner.files.activeFile().content().nodeTree.get(),
        ChainRef::data,
        Double.POSITIVE_INFINITY,
        global);
    if( found == null )
      return NO_RESPONSE;
    Point foundLocal = translator().global2local(found.data());
    var overlay = new ArrowOverlay(foundLocal.to(pos), 0);
    var underlay = mapView().currentVisible.clone();
    underlay.setHighlight(new TrackHighlight(found.chain(),
        found.index(), found.index(), 0xEEEE99));
    underlay.freeze();
    return new ToolResponse() {
      @Override
      public VisibleTrackData previewTrackData() { return underlay; }
      @Override
      public VectorOverlay previewOverlay() { return overlay; }
      @Override
      public void execute(ExecuteWhy why) {}
    };
  }

  private ToolResponse closestSegmentResponse(Point pos, int modifiers) {
    SegmentChain chain = mapView().editingChain;
    if( chain == null )
      return NO_RESPONSE;
    XyTree<List<ChainRef<Bezier>>> tree =
        chain.localize(mapView().translator()).segmentTree.get();
    ChainRef<Bezier> found = FindClosest.curve(
        tree,
        ChainRef::data,
        5,
        pos,
        1);
    if( found == null )
      return NO_RESPONSE;
    var underlay = mapView().currentVisible.clone();
    underlay.setHighlight(TrackHighlight.segment(found, 0xEEEE99));
    underlay.freeze();
    return new ToolResponse() {
      @Override
      public VisibleTrackData previewTrackData() { return underlay; }
      @Override
      public void execute(ExecuteWhy why) {}
    };
  }

}
