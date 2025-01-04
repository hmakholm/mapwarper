package net.makholm.henning.mapwarper.gui.maprender;

import net.makholm.henning.mapwarper.util.AbortRendering;

public interface RenderWorker {

  void doSomeWork() throws AbortRendering;

  /**
   * Higher numbers are earlier rendering phases. The numbering only needs
   * to be consistent within a projection, because everything that is
   * rendering at the same time will be from the same projection.
   *
   * Return 0 if rendering is waiting for external data and
   * {@link RenderContext#pokeSchedulerAsync()} will be called eventually.
   *
   * Return -1 when rendering is so complete that it won't get any
   * <em>more</em> complete.
   */
  int priority();

  void dispose();

}
