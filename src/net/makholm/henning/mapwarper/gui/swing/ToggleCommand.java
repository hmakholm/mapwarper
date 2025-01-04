package net.makholm.henning.mapwarper.gui.swing;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;

import net.makholm.henning.mapwarper.gui.Commands;

public abstract class ToggleCommand extends Command {

  public ToggleCommand(Commands owner, String codename, String niceName) {
    super(owner, codename, niceName);
  }

  public abstract boolean getCurrentState();

  public abstract void setNewState(boolean b);

  protected boolean dismissPopupMenuImmediately() {
    return false;
  }

  @Override
  void debugTraceInvoke() {}

  @Override
  public final void invoke() {
    boolean newState = !getCurrentState();
    System.err.println("["+codename+"="+newState+"]");
    setNewState(newState);
  }

  @Override
  public JMenuItem makeMenuItem() {
    var item = new JCheckBoxMenuItem(getAction());
    if( !dismissPopupMenuImmediately() )
      item.putClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick",
          Boolean.TRUE);
    item.setSelected(getCurrentState());
    return item;
  }

}
