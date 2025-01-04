package net.makholm.henning.mapwarper.gui.maprender;

public interface RenderFactory {

  public RenderWorker makeWorker(RenderTarget target);

}
