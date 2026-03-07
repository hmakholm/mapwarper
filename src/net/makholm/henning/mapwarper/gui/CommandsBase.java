package net.makholm.henning.mapwarper.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import net.makholm.henning.mapwarper.gui.files.FilePane;
import net.makholm.henning.mapwarper.gui.hairy.GuiMain;
import net.makholm.henning.mapwarper.gui.swing.Command;
import net.makholm.henning.mapwarper.gui.swing.SwingMapView;

/**
 * This is a separate class just so its constructor gets to run before
 * the instance initializers in {@link Command} and everything can see
 * its final fields from the beginning.
 */
public class CommandsBase {

  public final GuiMain window;
  public final MapView mapView;
  public final SwingMapView hairy;
  public final FilePane files;

  public final Map<String, Command> commandRegistry = new LinkedHashMap<>();

  CommandsBase(MapView mapView) {
    this.window = mapView.window;
    this.mapView = mapView;
    this.hairy = mapView.hairy;
    this.files = mapView.files;
  }

}
