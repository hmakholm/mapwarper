package net.makholm.henning.mapwarper.gui.maprender;

import java.util.Locale;
import java.util.Random;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.util.MathUtil;

public abstract class SupersamplingRenderer extends SimpleRenderer {

  protected final SupersamplingRecipe supersample0;
  private final double[] scratch;

  /**
   * @param source
   * The only source chain used for supersampling. Downloads of these
   * may be selectively suppressed.
   * @param fallback
   * These are tried when the supersampling fails; the download bits here
   * are generally respected.
   */
  public static SupersamplingRecipe prepareSupersampler(
      LayerSpec spec, double xscale, double yscale,
      long source, long fallback) {
    if( source == 0 || !Toggles.SUPERSAMPLE.setIn(spec.flags()))
      return new SupersamplingRecipe(source, fallback);

    // If the target pixels are very large compared to UI pixels, we
    // don't urgently _need_ to supersample, and here would be the place
    // to disable it. But experimentally, supersampling still makes the
    // display look neater even at extreme zooms, and if it happens at
    // lower priority than the basic rendering, there seems to be little
    // reason _not_ to ...

    double pixsize = Coords.zoom2pixsize(spec.targetZoom());
    double tilePixelsPerDisplayPixel = xscale * yscale / MathUtil.sqr(pixsize);

    int idealSamples = (int)(1.61 * tilePixelsPerDisplayPixel + 5);
    int actualSamples = Math.min(idealSamples, 40);
    return new SupersamplingRecipe(actualSamples, source, fallback);

  }

  protected SupersamplingRenderer(LayerSpec spec,
      double xpixsize, double ypixsize, RenderTarget target,
      SupersamplingRecipe... recipes) {
    super(spec, xpixsize, ypixsize, target);
    this.supersample0 = recipes[0];

    int scratchSize = 0;
    for( var recipe : recipes ) {
      if( recipe.numSamples > 1 ) {
        this.renderPassesWanted = 3;
        scratchSize = Math.max(scratchSize, recipe.scratchLengthNeeded());
      }
    }
    scratch = new double[scratchSize+3];
  }

  @Override
  protected boolean renderColumn(int col, double xmid,
      int ymin, int ymax) {
    return supersampleColumn(col,
        locateColumn(xmid-0.5*xscale, ybase),
        locateColumn(xmid,            ybase),
        locateColumn(xmid+0.5*xscale, ybase),
        ymin, ymax, supersample0);
  }

  protected final boolean supersampleColumn(int col,
      PointWithNormal pwn0, PointWithNormal pwnM, PointWithNormal pwn1,
      int ymin, int ymax, SupersamplingRecipe supersample) {
    if( supersample.numSamples == 1 || renderPassesCompleted < 2 )
      return renderWithoutSupersampling(col, pwnM,
          ymin, ymax, supersample.source | supersample.fallback, 0);

    long downloadlessChain = FallbackChain.neverDownload(supersample.source);

    supersample.interpolate(scratch, col, pwn0, pwnM, pwn1, yscale);
    int numSamples = supersample.numSamples;

    boolean hadAllPixels = true;
    int patternStartIndex = ((ymin-1)&7) * numSamples * 4;
    rowloop: for( int row = ymin; row <= ymax; row++ ) {
      if( (row&7) == 0 )
        patternStartIndex = 0;
      else
        patternStartIndex += numSamples * 4;
      var patternEndIndex = patternStartIndex + numSamples * 4;

      if( patternStartIndex < 0 && patternEndIndex+3 >= scratch.length )
        throw new ArrayIndexOutOfBoundsException();
      long splitSum = 0;
      for( int i = patternStartIndex; i<patternEndIndex; i+=4 ) {
        double sx = scratch[i+0] + scratch[i+2] * row;
        double sy = scratch[i+1] + scratch[i+3] * row;
        int pixel = getRawPixel(sx, sy, downloadlessChain, false);
        if( pixel != RGB.OUTSIDE_BITMAP ) {
          splitSum += splitChannels(pixel);
        } else {
          // The fallback to no supersampling is also the place where we
          // request _download_ of the supersampling layers -- since that
          // happens at the official mid-pixel coordinates so we don't
          // risk downloading something outside the margins.
          Point p = pwnM.pointOnNormal((row+0.5) * yscale);
          int rgb = getPixel(p, supersample.source | supersample.fallback);
          if( rgb == RGB.OUTSIDE_BITMAP )
            hadAllPixels = false;
          else {
            rgb = applyTilegrid(p, rgb);
            target.givePixel(col, row, rgb);
          }
          continue rowloop;
        }
      }
      int pixel = combineChannels(splitSum, supersample.oversampleScaler);
      int rgb = mainTiles.transferFunction.toARGB(pixel);
      if( tilegrid != null ) {
        Point mid = pwnM.pointOnNormal((row+0.5)*yscale);
        rgb = applyTilegrid(mid, rgb);
      }
      target.givePixel(col, row, rgb);
    }
    return hadAllPixels;
  }

