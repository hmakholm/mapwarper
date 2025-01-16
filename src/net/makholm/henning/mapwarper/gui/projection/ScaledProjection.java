package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;

final class ScaledProjection extends Projection {

  private final BaseProjection base;
  private final double xscale, yscale, squeeze;

  ScaledProjection(BaseProjection base, double scale, double squeeze) {
    this.base = base;
    this.xscale = scale * squeeze;
    this.yscale = scale;
    this.squeeze = squeeze;
  }

  @Override public BaseProjection base() { return base; }
  @Override public double scaleAcross() { return yscale; }
  @Override public double scaleAlong() { return xscale; }
  @Override public double getSqueeze() { return squeeze; }

  @Override
  public Point local2projected(Point local) {
    return Point.at(local.x * xscale, local.y * yscale);
  }

  @Override
  public Point projected2local(Point projected) {
    return Point.at(projected.x / xscale, projected.y / yscale);
  }

  @Override
  public AffineTransform createAffine() {
    AffineTransform at = base.createAffine();
    if( at != null ) at.scale(xscale, yscale);
    return at;
  }

  @Override
  public ProjectionWorker createNonAffineWorker() {
    return base.createWorker(this, xscale, yscale);
  }

  @Override
  public RenderFactory makeRenderFactory(LayerSpec spec) {
    return base.makeRenderFactory(spec, xscale, yscale);
  }

  @Override
  public Projection withScaleAndSqueeze(double newScale, double newSqueeze) {
    return base.withScaleAndSqueeze(newScale, newSqueeze);
  }

  @Override
  public Projection scaleAndSqueezeSimilarly(BaseProjection base) {
    return new ScaledProjection(base, yscale, squeeze);
  }

  @Override
  public Projection makeQuickwarp(Point local, boolean circle) {
    var projected = Point.at(local.x * xscale, local.y * yscale);
    var got = base.makeQuickwarp(projected, circle);
    return got.withScaleAndSqueeze(
        got.scaleAcross()*yscale,
        got.getSqueeze()*squeeze);
  }

  @Override
  public boolean equals(Object o) {
    return o == this ||
        (o instanceof ScaledProjection osp &&
            osp.longHash() == longHash() &&
            osp.base.equals(base) &&
            osp.xscale == xscale &&
            osp.yscale == yscale);
  }

  @Override
  protected long longHashImpl() {
    long hash = base.longHash();
    hash ^= Double.doubleToLongBits(xscale);
    hash = hashStep(hash);
    hash ^= Double.doubleToLongBits(yscale);
    return hash;
  }

  @Override
  public String toString() {
    float zoom = (float)(22 - Math.log(yscale)/Math.log(2));
    String zoomstr = zoom == (int)zoom ? "z"+(int)zoom : "z"+zoom;
    if( yscale < 0 ) zoomstr = "-"+zoomstr;
    if( squeeze == 1.0 ) {
      return base+" zoomed "+zoomstr;
    } else {
      return base+" zoomed "+zoomstr+" squeezed "+squeeze;
    }
  }

}
