package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.util.LongHashed;
import net.makholm.henning.mapwarper.util.MathUtil;

public abstract class Projection extends LongHashed {

  public abstract Point local2projected(Point p);
  public abstract Point projected2local(Point p);

  public AxisRect local2projected(AxisRect r) {
    return new AxisRect(local2projected(r.nwCorner()),
        local2projected(r.seCorner()));
  }

  public AxisRect projected2local(AxisRect r) {
    return new AxisRect(projected2local(r.nwCorner()),
        projected2local(r.seCorner()));
  }

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

  public AxisRect maxUnzoom() {
    return base().maxUnzoom().transform(this::projected2local);
  }

  public abstract Affinoid getAffinoid();

  public final Projection withScaleAcross(double pixelSizeAcross) {
    var aff = getAffinoid();
    aff.scaleAcross = MathUtil.snapToPowerOf2(pixelSizeAcross, 0.0001);
    return base().apply(aff);
  }

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
