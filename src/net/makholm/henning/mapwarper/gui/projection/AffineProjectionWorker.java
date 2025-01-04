package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.util.Collections;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.TransformHelper;
import net.makholm.henning.mapwarper.geometry.UnitVector;

class AffineProjectionWorker extends TransformHelper
implements ProjectionWorker {

  private final Projection projection;

  private final AffineTransform local2global;
  private final AffineTransform global2local;

  private final UnitVector baseNormal;

  AffineProjectionWorker(Projection owner, AffineTransform local2global,
      UnitVector baseNormal) {
    this.projection = owner;
    this.baseNormal = baseNormal;
    try {
      this.local2global = local2global;
      this.global2local = local2global.createInverse();
    } catch (NoninvertibleTransformException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Projection projection() {
    return projection;
  }

  @Override
  public PointWithNormal local2global(Point local) {
    return new PointWithNormal(apply(local2global, local), baseNormal);
  }

  @Override
  public Point global2local(Point global) {
    return apply(global2local, global);
  }

  @Override
  public Point global2localWithHint(Point global, Point nearbyLocal) {
    return global2local(global);
  }

  @Override
  public List<Bezier> global2local(Bezier global) {
    return Collections.singletonList(global.transform(global2local, this));
  }

}
