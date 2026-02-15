
package net.makholm.henning.mapwarper.gui.projection;

import java.nio.file.Path;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.util.BadError;

public abstract class BaseProjection extends Projection {

  public boolean isOrtho() {
    return false;
  }

  public boolean isWarp() {
    return false;
  }

  @Override public BaseProjection base() { return this; }
  @Override public final Point local2projected(Point p) { return p; }
  @Override public final Point projected2local(Point p) { return p; }
  @Override public final double scaleAcross() { return 1.0; }
  @Override public final double scaleAlong() { return 1.0; }

  @Override
  public AxisRect maxUnzoom() {
    // As a default implementation for warped and warp-like projections,
    // let's say the unzoom limit is 5 million unit, corresponding to between
    // 90 and 180 km on a screen. (The secret here is that this doesn't
    // really limit how _where_ in the coordinate system one can scroll to,
    // so it doesn't need to reflect how long the warp (if any) actually is).
    return new AxisRect(Point.ORIGIN, Point.at(5e6, 5e6));
  }

  @Override
  public Affinoid getAffinoid() {
    // ortho and circlewarp override this
    return new Affinoid();
  }

  public abstract Projection apply(Affinoid aff);

  protected ProjectionWorker createWorker(Projection owningProjection,
      double xscale, double yscale) {
    throw BadError.of("%s cannot make non-affine workers",
        this.getClass().getTypeName());
  }

  @Override protected final ProjectionWorker createNonAffineWorker() {
    return createWorker(this, 1.0, 1.0);
  }

  /**
   * Return true for projections that have a high risk of downloading many
   * not really wanted tiles.
   */
  public abstract boolean suppressMainTileDownload(double squeeze);

  @Override
  public final RenderFactory makeRenderFactory(LayerSpec spec) {
    return makeRenderFactory(spec, 1.0, 1.0);
  }

  protected abstract RenderFactory makeRenderFactory(LayerSpec spec,
      double xpixsize, double ypixsize);

  public abstract Projection makeQuickwarp(Point pos, boolean circle,
      Affinoid aff);

  abstract public String describe(Path currentFile);

  @Override
  public String toString() {
    return describe(null);
  }

}
