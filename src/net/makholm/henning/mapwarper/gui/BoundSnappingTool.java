package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
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

    TrackNode created = super.local2node(local);

    if( Toggles.TILEGRID.setIn(mapView().toggleState) ) {
      // When editing with tiles shown, we probably want to align to the
      // tile corners, so snap to those!
      int snapx = gridsnap(Coords.x(created.pos));
      int snapy = gridsnap(Coords.y(created.pos));
      TrackNode corner = new TrackNode(snapx, snapy);
      Point localCorner = translator().global2localWithHint(corner, local);
      if( localCorner.dist(local) < SNAP_DISTANCE ) {
        return corner;
      }
    }

    return created;
  }

  private static final int TILESIZE = (Coords.EARTH_SIZE >> 18);

  private int gridsnap(int coord) {
    return (coord + TILESIZE/2) & -TILESIZE;
  }

}
