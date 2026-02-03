package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.gui.projection.Affinoid;

/**
 * Helper class for handling the UI related to squeeze factors.
 */
public class Squeezing {

  public final MapView owner;
  private double remembered = DEFAULT;

  public Squeezing(MapView owner) {
    this.owner = owner;
  }

  private static final double DEFAULT = 9;

  private static final double[] STD_FACTORS = {
      1,
      1.4,
      2,
      351/121.0, // here three turns of 1:9 each show up as 45 degrees
      40/9.0, // here two turns of 1:9 each show up as 45 degrees
      19/3.0,
      9, // shows interconnections with common 1:9 switches as 45 degrees
      13, // 1:12 might be more common, but 13 gives a nicer progression
      19, // 1:19 is a somewhat common angle for high-speed switches
      25,
      35
  };

  public double defaultSqueeze() {
    return DEFAULT;
  }

  public void setSqueeze(Affinoid aff, boolean insistOnSqueezing) {
    aff.makeSqueezable(defaultSqueeze());
    if( insistOnSqueezing && aff.squeeze < 1.9 )
      aff.squeeze = remembered;
  }

  public void unsqueezeCommand() {
    var aff = owner.projection.getAffinoid();
    if( aff.squeeze > 1.35 ) {
      remembered = aff.squeeze;
      aff.squeeze = 1;
    } else {
      aff.squeeze = remembered;
    }
    owner.setProjection(owner.projection.base().apply(aff));
  }

  public Runnable stepCommand(int step) {
    var aff = owner.projection.getAffinoid();
    double prev = aff.squeeze;
    aff.squeeze = roundAndStep(prev, step);
    if( aff.squeeze == prev )
      return null;
    else
      return () -> owner.setProjection(owner.projection.base().apply(aff));
  }

  public double round(double prev) {
    return roundAndStep(prev, 0);
  }

  private static double roundAndStep(double prev, int step) {
    int bestIdx = 0;
    double bestDist = Double.POSITIVE_INFINITY;
    for( int i = 0; i < STD_FACTORS.length; i++ ) {
      double dist = prev/STD_FACTORS[i] + STD_FACTORS[i]/prev;
      if( dist < bestDist ) {
        bestIdx = i;
        bestDist = dist;
      }
    }
    int target = bestIdx + step;
    if( target >= 0 && target < STD_FACTORS.length )
      return STD_FACTORS[target];
    else
      return STD_FACTORS[bestIdx];
  }

}
