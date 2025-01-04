package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.maprender.FallbackChain;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.gui.maprender.SupersamplingRenderer;

public class QuickWarp extends BaseProjection {

  public final Point origin;
  public final UnitVector direction;
  private final UnitVector normal;

  public QuickWarp(Point origin, UnitVector direction) {
    this.origin = origin;
    this.direction = direction;
    this.normal = direction.turnRight();
  }

  @Override
  public boolean isQuickwarp() {
    return true;
  }

  @Override
  public AxisRect maxUnzoom() {
    // 5 million units is between 90 and 180 km, should be plenty
    // of space to explore the anonymous warp
    return new AxisRect(Point.at(-5_000_000, -5_000_000),
        Point.at(5_000_000, 5_000_000));
  }

  @Override
  public AffineTransform createAffine() {
    return new AffineTransform(
        direction.x, direction.y,
        -direction.y, direction.x,
        origin.x, origin.y);
  }

  @Override
  public RenderFactory makeRenderFactory(LayerSpec spec,
      double xscale, double yscale) {
    FallbackChain fallback = new FallbackChain(spec, xscale, yscale);
    long supersamplingChain, fallbackChain;
    if( Toggles.LENS_MAP.setIn(spec.flags()) ) {
      supersamplingChain = fallback.lensChain();
      fallbackChain = 0;
    } else {
      supersamplingChain = fallback.supersampleMain(false);
      fallback.attemptFallbacks(0);
      fallbackChain = fallback.getChain();
    }

    var supersamplingRecipe = SupersamplingRenderer.prepareSupersampler(spec,
        xscale, yscale, supersamplingChain);
    return target
        -> new SupersamplingRenderer(spec, xscale, yscale, target,
            supersamplingRecipe, fallbackChain) {
          @Override
          protected PointWithNormal locateColumn(double x, double y) {
            return new PointWithNormal(
                origin.plus(x, direction).plus(y, normal), normal);
          }
        };
  }

  @Override
  public boolean equals(Object o) {
    return o == this ||
        (o instanceof QuickWarp oaw &&
            oaw.origin.is(origin) &&
            oaw.direction.is(direction));
  }

  @Override
  protected long longHashImpl() {
    long hash = 202412150849L;
    hash ^= Double.doubleToLongBits(origin.x);
    hash = hashStep(hash);
    hash ^= Double.doubleToLongBits(origin.y);
    hash = hashStep(hash);
    hash ^= Double.doubleToLongBits(direction.x);
    hash = hashStep(hash);
    hash ^= Double.doubleToLongBits(direction.y);
    return hash;
  }

}
