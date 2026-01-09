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
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.ListMapper;

public final class TurnedProjection extends Projection {

  final Projection base;
  final int quadrants;

  /**
   * Turns by 90° such that a warped projection where the track goes
   * to the right becomes one where the track goes up.
   */
  public static Projection turnCounterclockwise(Projection base) {
    return turnCounterclockwise(base, 1);
  }

  public static Projection turnCounterclockwise(Projection base, int quadrants) {
    if( base instanceof TurnedProjection tp ) {
      quadrants = (quadrants + tp.quadrants) % 4;
      base = tp.base;
    }
    if( quadrants == 0 )
      return base;
    else
      return new TurnedProjection(base, quadrants);
  }

  public static Projection invert(Projection base) {
    if( base instanceof TurnedProjection tp ) {
      if( tp.quadrants == 2 )
        return tp.base;
      else
        return new TurnedProjection(tp.base, (tp.quadrants+2)%4);
    } else
      return new TurnedProjection(base, 2);
  }

  private final AffineTransform local2next, next2local;

  private TurnedProjection(Projection base, int quadrants) {
    this.base = base;
    this.quadrants = quadrants;
    local2next = AffineTransform.getQuadrantRotateInstance(quadrants);
    next2local = AffineTransform.getQuadrantRotateInstance(4-quadrants);
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
      return new TurnedProjection(newBase, quadrants);
  }

  @Override
  public Projection scaleAndSqueezeSimilarly(BaseProjection realBase) {
    return new TurnedProjection(base.scaleAndSqueezeSimilarly(realBase), quadrants);
  }

  @Override
  public Projection makeSqueezeable() {
    if( base().isOrtho() && quadrants % 2 == 1 ) {
      Projection p = scaleAndSqueezeSimilarly(QuickWarp.DOWN);
      return turnCounterclockwise(p, 3);
    } else {
      return super.makeSqueezeable();
    }
  }

  @Override
  public Projection perhapsOrthoEquivalent() {
    Projection newBase = base.perhapsOrthoEquivalent();
    return newBase == null ? null : turnCounterclockwise(newBase, quadrants);
  }

  private Point local2next(Point local) {
    switch( quadrants ) {
    case 1: return Point.at(-local.y, local.x);
    case 2: return Point.at(-local.x, -local.y);
    case 3: return Point.at(local.y, -local.x);
    default: throw BadError.of("quadrants=%d", quadrants);
    }
  }

  private Point next2local(Point next) {
    switch( quadrants ) {
    case 1: return Point.at(next.y, -next.x);
    case 2: return Point.at(-next.x, -next.y);
    case 3: return Point.at(-next.y, next.x);
    default: throw BadError.of("quadrants=%d", quadrants);
    }
  }

  @Override
  public Point local2projected(Point local) {
    return base.local2projected(local2next(local));
  }

  @Override
  public Point projected2local(Point projected) {
    return next2local(base.projected2local(projected));
  }

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
  public Projection makeQuickwarp(Point local, boolean circle) {
    var got = base.makeQuickwarp(local2next(local), circle);
    return turnCounterclockwise(got, quadrants);
  }

  @Override
  public RenderFactory makeRenderFactory(LayerSpec spec) {
    RenderFactory inner = base.makeRenderFactory(spec);
    switch( quadrants ) {
    case 1: return target -> inner.makeWorker(new Turned90Target(target));
    case 2: return target -> inner.makeWorker(new Turned180Target(target));
    case 3: return target -> inner.makeWorker(new Turned270Target(target));
    default:
      throw BadError.of("Cannot render at %d×90°", quadrants);
    }
  }

  private static class Turned90Target implements RenderTarget {
    private final RenderTarget outer;
    private final int rowsm1;

    Turned90Target(RenderTarget outer) {
      this.outer = outer;
      rowsm1 = outer.rows()-1;
    }

    @Override public long left() { return -(outer.top() + outer.rows()); }
    @Override public long top() { return outer.left(); }
    @Override public int columns() { return outer.rows(); }
    @Override public int rows() { return outer.columns(); }
    @Override public boolean isUrgent() { return outer.isUrgent(); }
    @Override public boolean eagerDownload() { return outer.eagerDownload(); }
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

  private static class Turned180Target implements RenderTarget {
    private final RenderTarget outer;
    private final int rowsm1, colsm1;

    Turned180Target(RenderTarget outer) {
      this.outer = outer;
      rowsm1 = outer.rows()-1;
      colsm1 = outer.columns()-1;
    }

    @Override public long left() { return -(outer.left()+outer.columns()); }
    @Override public long top() { return -(outer.top()+outer.rows()); }
    @Override public int columns() { return outer.columns(); }
    @Override public int rows() { return outer.rows(); }
    @Override public boolean isUrgent() { return outer.isUrgent(); }
    @Override public boolean eagerDownload() { return outer.eagerDownload(); }
    @Override public void isNowGrownUp() { outer.isNowGrownUp(); }
    @Override public void pokeSchedulerAsync() { outer.pokeSchedulerAsync(); }

    @Override
    public void checkCanceled() throws AbortRendering {
      outer.checkCanceled();
    }

    @Override
    public void givePixel(int x, int y, int rgb) {
      outer.givePixel(colsm1-x, rowsm1-y, rgb);
    }
  }

  private static class Turned270Target implements RenderTarget {
    private final RenderTarget outer;
    private final int colsm1;

    Turned270Target(RenderTarget outer) {
      this.outer = outer;
      colsm1 = outer.columns()-1;
    }

    @Override public long left() { return outer.top(); }
    @Override public long top() { return -(outer.left()+outer.columns()); }
    @Override public int columns() { return outer.rows(); }
    @Override public int rows() { return outer.columns(); }
    @Override public boolean isUrgent() { return outer.isUrgent(); }
    @Override public boolean eagerDownload() { return outer.eagerDownload(); }
    @Override public void isNowGrownUp() { outer.isNowGrownUp(); }
    @Override public void pokeSchedulerAsync() { outer.pokeSchedulerAsync(); }

    @Override
    public void checkCanceled() throws AbortRendering {
      outer.checkCanceled();
    }

    @Override
    public void givePixel(int x, int y, int rgb) {
      outer.givePixel(colsm1-y, x, rgb);
    }
  }

  @Override
  public boolean equals(Object o) {
    return o == this ||
        (o instanceof TurnedProjection otp &&
            otp.quadrants == quadrants &&
            otp.base.equals(base));
  }

  @Override
  protected long longHashImpl() {
    return base.longHash() + 202501100147L * quadrants;
  }

  @Override
  public String toString() {
    return "turned("+quadrants+") "+base;
  }

}
