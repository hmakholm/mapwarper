package net.makholm.henning.mapwarper.gui.maprender;

import java.awt.image.renderable.RenderContext;

import net.makholm.henning.mapwarper.util.AbortRendering;

/**
 * {@link RenderWorker}s are not thread safe; the render queue will be
 * responsible for sequencing calls to <em>all</em> the methods declared
 * here in case we render in multiple threads.
 */
public interface RenderWorker {

  /**
   * This should be called with no locks held!
   */
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

  /**
   * Usually this is responsible for cancelling download requests for
   * tiles this render worker would need.
   *
   * No work may be done after this has been called.
   *
   * This should be called with no locks held!
   */
  void dispose();

}
