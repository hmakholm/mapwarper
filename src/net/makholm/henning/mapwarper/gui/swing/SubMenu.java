package net.makholm.henning.mapwarper.gui.swing;

import javax.swing.JMenu;

import net.makholm.henning.mapwarper.gui.IMenu;

@SuppressWarnings("serial")
class SubMenu extends JMenu implements IMenu {

  SubMenu(String name) {
    super(name);
  }

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
