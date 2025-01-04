package net.makholm.henning.mapwarper.geometry;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

public class TransformHelper {

  private final Point2D.Double temp = new Point2D.Double();

  public Point apply(AffineTransform at, Point p) {
    temp.x = p.x;
    temp.y = p.y;
    at.transform(temp, temp);
    return Point.at(temp.x, temp.y);
  }

  public Vector applyDelta(AffineTransform at, Vector v) {
    temp.x = v.x;
    temp.y = v.y;
    at.deltaTransform(temp, temp);
    return Vector.of(temp.x, temp.y);
  }

}
