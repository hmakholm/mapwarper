package net.makholm.henning.mapwarper.gui.projection;

import net.makholm.henning.mapwarper.util.BadError;

/**
 * This mutable object represents a way to transform the raw coordinate
 * system of a {@link BaseProjection} to the displayed coordinate system.
 * It's not a general affine transformation (thus the silly name), in that
 * it preserves the coordinate axes (but may or may not interchange them)
 * and orientation. The axes may be scaled differently for squeezing, though.
 *
 * In the particular case of a circlewarp, what is actually described here
 * is the transformation from a possibly-fictive base transformation where
 * the x-coordinate goes in the same direction as the track -- even though
 * that may be the negative of the actually implemented CircleWarp.
 * Special implementations of {@link CircleWarp#getAffinoid()} and
 * {@link CircleWarp#apply(Affinoid)} make this happen transparently to
 * the outside.
 */
public final class Affinoid {

  /**
   * This is true if the turnedness meaningfully indicates a direction
   * that tracks are being be warped along. That is, generally any
   * {@link Affinoid} except ones extracted from an ortho projection.
   */
  public boolean squeezable = true;

  public double scaleAcross = 1;
  public double squeeze = 1;
  public int quadrantsTurned = 0;

  public double scaleAlong() {
    return scaleAcross * squeeze;
  }

  public void makeSqueezable(double defaultSqueeze) {
    if( !squeezable ) {
      quadrantsTurned = 0;
      squeezable = true;
      squeeze = defaultSqueeze;
    }
  }

  public void assertSqueezable() {
    if( !squeezable ) throw BadError.of("This should be squeezable");
  }

  public void squeezefactor(double f) {
    squeeze *= f;
    if( f > 1 && squeeze > 50 ) squeeze = 50;
    if( f < 1 && squeeze < 1 ) squeeze = 1;
  }

  /**
   * This should only be called from the base projections themself.
   */
  Projection apply(BaseProjection base) {
    if( scaleAcross == 1.0 && squeeze == 1.0 )
      return TurnedProjection.turnCounterclockwise(base, quadrantsTurned);
    var scaled = new ScaledProjection(base, scaleAcross, squeeze);
    return TurnedProjection.turnCounterclockwise(scaled, quadrantsTurned);
  }

}
