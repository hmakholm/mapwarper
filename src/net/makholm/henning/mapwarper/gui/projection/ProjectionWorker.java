package net.makholm.henning.mapwarper.gui.projection;

import java.util.List;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;

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

}
