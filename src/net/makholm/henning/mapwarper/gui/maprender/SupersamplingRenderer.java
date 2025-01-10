package net.makholm.henning.mapwarper.gui.maprender;

import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.util.MathUtil;

public abstract class SupersamplingRenderer extends SimpleRenderer {

  final SupersamplingRecipe supersample;

  public static SupersamplingRecipe prepareSupersampler(
      LayerSpec spec, double xscale, double yscale,
      long supersamplingChain) {
    if( supersamplingChain == 0 ||
        !Toggles.SUPERSAMPLE.setIn(spec.flags()))
      return null;

    // If the target pixels are very large compared to UI pixels, we
    // don't urgently _need_ to supersample, and here would be the place
    // to disable it. But experimentally, supersampling still makes the
    // display look neater even at extreme zooms, and if it happens at
    // lower priority than the basic rendering, there seems to be little
    // reason _not_ to ...

    double pixsize = Coords.zoom2pixsize(spec.targetZoom());
    double tilePixelsPerDisplayPixel = xscale * yscale / MathUtil.sqr(pixsize);

    int idealSamples = (int)(1.61 * tilePixelsPerDisplayPixel + 5);
    return new SupersamplingRecipe(Math.min(idealSamples, 40),
        supersamplingChain);
  }

  protected SupersamplingRenderer(LayerSpec spec,
      double xpixsize, double ypixsize, RenderTarget target,
      SupersamplingRecipe supersample, long fallbackChain) {
    super(spec, xpixsize, ypixsize, target, fallbackChain);
    this.supersample = supersample;
    this.renderPassesWanted = 3;
  }

  protected long combinedChain() {
    return fallbackChain | supersample.supersamplingChain;
  }

  @Override
  protected boolean renderColumn(int col, double xmid,
      int ymin, int ymax, double ybase) {
    if( supersample == null || renderPassesCompleted < 2 )
      return super.renderColumn(col, xmid, ymin, ymax, ybase);

    float[] colMultipliers = supersample.multipliers[col%8];
    int numSamples = supersample.numSamples;
    long supersamplingChain = supersample.supersamplingChain;

    PointWithNormal leftBase = locateColumn(xmid - xscale/2, ybase);
    PointWithNormal rightBase = locateColumn(xmid + xscale/2, ybase);
    double bx = leftBase.x, by = leftBase.y;
    double mx = rightBase.x - bx, my = rightBase.y - by;
    double dx = leftBase.normal.x * yscale, dy = leftBase.normal.y * yscale;
    double dmx = rightBase.normal.x * yscale - dx;
    double dmy = rightBase.normal.y * yscale - dy;

    boolean hadAllPixels = true;
    rowloop: for( int row = ymin; row <= ymax; row++ ) {
      double nwX = bx + row*dx, acrossX = mx + row*dmx;
      double nwY = by + row*dy, acrossY = my + row*dmy;

      int patternStartIndex = (row%8) * numSamples * 2;
      int rbSum = 0;
      int gSum = 0;
      for( int i = 0; i<numSamples; i++ ) {
        double lefting = colMultipliers[patternStartIndex + 2*i + 0];
        double downing = colMultipliers[patternStartIndex + 2*i + 1];
        double product = lefting*downing;
        double sx = nwX + lefting*acrossX + downing*dx + product*dmx;
        double sy = nwY + lefting*acrossY + downing*dy + product*dmy;
        int rgb = getPixel(sx, sy, supersamplingChain);
        if( RGB.anyTransparency(rgb) ) {
          // Either missing or (partially) transparent.
          // We can't supersample transparency, so in both cases
          // fall back to no supersampling.
          double cx = nwX + acrossX/2 + dx/2 + dmx/4;
          double cy = nwY + acrossY/2 + dy/2 + dmy/4;
          rgb = getPixel(cx, cy, fallbackChain);
          if( rgb == RGB.OUTSIDE_BITMAP )
            hadAllPixels = false;
          else
            target.givePixel(col, row, rgb);
          continue rowloop;
        }
        rbSum += rgb & 0xFF00FF;
        gSum += rgb & 0x00FF00;
      }
      int rScaled = (rbSum >>> 16) * supersample.oversampleScaler;
      int gScaled = gSum * supersample.oversampleScaler;
      int bScaled = (rbSum & 0xFFFF) * supersample.oversampleScaler;
      target.givePixel(col, row, RGB.OPAQUE |
          (rScaled & 0xFF0000) |
          ((gScaled >> 16) & 0x00FF00) |
          (bScaled >> 16));
    }
    return hadAllPixels;
  }

  public static class SupersamplingRecipe {
    final long supersamplingChain;
    final int numSamples;
    final float[][] multipliers;
    final int oversampleScaler;

    SupersamplingRecipe(int numSamples, long supersamlingChain) {
      this.supersamplingChain = supersamlingChain;
      this.numSamples = numSamples;

      // Create sample points as a 2-Hammersley set scaled up to 8×8 pixel
      // boxes. This ought to be less sensitive to sharp edges almost parallel
      // to the axes than a rectangular sampling grid is.
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

      oversampleScaler = 0xFF_FF_FF / (0xFF * numSamples);
    }
  }

}