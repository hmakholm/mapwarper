package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;
import java.nio.file.Path;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.maprender.BasicRenderer;
import net.makholm.henning.mapwarper.gui.maprender.FallbackChain;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;

public final class OrthoProjection extends BaseProjection {

  public static final int WEAK_SHRINK = 3;

  public static final OrthoProjection ORTHO = new OrthoProjection();

  private OrthoProjection() { }

  @Override
  public boolean isOrtho() {
    return true;
  }

  @Override
  public Affinoid getAffinoid() {
    var aff = new Affinoid();
    aff.squeezable = false;
    return aff;
  }

  @Override
  public Projection apply(Affinoid aff) {
    if( aff.squeezable ) {
      aff.squeezable = false;
      aff.quadrantsTurned = 0;
    }
    if( aff.squeeze == 1 ) {
      return aff.apply(this);
    } else {
      UnitVector dir = UnitVector.withBearing(90-90*aff.quadrantsTurned);
      aff.squeezable = true;
      aff.quadrantsTurned = 0;
      return new QuickWarp(dir).apply(aff);
    }
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
    } else if( Toggles.DOWNLOAD.setIn(spec.flags())) {
      fallback.attemptMain();
      fallback.attemptFallbacks(2);
      chain = fallback.getChain();
    } else {
      chain = fallback.weakChain(WEAK_SHRINK);
    }
    return target
        -> new BasicRenderer(spec, xpixsize, ypixsize, target, chain) {
          @Override
          protected PointWithNormal locateColumn(double x, double y) {
            return new PointWithNormal(Point.at(x,y), commonNormal);
          }
        };
  }

  @Override
  public boolean usesDownloadFlag() {
    return true;
  }

  @Override
  public Projection makeQuickwarp(Point local, boolean circle, Affinoid aff) {
    return this.apply(aff);
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
  public String describe(Path currentFile) {
    return "";
  }

  @Override
  public String toString() {
    return "ORTHO";
  }

}
