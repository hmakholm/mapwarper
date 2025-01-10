package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.maprender.FallbackChain;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.gui.maprender.SupersamplingRenderer;

public class CircleWarp extends BaseProjection {

  public final Point center, focus;
  final double radius;
  final double degxscale;
  final double bearing0;

  public CircleWarp(Point center, Point focus) {
    this.center = center;
    this.focus = focus;
    this.radius = center.dist(focus);
    this.degxscale = (180/Math.PI) / radius;
    this.bearing0 = center.to(focus).bearing();
  }

  @Override
  public boolean isQuickwarp() {
    return true;
  }

  @Override
  public AxisRect maxUnzoom() {
    return new AxisRect(Point.at(-radius*4, 0),
        Point.at(radius*4, radius+1_000_000));
  }

  @Override
  public AffineTransform createAffine() {
    return null;
  }

  private PointWithNormal local2global(double x, double y) {
    var down = UnitVector.withBearing(bearing0 - x*degxscale);
    return new PointWithNormal(center.plus(y, down), down);
  }

  @Override
  public ProjectionWorker createWorker(Projection owningProjection,
      double xscale, double yscale) {
    return new ProjectionWorker() {
      @Override
      public Projection projection() { return owningProjection; }

      @Override
      public PointWithNormal local2global(Point p) {
        return CircleWarp.this.local2global(p.x*xscale, p.y*yscale);
      }

      @Override
      public Point global2localWithHint(Point global, Point nearbyLocal) {
        return global2local(global);
      }

      @Override
      public Point global2local(Point p) {
        LineSeg v = p.minus(center);
        double degrees = bearing0 - v.bearing();
        while( degrees < -180 ) degrees += 360;
        while( degrees > 180 ) degrees -= 360;
        return Point.at(degrees/(degxscale*xscale), v.length()/yscale);
      }

      @Override
      public List<Bezier> global2local(Bezier global) {
        return List.of(Bezier.line(global2local(global.p1),
            global2local(global.p4)));
      }
    };
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
            return local2global(x, y);
          }
        };
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this ||
        (obj instanceof CircleWarp o &&
            o.center.is(center) &&
            o.focus.is(focus));
  }

  @Override
  protected long longHashImpl() {
    long hash = 202501100230L;
    hash ^= Double.doubleToRawLongBits(center.x);
    hash = hashStep(hash);
    hash ^= Double.doubleToRawLongBits(center.y);
    hash = hashStep(hash);
    hash ^= Double.doubleToRawLongBits(focus.x);
    hash = hashStep(hash);
    hash ^= Double.doubleToRawLongBits(focus.y);
    return hash;
  }

}
