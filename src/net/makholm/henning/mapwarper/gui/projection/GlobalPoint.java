package net.makholm.henning.mapwarper.gui.projection;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.util.LyngHash;

final class GlobalPoint extends Point {

  private final long longHash;

  GlobalPoint(double x, double y) {
    super(x, y);
    long h = Double.doubleToRawLongBits(x);
    h = Long.rotateRight(h, 32) ^ Double.doubleToRawLongBits(y);
    longHash = LyngHash.hash64to64(h);
  }

  static GlobalPoint of(Point p) {
    return p instanceof GlobalPoint gp ? gp : new GlobalPoint(p.x, p.y);
  }

  @Override
  public int hashCode() {
    return (int)longHash;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this ||
        obj instanceof GlobalPoint o &&
        o.x == x && o.y == y;
  }

}
