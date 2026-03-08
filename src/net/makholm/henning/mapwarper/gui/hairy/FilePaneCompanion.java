package net.makholm.henning.mapwarper.gui.hairy;

import net.makholm.henning.mapwarper.gui.files.FilePane;

/**
 * This object is the framework-specific part of {@link FilePane}.
 * There's an 1-to-1 correspondence between instances of the two
 * types.
 */
public interface FilePaneCompanion {

  public void refreshScene(FilePane.Entry[] newView);

  public boolean altHeld(int modifiers);

}
