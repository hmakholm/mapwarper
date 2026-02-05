package net.makholm.henning.mapwarper.georaster;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.util.ValWithPartials;

/**
 * A {@link PixelAddresser} that works with the tile structure of
 * {@link CompoundShortcode}.
 *
 * In order to be compatible with the UTM TIFFs from GeoDanmark,
 * maxitile y-coordinates grow towards the north, but minitile
 * y-coordinates grow towards the south.
 */
public abstract class CompoundAddresser extends CompoundShortcode
implements PixelAddresser {

  protected CompoundAddresser(int maxipixels, int minipixels) {
    super(maxipixels, minipixels);
  }

  public static class WithUTM extends CompoundAddresser {
    final AxisRect validity;
    final Point refpoint;
    final ValWithPartials E = new ValWithPartials();
    final ValWithPartials N = new ValWithPartials();

    public WithUTM(AxisRect validity, UTM utm, double maximeter,
        int maxipixels, int minipixels, Point refpoint) {
      super(maxipixels, minipixels);
      if( validity != null ) refpoint = validity.clamp(refpoint);
      this.validity = validity;
      this.refpoint = refpoint;
      utm.toUTM(refpoint, E,  N);
      E.scale(1/maximeter);
      N.scale(1/maximeter);
      locateByMaxi(E.v, N.v);
    }

    @Override
    public long locate(double x, double y) {
      if( validity != null && !validity.contains(x, y) )
        return 0;

      x -= refpoint.x;
      y -= refpoint.y;
      return locateByMaxi(E.apply(x, y), N.apply(x, y));
    };
  }

  protected double maxix, maxiy;
  protected int pixx, pixy;

  /**
   * The parameters here must be in a coordinate system where
   * <em>maxitiles</em> have integer coordinates.
   */
  protected final long locateByMaxi(double easting, double northing) {
    maxix = easting;
    maxiy = northing;
    var tilex = Math.floor(easting);
    var tiley = Math.floor(northing);
    // This can overflow for very small _negative_ coordinates
    // In practice the coordinates are always positive, though
    pixx = (int)((easting-tilex)*maxipixels);
    pixy = (int)((tiley+1-northing)*maxipixels);
    return makeShortcode((int)tilex, (int)tiley,
        pixx >> log2minipixels, pixy >> log2minipixels);
  }

  /**
   * Allow downloading based on this large a fringe of a
   * <em>maxitile</em>. The default value here is based
   * on observed error in the extrapolated UTM conversion
   * 8 km from the focus point.
   */
  protected double downloadEdge = 8.0/1000;

  @Override
  public boolean onTileEdge() {
    return (Math.abs(maxix)+downloadEdge)%1.0 < 2*downloadEdge
        || (Math.abs(maxiy)+downloadEdge)%1.0 < 2*downloadEdge;
  }

  @Override
  public int getPixel(TileBitmap data) {
    if( data.numPixels == 1 )
      return data.pixelByIndex(0);
    int mask = (1 << log2minipixels)-1;
    int x = pixx & mask;
    int y = pixy & mask;
    return data.pixelByIndex((y << log2minipixels) + x);
  }

  @Override
  public boolean isOddDownloadTile(long shortcode) {
    return ((int)(shortcode >> 48) + (int)(shortcode >> 32)) % 2 != 0;
  }

  @Override
  public long getDownloadPriority(long shortcode) {
    // Ignore minitile position; we assume entire maxitiles will be
    // downloaded together anyway.
    int dx = tilex(shortcode)*256 + 128 - (int)(maxix * 256);
    int dy = tiley(shortcode)*256 + 128 - (int)(maxiy * 256);
    return (long)dx*dx + (long)dy*dy + ((long)maxisize(shortcode) << 47);
  }

}
