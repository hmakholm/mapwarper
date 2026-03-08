package net.makholm.henning.mapwarper.gui.hairy;

import net.makholm.henning.mapwarper.gui.Command;

public interface IMenu {

  public void add(Command command);

  public IMenu addSubmenu(String name);

  public void addSeparator();

}
