package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.track.ChainRef;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.TrackNode;

class BoundEditTool extends EditTool {

  protected BoundEditTool(Commands owner) {
    super(owner, SegKind.BOUND, "bound line");
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
    if( found != null && found.chain().chainClass == SegKind.BOUND ) {
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
