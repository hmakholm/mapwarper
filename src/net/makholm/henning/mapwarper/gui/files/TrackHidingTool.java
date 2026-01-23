package net.makholm.henning.mapwarper.gui.files;

import net.makholm.henning.mapwarper.gui.Commands;
import net.makholm.henning.mapwarper.gui.swing.Tool;

/**
 * This is for tools that tend to make the current file content invisible;
 * such tools will therefore forcibly deselect themself when the active
 * file changes.
 */
public abstract class TrackHidingTool extends Tool {

  protected TrackHidingTool(Commands owner, String codename, String niceName) {
    super(owner, codename, niceName);
  }

  @Override
  public void activeFileChanged() {
    if( mapView().currentTool != this ) {
      // Good!
    } else if( !(previousTool instanceof TrackHidingTool) &&
        switchToPreviousTool() ) {
      // Good
    } else if( editingChain() != null ) {
      if( editingChain().isTrack() )
        mapView().selectTool(owner.trackTool);
      else
        mapView().selectTool(owner.boundTool);
    } else {
      var content = owner.files.activeFile().content();
      if( content.numTrackChains != 0 )
        mapView().selectTool(owner.trackTool);
      else if( content.numBoundChains != 0 )
        mapView().selectTool(owner.boundTool);
      else
        mapView().selectTool(owner.move);
    }
  }

}
