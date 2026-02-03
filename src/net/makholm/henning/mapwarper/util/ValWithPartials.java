package net.makholm.henning.mapwarper.util;

import java.awt.geom.AffineTransform;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.Vector;

/**
 * For calculations that keep track of both an intermediate value
 * and how it varies with original x and y coordinates, here is a
 * <em>mutable</em> (for efficiency) class to hold all three
 * values and keep track of the partial derivatives with it.
 */
public final class ValWithPartials {

  public double v;
  public double dX;
  public double dY;

  @Override
  public ValWithPartials clone() {
    return new ValWithPartials().set(this);
  }

  /** Updates destructively and returns self for convenience. */
  public ValWithPartials set(ValWithPartials base) {
    v = base.v; dX = base.dX; dY = base.dY;
    return this;
  }

  /** Updates destructively and returns self for convenience. */
  public ValWithPartials setRawX(double x) {
    v = x; dX = 1; dY = 0;
    return this;
  }

  /** Updates destructively and returns self for convenience. */
  public ValWithPartials setRawY(double y) {
    v = y; dX = 0; dY = 1;
    return this;
  }

  /** Updates destructively and returns self for convenience. */
  public ValWithPartials scale(double factor) {
    v *= factor; dX *= factor; dY *= factor;
    return this;
  }

  /** Updates destructively and returns self for convenience. */
  public ValWithPartials add(double delta) {
    v += delta;
    return this;
  }

  /** Updates destructively and returns self for convenience. */
  public ValWithPartials add(ValWithPartials delta) {
    v += delta.v; dX += delta.dX; dY += delta.dY;
    return this;
  }

  public ValWithPartials sub(ValWithPartials delta) {
    v -= delta.v; dX -= delta.dX; dY -= delta.dY;
    return this;
  }

  /** Updates destructively and returns self for convenience. */
  public ValWithPartials multiply(ValWithPartials factor) {
    dX = dX * factor.v + v * factor.dX;
    dY = dY * factor.v + v * factor.dY;
    v *= factor.v;
    return this;
  }

  /** Updates destructively and returns self for convenience. */
  public ValWithPartials divide(ValWithPartials denom) {
    double recip = 1/denom.v;
    v *= recip;
    dX = (dX - v*denom.dX)*recip;
    dY = (dY - v*denom.dY)*recip;
    return this;
  }

  /**
   * Updates destructively and returns self; will write to the getsCosh
   * object destructively too if it's non-null.
   */
  public ValWithPartials sin(ValWithPartials getsCos) {
    var sin = Math.sin(v);
    var cos = Math.cos(v);
    if( getsCos != null ) {
      getsCos.v = cos;
      getsCos.dX = dX * -sin;
      getsCos.dY = dY * -sin;
    }
    v = sin;
    dX *= cos;
    dY *= cos;
    return this;
  }

  /**
   * Updates destructively and returns self; will write to the getsCosh
   * object too if it's non-null.
   */
  public ValWithPartials sinh(ValWithPartials getsCosh) {
    var sinh = Math.sinh(v);
    var cosh = Math.sqrt(1+sinh*sinh);
    if( getsCosh != null ) {
      getsCosh.v = cosh;
      getsCosh.dX = dX * sinh;
      getsCosh.dY = dY * sinh;
    }
    v = sinh;
    dX *= cosh;
    dY *= cosh;
    return this;
  }

  public ValWithPartials atan() {
    var d = 1/(1+v*v);
    v = Math.atan(v);
    dX *= d;
    dY *= d;
    return this;
  }

  public ValWithPartials atanh() {
    var d = 1/(1-v*v);
    v = 0.5 * Math.log((1+v)/(1-v));
    dX *= d;
    dY *= d;
    return this;
  }

  public double apply(double x, double y) {
    return v + dX*x + dY*y;
  }

  public double apply(Vector delta) {
    return v + dX*delta.x + dY*delta.y;
  }

  public static AffineTransform toAffine(Point orig,
      ValWithPartials x, ValWithPartials y) {
    var out = new AffineTransform(x.dX, y.dX, x.dY, y.dY, x.v, y.v);
    out.translate(-orig.x, -orig.y);
    return out;
  }

  @Override
  public String toString() {
    return v + " + " + dX + " dx + " + dY + " dy";
  }
}
