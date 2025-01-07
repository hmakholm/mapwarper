package net.makholm.henning.mapwarper.util;

/**
 * A generic class that can find a point where a function goes from
 * negative to positive, under the assumption that <em>either</em>
 * we know the function is eventually negative for large enough negative
 * arguments, and eventually positive for large enough positive arguments,
 * <em>or</em> that we know interval such that it is negative at the
 * start and positive at the end.
 *
 * The iteration can optionally make use of a derivative, in which case
 * we end up doing Newton-Raphson but with some extra probes to ensure
 * progress.
 *
 * If a derivative is not defined, the algorithm falls back on linear
 * interpolation (extrapolation).
 *
 * The number of evaluations is never more than a constant times what
 * straight bisection would use.
 */
public abstract class RootFinder {
  public final double precision;
  private final double minjump;
  private final double maxjump;

  /**
   * The precision is the difference in x-values within which we'll
   * <em>always</em> use linear interpolation -- as such it bounds the
   * possible error, but usually the result will be more precise than that.
   */
  protected RootFinder(double precision) {
    this.precision = precision;
    this.minjump = precision * 0.9;
    this.maxjump = 1000 * precision;
  }

  protected abstract double f(double x);

  protected double derivative(double x) {
    // Returning NaN makes us fall back on linear interpolations
    return Double.NaN;
  }

  public final double rootNear(double a) {
    return rootNear(a, f(a));
  }

  /**
   * Find a root under the assumption that f(x) is eventually negative
   * when x is negative enough, and eventually positive when x is positive
   * enough. The parameter a with fa=f(a) is an initial guess for roughly
   * where the root might be.
   */
  public final double rootNear(double a, double fa) {
    if( fa == 0 ) return a;
    double jump = minjump;
    double d = derivative(a);
    if( !(d > 0) ) d = 1;
    // move outwards exponentially until we see a value of a different sign.
    for(;;) {
      if( d > 0 )
        jump = Math.max(jump, Math.min(maxjump, Math.abs(fa)/d));
      double x = a - Math.copySign(jump, fa);
      double fx = f(x); if( fx == 0 ) return x;
      if( Math.copySign(fx, fa) == fx ) {
        // The sign didn't change, so try again with a larger jump
        d = derivative(a);
        if( !(d > 0) ) d = (fx-fa)/(x-a);
        a = x; fa = fx;
        jump *= 2;
      } else if( x < a ) {
        return rootBetween(x, fx, a, fa);
      } else {
        return rootBetween(a, fa, x, fx);
      }
    }
  }

  /**
   * Find a root under the assumptions a<=b and f(a) = fa <= 0 <= fb = f(b).
   */
  public final double rootBetween(double a, double fa, double b, double fb) {
    if( fa == 0 ) return a;
    if( fb == 0 ) return b;
    if( fa > 0 || fb < 0 ) {
      System.err.println("Whoops, this is against an invariant!");
      return MathUtil.avg(a, b);
    }
    for(;;) {
      double width = b-a;
      double x, fx;
      if( width <= precision ) {
        // Plain linear interpolation
        return a - (b-a)*fa/(fb-fa);
      } else if( Math.abs(fa) <= Math.abs(fb) ) {
        double d = derivative(a);
        // Use linear interpolation if we get NaN, 0, or negative
        if( !(d>0) ) d = (fb-fa)/(b-a);
        double jump = Math.max(minjump, -fa/d);
        // First try to jump the distance predicted by the derivative, except
        // don't jump more than half the interval. If we would jump more than
        // half the distance to b, but still |fa|<|fb|, the function must
        // have a weird shape anyway, and we're better served by cutting in
        // halves until we narrow in on a more uniform interval.
        if( jump*2 < width ) {
          x = a+jump; fx = f(x); if( fx == 0 ) return x;
          if( fx > 0 ) { b = x; fb = fx; continue; } else { a = x; fa = fx; }
          // Next, try the same jump distance once more, in case the function
          // was just a bit concave down, though still not jumping more than
          // half the remaining distance.
          if( jump*3 < width ) {
            x += jump; fx = f(x); if( fx == 0 ) return x;
            if( fx > 0 ) { b = x; fb = fx; continue; } else { a = x; fa = fx; }
          }
        }
      } else {
        // the case for |fb|<|fa| is the same, mutatis mutandis
        double d = derivative(b);
        if( !(d>0) ) d = (fb-fa)/(b-a);
        double jump = Math.max(minjump, fb/d);
        if( jump*2 < width ) {
          x = b-jump; fx = f(x); if( fx == 0 ) return x;
          if( fx < 0 ) { a = x; fa = fx; continue; } else { b = x; fb = fx; }
          if( jump*3 < width ) {
            x -= jump; fx = f(x); if( fx == 0 ) return x;
            if( fx < 0 ) { a = x; fa = fx; continue; } else { b = x; fb = fx; }
          }
        }
      }
      // We get down here after two of the fancy derivative-guided guesses
      // _didn't_ manage to shrink by a factor of 2. Do a plain bisection
      // step to ensure progress happens.
      x = (a+b)/2; fx = f(x); if( fx == 0 ) return x;
      if( fx > 0 ) { b = x; fb = fx; } else { a = x; fa = fx; }
    }
  }

}
