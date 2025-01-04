
package net.makholm.henning.mapwarper.gui.projection;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.MathUtil;

public abstract class BaseProjection extends Projection {

  public boolean isOrtho() {
    return false;
  }

  public boolean isQuickwarp() {
    return false;
  }

  public boolean isWarp() {
    return false;
  }

  @Override public final BaseProjection base() { return this; }
  @Override public final Point local2projected(Point p) { return p; }
  @Override public final Point projected2local(Point p) { return p; }
  @Override public final double scaleAcross() { return 1.0; }
  @Override public final double scaleAlong() { return 1.0; }
  @Override public final double getSqueeze() { return 1.0; }

  @Override
  public abstract AxisRect maxUnzoom();

  @Override
  public final Projection withScaleAndSqueeze(double scale, double squeeze) {
    scale = MathUtil.snapToPowerOf2(scale, 0.0001);
    squeeze = MathUtil.snapToInteger(squeeze, 0.01);
    if( scale == 1.0 && squeeze == 1.0 )
      return this;
    else
      return new ScaledProjection(this, scale, squeeze);
  }

  @Override
  public final Projection scaleAndSqueezeSimilarly(BaseProjection base) {
    return base;
  }

  protected ProjectionWorker createWorker(Projection owningProjection,
      double xscale, double yscale) {
    throw BadError.of("%s cannot make non-affine workers",
        this.getClass().getTypeName());
  }

  @Override protected final ProjectionWorker createNonAffineWorker() {
    return createWorker(this, 1.0, 1.0);
  }

  @Override
  public final RenderFactory makeRenderFactory(LayerSpec spec) {
    return makeRenderFactory(spec, 1.0, 1.0);
  }

  protected abstract RenderFactory makeRenderFactory(LayerSpec spec,
      double xpixsize, double ypixsize);

}
