package net.makholm.henning.mapwarper.gui.hairy;

import java.nio.file.Path;

import net.makholm.henning.mapwarper.gui.Commands;
import net.makholm.henning.mapwarper.gui.swing.Command;

public interface GuiMain {

  public Commands commands();

  public boolean filePaneVisible();
  public void setFilePaneVisible(boolean state);

  public boolean tilesetPaneVisible();
  public void setTilesetPaneVisible(boolean state);
  public void updateTilesetPane();

  public boolean toolbarVisible();
  public void setToolbarVisible(boolean state);
  public void repaintToolbar(Command cmd);

  public void showWarningBox(String title, String fmt, Object... args);
  public void showErrorBox(String fmt, Object... args);
  public boolean showYesCancelBox(String title, String fmt, Object... args);
  public Boolean showYesNoCancelBox(String title, String fmt, Object... args);

  public Path showOpenDialog(String extension, String typetext);
  public Path showSaveDialog(String title, String extension, String typetext);

  public void quitCommand();

}
