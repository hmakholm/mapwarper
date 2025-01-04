package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.gui.swing.Command;

public interface IMenu {

  public void add(Command command);

  public IMenu addSubmenu(String name);

  public void addSeparator();

}
