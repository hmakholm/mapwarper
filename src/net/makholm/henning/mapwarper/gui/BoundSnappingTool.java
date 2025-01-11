package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.TrackNode;

class BoundSnappingTool extends EditTool {

  protected BoundSnappingTool(Commands owner, SegKind kind, String desc) {
    super(owner, kind, desc);
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
      return found.chain().nodes.get(found.index());
    }

    found = FindClosest.point(
        mapView().currentVisible.otherBoundNodeTree.apply(translator()),
        ChainRef::data, SNAP_DISTANCE, local);
    if( found != null )
      return found.chain().nodes.get(found.index());

    return super.local2node(local);
  }

}
