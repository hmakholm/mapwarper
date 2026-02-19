package net.makholm.henning.mapwarper.gui.maprender;

import net.makholm.henning.mapwarper.util.AbortRendering;

public interface RenderTarget {

  long left();
  long top();
  int columns();
  int rows();

  /**
   * This should return true if it is important to get <em>something</em>
   * shown quickly, even at the expense of lower quality that needs
   * additional render passes later.
   */
  boolean isUrgent();

  void checkCanceled() throws AbortRendering;

  void givePixel(int x, int y, int rgb);

  void isNowGrownUp();

  void pokeSchedulerAsync();
}
