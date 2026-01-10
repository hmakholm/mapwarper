package net.makholm.henning.mapwarper.gui.projection;

import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.gui.maprender.SupersamplingRenderer;

class BaseWarpRenderer extends SupersamplingRenderer {

  protected final MinimalWarpWorker worker;

  protected BaseWarpRenderer(WarpedProjection warp,
      LayerSpec spec, double xpixsize, double ypixsize, RenderTarget target,
      SupersamplingRecipe supersample) {
    super(spec, xpixsize, ypixsize, target, supersample);
    this.worker = new MinimalWarpWorker(warp);
  }

  @Override
  protected PointWithNormal locateColumn(double x, double y) {
    worker.setLefting(x);
    return worker.pointWithNormal(worker.projected2downing(y));
  }

}
