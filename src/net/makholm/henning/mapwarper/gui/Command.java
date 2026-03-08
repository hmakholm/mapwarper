package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.gui.hairy.CommandCompanion;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.util.BadError;

public abstract class Command {

  public final Commands owner;
  public final CommandCompanion hairy;
  public final MapView mapView;
  public final String codename;
  public final String niceName;

  public Command(Commands owner, String codename, String niceName) {
    this.owner = owner;
    this.mapView = owner.mapView;
    this.codename = codename;
    this.niceName = niceName != null ? niceName : "("+codename+")";

    if( owner.commandRegistry.containsKey(codename) )
      throw BadError.of("Command '%s' seems to be registered twice.", codename);
    owner.commandRegistry.put(codename, this);

    this.hairy = owner.window.createCompanion(this);
  }

  protected final void beep() {
    owner.window.beep();
  }

  public final MapView mapView() {
    return owner.mapView;
  }

  protected final ProjectionWorker translator() {
    return mapView.translator();
  }

  protected final SegmentChain editingChain() {
    return mapView.editingChain;
  }

  protected final FileContent activeFileContent() {
    return owner.files.activeFile().content();
  }

  public boolean makesSenseNow() {
    return true;
  }

  public void invokeByKey() {
    invoke();
  }

  public abstract void invoke();

  /**
   * @return true if we need to refresh the displayed situation
   */
  public boolean invocationKeyReleased(boolean anythingDone, int modifiers) {
    return false;
  }

  public void debugTraceInvoke() {
    System.err.println("["+codename+"]");
  }

  public Boolean getMenuSelected() {
    // Returning null means this never shows a checkmark
    return null;
  }

  public String overrideMenuItemText() {
    // Returning null means to use the standard nicename.
    return null;
  }

}
