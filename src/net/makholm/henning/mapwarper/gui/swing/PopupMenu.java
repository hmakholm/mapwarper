package net.makholm.henning.mapwarper.gui.swing;

import javax.swing.JPopupMenu;

import net.makholm.henning.mapwarper.gui.IMenu;

@SuppressWarnings("serial")
class PopupMenu extends JPopupMenu implements IMenu {

  @Override
  public void add(Command command) {
    add(command.makeMenuItem());
  }

  @Override
  public IMenu addSubmenu(String name) {
    var sub = new SubMenu(name);
    add(sub);
    return sub;
  }

}
