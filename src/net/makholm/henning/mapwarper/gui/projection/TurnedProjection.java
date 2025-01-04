package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.TransformHelper;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.util.AbortRendering;
import net.makholm.henning.mapwarper.util.ListMapper;

public final class TurnedProjection extends Projection {

  protected final Projection base;

  /**
   * Turns by 90° such that a warped projection where the track goes
   * to the right becomes one where the track goes up.
   */
  public static Projection turnCounterclockwise(Projection base) {
    if( base instanceof TurnedProjection tp ) {
      return tp.base.withScaleAcross(-tp.base.scaleAcross());
    } else {
      return new TurnedProjection(base);
    }
  }

  private TurnedProjection(Projection base) {
    this.base = base;
  }

  @Override public BaseProjection base() { return base.base(); }
  @Override public double scaleAcross() { return base.scaleAcross(); }
  @Override public double scaleAlong() { return base.scaleAlong(); }
  @Override public double getSqueeze() { return base.getSqueeze(); }

  @Override
  public Projection withScaleAndSqueeze(double scale, double squeeze) {
    Projection newBase = base.withScaleAndSqueeze(scale, squeeze);
    if( newBase.equals(base) )
      return this;
    else
      return new TurnedProjection(newBase);
  }

  @Override
  public Projection scaleAndSqueezeSimilarly(BaseProjection realBase) {
    return new TurnedProjection(base.scaleAndSqueezeSimilarly(realBase));
  }

  private static Point local2next(Point local) {
    return Point.at(-local.y, local.x);
  }

  private static Point next2local(Point next) {
    return Point.at(next.y, -next.x);
  }

  @Override
  public Point local2projected(Point local) {
    return base.local2projected(local2next(local));
  }

  @Override
  public Point projected2local(Point projected) {
    return next2local(base.projected2local(projected));
  }

  private static final AffineTransform local2next =
      AffineTransform.getQuadrantRotateInstance(1);

  private static final AffineTransform next2local =
      AffineTransform.getQuadrantRotateInstance(3);

  @Override
  public AffineTransform createAffine() {
    AffineTransform at = base.createAffine();
    if( at != null ) at.concatenate(local2next);
    return at;
  }

  private class TurningWorker extends TransformHelper
  implements ProjectionWorker {
    ProjectionWorker inner = base.createWorker();

    @Override
    public Projection projection() {
      return TurnedProjection.this;
    }

    @Override
    public PointWithNormal local2global(Point p) {
      return inner.local2global(local2next(p));
    }

    @Override
    public Point global2local(Point p) {
      return next2local(inner.global2local(p));
    }

    @Override
    public Point global2localWithHint(Point global, Point nearbyLocal) {
      var hint = local2next(nearbyLocal);
      return next2local(inner.global2localWithHint(global, hint));
    }

    @Override
    public List<Bezier> global2local(Bezier global) {
      List<Bezier> got = inner.global2local(global);
      return ListMapper.map(got, b -> b.transform(next2local, this));
    }
  }

  @Override
  public ProjectionWorker createNonAffineWorker() {
    return new TurningWorker();
  }

  @Override
  public RenderFactory makeRenderFactory(LayerSpec spec) {
    RenderFactory inner = base.makeRenderFactory(spec);
    return target -> inner.makeWorker(new TurnedTarget(target));
  }

  private static class TurnedTarget implements RenderTarget {
    private final RenderTarget outer;
    private final int rowsm1;

    TurnedTarget(RenderTarget outer) {
      this.outer = outer;
      rowsm1 = outer.rows()-1;
    }

    @Override public long left() { return -(outer.top() + outer.rows()); }
    @Override public long top() { return outer.left(); }
    @Override public int columns() { return outer.rows(); }
    @Override public int rows() { return outer.columns(); }
    @Override public boolean isUrgent() { return outer.isUrgent(); }
    @Override public void isNowGrownUp() { outer.isNowGrownUp(); }
    @Override public void pokeSchedulerAsync() { outer.pokeSchedulerAsync(); }

    @Override
    public void checkCanceled() throws AbortRendering {
      outer.checkCanceled();
    }

    @Override
    public void givePixel(int x, int y, int rgb) {
      outer.givePixel(y, rowsm1-x, rgb);
    }
  }

  @Override
  public boolean equals(Object o) {
    return o == this ||
        (o instanceof TurnedProjection otp && otp.base.equals(base));
  }

  @Override
  protected long longHashImpl() {
    return base.longHash() + 202412151456L;
  }

  @Override
  public String toString() {
    return "turned "+base;
  }

}
