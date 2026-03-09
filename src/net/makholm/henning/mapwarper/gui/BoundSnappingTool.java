package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.TrackNode;

class BoundSnappingTool extends EditTool {

  protected BoundSnappingTool(Commands owner, SegKind kind) {
    super(owner, kind);
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
    if( found != null && found.chain().isBound() ) {
      return lastSnapped = found.chain().nodes.get(found.index());
    }

    found = FindClosest.point(
        mapView().currentVisible.otherBoundNodeTree.apply(translator()),
        ChainRef::data, SNAP_DISTANCE, local);
    if( found != null )
      return lastSnapped = found.chain().nodes.get(found.index());

    TrackNode created = super.local2node(local);

    int tilegridZoom = mapView.dynamicMapLayerSpec.tilegridZoom();
    if( tilegridZoom > 0 ) {
      // When editing with tiles shown, we probably want to align to the
      // tile corners, so snap to those!
      int mask = -(Coords.EARTH_SIZE >> tilegridZoom);
      int half = Coords.EARTH_SIZE >> (tilegridZoom+1);
      int snapx = (Coords.x(created.pos) + half) & mask;
      int snapy = (Coords.y(created.pos) + half) & mask;
      TrackNode corner = new TrackNode(snapx, snapy);
      Point localCorner = translator().global2localWithHint(corner, local);
      if( localCorner.dist(local) < SNAP_DISTANCE ) {
        lastSnapped = corner;
        return lastSnapped = corner;
      }
    }

    return created;
  }

  private TrackNode lastSnapped;

  @Override
  public boolean isSnappedNode(TrackNode node) {
    return node == lastSnapped;
  }

  @Override
  public void enterAction() {
    if( editingChain() == null ) return;
    for( var kind : editingChain().kinds ) {
      if( kind == SegKind.PASS || kind == SegKind.SKIP ) {
        mapView().refreshWarp(true);
        return;
      }
    }
  }

}
