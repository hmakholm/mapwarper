package net.makholm.henning.mapwarper.gui.swing;

import net.makholm.henning.mapwarper.gui.Commands;

public abstract class ToggleCommand extends Command {

  public ToggleCommand(Commands owner, String codename, String niceName) {
    super(owner, codename, niceName);
  }

  public abstract boolean getCurrentState();

  public abstract void setNewState(boolean b);

  public boolean dismissPopupMenuImmediately() {
    return false;
  }

  @Override
  void debugTraceInvoke() {}

  @Override
  public Boolean getMenuSelected() {
    return getCurrentState();
  }

  @Override
  public final void invoke() {
    boolean newState = !getCurrentState();
    System.err.println("["+codename+"="+newState+"]");
    setNewState(newState);
  }

}