  // These two functions cleverly (?) facilitate averaging 4 channels in one
  // using 64-bit integer arithmetic and bit fiddling instead of SIMD:

  private static long splitChannels(int pixel) {
    // Transform ABCD to 0A0C0B0D
    return (pixel & 0x00FF00FFL) + ((pixel & 0xFF00FF00L) << 24);
  }

  private static int combineChannels(long splitSum, int scaler) {
    // splitSum is AACCBBDD
    int scaledA = (char)(splitSum >>> 48) * scaler;
    int scaledB = (char)(splitSum >>> 16) * scaler;
    int scaledC = (char)(splitSum >>> 32) * scaler;
    int scaledD = (char)(splitSum       ) * scaler;
    int iA000 = (scaledA << 8) & 0xFF000000;
    int i0B00 = (scaledB     ) & 0x00FF0000;
    int i00C0 = (scaledC >> 8) & 0x0000FF00;
    int i000D = (scaledD >> 16);
    return iA000 | i0B00 | i00C0 | i000D;
  }

  private static int makeScaler(int numSamples) {
    return 0xFF_FF_FF / (0xFF * numSamples);
  }

  /** A quick unit test... */
  public static void mainx(String[] args) {
    Random r = new Random();
    for( int i = 0; i<10000; i++ ) {
      int goal = r.nextInt();
      int count = r.nextInt(40)+1;
      int scaler = makeScaler(count);
      long split = splitChannels(goal);
      long splitSum = split * count;
      int got = combineChannels(splitSum, scaler);
      if( got != goal ) {
        System.out.printf(Locale.ROOT,
            "%d %08x -> %016x x%d -> %016x -> %08x\n",
            i, goal, split, count, splitSum, got);
      }
    }
  }

  public static class SupersamplingRecipe {
    final long source, fallback;
    final int numSamples;
    final float[][] multipliers;
    final int oversampleScaler;

    SupersamplingRecipe(long source, long fallback) {
      this.numSamples = 1;
      this.source = source;
      this.fallback = fallback;
      this.multipliers = null;
      this.oversampleScaler = 0;
    }

    SupersamplingRecipe(int numSamples, long source, long fallback) {
      this.numSamples = numSamples;
      this.source = source;
      this.fallback = fallback;

      // Create sample points as a base 2-Hammersley net scaled up to 8×8 pixel
      // boxes. This ought to be less sensitive to sharp edges almost parallel
      // to the axes than a rectangular sampling grid is.
      // https://en.wikipedia.org/wiki/Low-discrepancy_sequence#Hammersley_set
      multipliers = new float[8][8*numSamples*2];
      int counter = 20241227;
      for( int column = 0; column < 8; column++ ) {
        for( int i = 0 ; i < 8*numSamples ; i++ ) {
          double lefting = (i+0.5) / (8*numSamples);
          int bitflipped = Integer.reverse(counter++);
          int row = (bitflipped >>> 29);
          double downing = (double)(bitflipped & (-1 >>> 3)) / (1 << 29);
          int localIndex = i / 8;
          int index = row * numSamples + localIndex;
          multipliers[column][2 * index + 0] = (float)lefting;
          multipliers[column][2 * index + 1] = (float)downing;
        }
      }

      oversampleScaler = makeScaler(numSamples);
    }

    public int scratchLengthNeeded() {
      return 4 * 8 * numSamples;
    }

    /**
     * Precompute lines that interpolate cubically between the three given
     * ones (for the left, center, and right side of a pixel column) to produce
     * 8 sets of numSamples sample points.
     *
     * The output is delivered in the {@link scratch} array, in a sequence
     * of quadruples (xBase, yBase, dx, dy).
     */
    void interpolate(double[] scratch, int column,
        PointWithNormal pwn0, PointWithNormal pwnM, PointWithNormal pwn1,
        double yscale) {
      var colMultipliers = this.multipliers[column%8];
      var n0 = pwn0.normal.scale(yscale);
      var nM = pwnM.normal.scale(yscale);
      var n1 = pwn1.normal.scale(yscale);
      for( int i = 0; i<8*numSamples; i++ ) {
        double t = colMultipliers[2*i+0];
        double u = colMultipliers[2*i+1];
        double c0 = (t-1) * (2*t-1), c1 = t * (2*t-1);
        double cM = 1 - (c0 + c1);
        var dx = c0*n0.x + cM*nM.x + c1*n1.x;
        var dy = c0*n0.y + cM*nM.y + c1*n1.y;
        var x = c0*pwn0.x + cM*pwnM.x + c1*pwn1.x + u*dx;
        var y = c0*pwn0.y + cM*pwnM.y + c1*pwn1.y + u*dy;
        scratch[4*i+0] = x;
        scratch[4*i+1] = y;
        scratch[4*i+2] = dx;
        scratch[4*i+3] = dy;
      }
    }
  }

}