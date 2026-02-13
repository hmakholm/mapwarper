package net.makholm.henning.mapwarper.track;

import java.util.function.Function;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.TransformHelper;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.WebMercator;

/**
 * A fairly precise length estimator for segment chains, producing length
 * in meters and taking into account the Web-Mercator distortion, to a
 * precision of about 1/1000.
 *
 * The curves themselves stay what they are, so straight lines in
 * global coordinates are measured as rhumb lines, etc.
 *
 * Even though it has no overt state; it uses internal scratch fields,
 * so it is not thread safe.
 */
public class LengthEstimator extends TransformHelper
implements Function<SegmentChain, Double> {

  /**
   * When the Y coordinate changes by this much, the scale factor can change by
   * up to 1/1000 -- less so at smaller latitudes.
   *
   * In Narvik, this corresponds to about 2.5 km.
   *
   * In Copenhagen, it's 3.6 km -- and already there the variation is smaller
   * than 1 in 1200.
   *
   * In Miami, its's 5.7 km with a variation of 1 in 2300.
   */
  private static final double YSPAN = Coords.EARTH_SIZE / (Math.PI*2) / 1000;

  @Override
  public Double apply(SegmentChain chain) {
    double total = 0;
    for( var curve : chain.smoothed() )
      total += length(curve);
    return total;
  }

  public double length(Bezier curve) {
    if( Math.abs(curve.p1.y - curve.p4.y) > YSPAN ) {
      var split = curve.split(0.5);
      return length(split.front()) + length(split.back());
    } else {
      return WebMercator.mercatorize(curve, this).estimateLength();
    }
  }

}
