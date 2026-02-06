package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;
import java.nio.file.Path;

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

  public QuickWarp(UnitVector direction) {
    this(Point.ORIGIN, direction);
  }

  public QuickWarp(Point origin, UnitVector direction) {
    this.origin = origin;
    this.direction = direction;
    this.normal = direction.turnRight();
  }

  @Override
  public Projection apply(Affinoid aff) {
    if( aff.squeeze == 1 ) {
      if( direction.y == 0 )
        return applyOrtho(aff, direction.x > 0 ? 0 : 2);
      if( direction.x == 0 )
        return applyOrtho(aff, direction.y > 0 ? 1 : 3);
    }
    aff.assertSqueezable();
    return aff.apply(this);
  }

  private Projection applyOrtho(Affinoid aff, int ourTurn) {
    aff.squeezable = false;
    aff.quadrantsTurned += ourTurn;
    return aff.apply(OrthoProjection.ORTHO);
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
      fallback.attemptFallbacks(3);
      fallbackChain = fallback.getChain();
    }

    var recipe = SupersamplingRenderer.prepareSupersampler(spec,
        xscale, yscale, supersamplingChain, fallbackChain);
    return target
        -> new SupersamplingRenderer(spec, xscale, yscale, target, recipe) {
          @Override
          protected PointWithNormal locateColumn(double x, double y) {
            return new PointWithNormal(
                origin.plus(x, direction).plus(y, normal), normal);
          }
        };
  }

  @Override
  public Projection makeQuickwarp(Point local, boolean circle, Affinoid aff) {
    return this.apply(aff);
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

  @Override
  public String describe(Path currentFile) {
    return "quickwarp";
  }

  @Override
  public String toString() {
    return "quickwarp "+direction.bearingString()+"\u00B0";
  }

}
