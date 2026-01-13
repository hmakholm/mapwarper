package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.overlays.BoxOverlay;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;

final class DeleteTool extends GenericEditTool
implements DragSubchainSelector.Callback {

  DeleteTool(Commands owner) {
    super(owner, "delete", "Delete segments");
    toolCursor = loadCursor("crosshairCursor.png");
  }

  @Override
  public void sanitizeEditingStateWhenSelected() {
    if( editingChain() == null ) {
      var chains = mapView().files.activeFile().content().chainsCopy();
      if( chains.size() == 1 ) {
        mapView().setEditingChain(chains.iterator().next());
      }
    }
  }

  @Override
  public ToolResponse mouseResponse(Point local, int modifiers) {
    if( editingChain() == null ) {
      var found = pickSegmentAnyChain(local);
      if( found != null )
        return selectEditingChain(found).freeze();
    } else {
      var found = pickSegmentInActive(local);
      if( found != null ) {
        var action = draggedSubchain(found.chain(),
            found.index(), found.index()+1);
        if( shiftHeld(modifiers) )
          action = action.withPreview();
        return action.freeze();
      }
    }
    var box = new BoxOverlay(new AxisRect(local).grow(10), DELETE_HIGHLIGHT);
    return new WrappedToolResponse(Tool.NO_RESPONSE) {
      @Override public VectorOverlay previewOverlay() { return box; }
    };
  }

  @Override
  public MouseAction drag(Point p1, int mod1) {
    return new DragSubchainSelector(mapView(), p1, DELETE_HIGHLIGHT, this);
  }

  @Override
  public ProposedAction draggedSubchain(SegmentChain chain, int a, int b) {
    var highlight = new TrackHighlight(chain, a, b, DELETE_HIGHLIGHT);
    var last = chain.numNodes-1;
    if( a == 0 && b == last )
      return killTheChain("Delete chain").with(highlight);
    else if( a == b )
      return noopAction("Cannot cut single node")
          .with(new TrackHighlight(chain, a, b, 0xCCCCCC));
    else if( a == 0 )
      return rewriteTo("Truncate chain", chain.subchain(b, last)).with(highlight);
    else if( b == last )
      return rewriteTo("Truncate chain", chain.subchain(0, a)).with(highlight);
    else
      return StandardAction.split(this, "Delete "+(b-a)+" segments",
          chain.subchain(0, a), mapView().mouseLocal, chain.subchain(b,last))
          .with(highlight);
  }

}
