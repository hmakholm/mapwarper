package net.makholm.henning.mapwarper.gui.maprender;

import net.makholm.henning.mapwarper.util.AbortRendering;

public interface RenderTarget {

  long left();
  long top();
  int columns();
  int rows();

  boolean isUrgent();

  void checkCanceled() throws AbortRendering;

  void givePixel(int x, int y, int rgb);

  void isNowGrownUp();

  void pokeSchedulerAsync();
}
