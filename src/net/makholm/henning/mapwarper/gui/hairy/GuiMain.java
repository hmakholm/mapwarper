package net.makholm.henning.mapwarper.gui.hairy;

import java.nio.file.Path;

import net.makholm.henning.mapwarper.gui.Command;
import net.makholm.henning.mapwarper.gui.Commands;
import net.makholm.henning.mapwarper.gui.MapView;
import net.makholm.henning.mapwarper.gui.files.FilePane;

public interface GuiMain {

  public MapViewCompanion createCompanion(MapView logic);
  public FilePaneCompanion createCompanion(FilePane logic);
  public CommandCompanion createCompanion(Command logic);

  public Commands commands();

  public boolean filePaneVisible();
  public void setFilePaneVisible(boolean state);

  public boolean tilesetPaneVisible();
  public void setTilesetPaneVisible(boolean state);
  public void updateTilesetPane();

  public boolean toolbarVisible();
  public void setToolbarVisible(boolean state);
  public void repaintToolbar(Command cmd);

  public void beep();
  public void showWarningBox(String title, String fmt, Object... args);
  public void showErrorBox(String fmt, Object... args);
  public boolean showYesCancelBox(String title, String fmt, Object... args);
  public Boolean showYesNoCancelBox(String title, String fmt, Object... args);

  public Path showOpenDialog(String extension, String typetext);
  public Path showSaveDialog(String title, String extension, String typetext);

  public void quitCommand();

}
