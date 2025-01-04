package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.maprender.FallbackChain;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.gui.maprender.SimpleRenderer;

public final class OrthoProjection extends BaseProjection {

  public static final OrthoProjection ORTHO = new OrthoProjection();

  private OrthoProjection() { }

  @Override
  public boolean isOrtho() {
    return true;
  }

  @Override
  public AxisRect maxUnzoom() {
    return new AxisRect(Point.ORIGIN,
        Point.at(Coords.EARTH_SIZE, Coords.EARTH_SIZE));
  }

  @Override
  public AffineTransform createAffine() {
    return new AffineTransform();
  }

  private static final UnitVector commonNormal =
      Vector.of(0, 1).normalize();

  @Override
  public RenderFactory makeRenderFactory(LayerSpec spec,
      double xpixsize, double ypixsize) {
    long chain;
    FallbackChain fallback = new FallbackChain(spec, xpixsize, ypixsize);
    if( Toggles.LENS_MAP.setIn(spec.flags()) ) {
      chain = fallback.lensChain();
    } else {
      fallback.attemptMain(true);
      fallback.attemptFallbacks(2);
      chain = fallback.getChain();
    }
    return target
        -> new SimpleRenderer(spec, xpixsize, ypixsize, target, chain) {
          @Override
          protected PointWithNormal locateColumn(double x, double y) {
            return new PointWithNormal(Point.at(x,y), commonNormal);
          }
        };
  }

  @Override
  public boolean equals(Object o) {
    // This is a singleton instance, so equality checking is easy
    return o == this;
  }

  @Override
  protected long longHashImpl() {
    return 202412150848L;
  }

  @Override
  public String toString() {
    return "ORTHO";
  }

}
