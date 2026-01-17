package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.function.IntPredicate;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.track.SegmentChain;

/**
 * A projection worker is not thread safe; it might cache intermediate
 * results internally.
 */
public interface ProjectionWorker {

  public abstract Projection projection();

  public abstract PointWithNormal local2global(Point p);
  public abstract Point global2local(Point p);

  public abstract Point global2localWithHint(Point global, Point nearbyLocal);

  public abstract List<Bezier> global2local(Bezier global);

  /**
   * Makes a linear transformation that makes a <em>global</em> delta at the given local
   * point into a <em>local</em> one. The constant part of the transformation is
   * unpredictable and should not be used.
   */
  public abstract AffineTransform createDifferential(Point local);

  public default IntPredicate makeBoundDiscarder(SegmentChain chain) {
    return DISCARD_NONE;
  }

  public static final IntPredicate DISCARD_NONE = i -> false;

  public default void dumpSearchStats() { }

}
