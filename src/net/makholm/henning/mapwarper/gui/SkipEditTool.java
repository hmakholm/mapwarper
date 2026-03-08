package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.gui.projection.WarpedProjection;
import net.makholm.henning.mapwarper.track.SegKind;

class SkipEditTool extends BoundEditTool {

  protected SkipEditTool(Commands owner, SegKind kind) {
    super(owner, kind);
  }

  @Override
  public void whenSelected() {
    super.whenSelected();
    if( kind == SegKind.PASS ) {
      // Unconditionally deselect the editing chain; this kind of lines is
      // generally not useful in chains.
      mapView().setEditingChain(null);
    }
  }

  @Override
  public void enterAction() {
    mapView().refreshWarp(true);
  }

  public static class ToggleState extends ToggleCommand {

    public ToggleState(Commands owner) {
      super(owner, "toggleFastforward", "Respect fast-forward sections");
    }

    @Override
    public boolean makesSenseNow() {
      return mapView().projection.base() instanceof WarpedProjection;
    }

    @Override
    public boolean getCurrentState() {
      return mapView().projection.getAffinoid().useSkips;
    }

    @Override
    public void setNewState(boolean b) {
      mapView().modifyAffinoid(aff -> aff.useSkips = b);
    }

  }

}
