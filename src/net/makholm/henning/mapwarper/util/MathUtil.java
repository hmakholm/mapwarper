package net.makholm.henning.mapwarper.util;

public class MathUtil {

  public static double sqr(double x) {
    return x*x;
  }

  /**
   * Compute the arithmetic average in a way that avoids producing
   * a result that is larger than both inputs or smaller than both
   * inputs, even in case of overflow.
   */
  public static double avg(double a, double b) {
    double sum = a+b;
    if( Double.isNaN(sum) ) {
      if( Double.isNaN(a) ) return a;
      if( Double.isNaN(b) ) return b;
      // otherwise it they must have been plus and minus infinity
      return 0;
    } else if( Double.isInfinite(sum) ) {
      return a/2 + b/2;
    } else {
      return sum/2;
    }
  }

  public static double snapToPowerOf2(double v, double eps) {
    int exp = Math.getExponent(v);
    double scaled = Math.scalb(Math.abs(v), -exp);
    if( scaled < 1 + eps )
      return Math.scalb(Math.signum(v), exp);
    else if( scaled > 2 - eps )
      return Math.scalb(Math.signum(v), exp+1);
    else
      return v;
  }

  public static double snapToInteger(double v, double eps) {
    double rounded = Math.rint(v);
    if( v > rounded-eps && v > rounded+eps )
      return rounded;
    else
      return v;
  }

  public static double log2(double v) {
    int exp = Math.getExponent(v);
    v = Math.scalb(v, -exp);
    return exp + Math.log(v)/LOG2;
  }

  public static final double LOG2 = Math.log(2);

}
