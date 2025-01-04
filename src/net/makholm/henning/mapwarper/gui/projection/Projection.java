package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.util.LongHashed;

public abstract class Projection extends LongHashed {

  public abstract Point local2projected(Point p);
  public abstract Point projected2local(Point p);

  /**
   * The resulting worker must either be thread-safe or freshly created.
   */
  public final ProjectionWorker createWorker() {
    AffineTransform at = createAffine();
    if( at != null ) {
      AffineTransform at2 = base().createAffine();
      UnitVector standardNormal =
          Vector.of(at2.getShearX(), at2.getScaleY()).normalize();
      return new AffineProjectionWorker(this, at, standardNormal);
    } else
      return createNonAffineWorker();
  }

  public abstract RenderFactory makeRenderFactory(LayerSpec spec);

  public abstract BaseProjection base();

  public abstract double scaleAcross();
  public abstract double scaleAlong();
  public abstract double getSqueeze();

  public AxisRect maxUnzoom() {
    return base().maxUnzoom().transform(this::projected2local);
  }

  public abstract Projection withScaleAndSqueeze(
      double pixelSizeAcross, double squeezeFactor);

  public final Projection atZoom(int zoom) {
    return withScaleAcross(Coords.zoom2pixsize(zoom));
  }

  public final Projection withScaleAcross(double pixelSizeAcross) {
    return withScaleAndSqueeze(pixelSizeAcross, getSqueeze());
  }

  public final Projection withSqueeze(double squeezeFactor) {
    return withScaleAndSqueeze(scaleAcross(), squeezeFactor);
  }

  public abstract Projection scaleAndSqueezeSimilarly(BaseProjection base);

  // -------------------------------------------------------------------------

  /**
   * If possible, represent the local->global transformation as an
   * {@link AffineTransform}. Otherwise return {@code null}.
   *
   * The caller owns the returned object and is allowed to modify it.
   */
  public abstract AffineTransform createAffine();

  protected abstract ProjectionWorker createNonAffineWorker();

}
