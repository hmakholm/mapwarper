package net.makholm.henning.mapwarper.georaster.geotiff;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.PixelAddresser;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.georaster.UTM;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.ValWithPartials;

/**
 * This addresser uses an external projection with northing and easting
 * coordinates to divide the world (or part of it) into square "maxitiles"
 * (of sizes not necessarily a power of 2) which are each divided into
 * "minitiles" that <em>are</em> powers of 2.
 * <p>
 * The intention is that data must be <em>downloaded</em> in entire
 * maxitiles but can be <em>decoded</em> a single minitile at a time.
 */
public abstract class CompoundAddresser implements PixelAddresser {

  public CompoundAddresser(int maxipixels, int minipixels) {
    if( maxipixels <= 0 || maxipixels >= (1<<16) )
      throw BadError.of("Wrong maxitile size %d", maxipixels);
    this.maxipixels = maxipixels;
    this.log2minipixels = Integer.numberOfTrailingZeros(minipixels);
    if( log2minipixels >= 16 || minipixels != (1<<log2minipixels) )
      throw BadError.of("Wrong minitile size %d", minipixels);
    if( maxipixels > (16 << log2minipixels) )
      throw BadError.of("Too many minitiles of %d in %d", minipixels, maxipixels);

    shortcodeBase = ((long)maxipixels << 16) +
        (log2minipixels << 12);

    if( minisPerMaxi(shortcodeBase) % 2 == 1 )
      maxisummarymask = 3;
    else
      maxisummarymask = 0;
  }

  public static class WithUTM extends CompoundAddresser {
    final AxisRect validity;
    final Point refpoint;
    final ValWithPartials E = new ValWithPartials();
    final ValWithPartials N = new ValWithPartials();

    public WithUTM(AxisRect validity, UTM utm, double maximeter,
        int maxipixels, int minipixels, Point refpoint0) {
      super(maxipixels, minipixels);
      this.validity = validity;
      this.refpoint = validity == null ? refpoint0 : validity.clamp(refpoint0);
      utm.toUTM(refpoint0, E,  N);
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

  // ------------------------------------------------------------------
  // shortcode construction and interpretation
  // - 16 bits (signed) for maxitile x address
  // - 16 bits (signed) for maxitile y address
  // - 16 bits for maxitile _size_
  // - 4 bits for log2 of minitile size
  // - 4 bits for minitile x address, counting _right_
  // - 4 bits for minitile y address, counting _up_
  // - 4 bits for replicated lower bits for localhash support.

  public static int tilex(long shortcode) {
    return (int)(shortcode >> 48);
  }

  public static int tiley(long shortcode) {
    return (int)(shortcode >> 16) >> 16;
  }

  public static int maxisize(long shortcode) {
    return (int)shortcode >>> 16;
  }

  public static int log2minisize(long shortcode) {
    return ((int)shortcode >> 12) & 0xF;
  }

  public static int minisize(long shortcode) {
    return 1 << log2minisize(shortcode);
  }

  public static int minisPerMaxi(long shortcode) {
    return (maxisize(shortcode) + minisize(shortcode) - 1)
        >> log2minisize(shortcode);
  }

  public static int minix(long shortcode) {
    return ((int)shortcode >> 8) & 0xF;
  }

  public static int miniy(long shortcode) {
    return ((int)shortcode >> 4) & 0xF;
  }

  private long makeShortcode(int tilex, int tiley, int minix, int miniy) {
    return
        ((long)tilex << 48) +
        ((long)(char)tiley << 32) +
        shortcodeBase +
        (minix << 8) +
        (miniy << 4) +
        ((minix + 2*miniy) & 3) +
        ((tilex + 2*tiley) & maxisummarymask);
  }

  // ---------------------------------------------------------------

  private final int maxipixels, log2minipixels;
  private final int maxisummarymask;
  private final long shortcodeBase;

  protected double maxix, maxiy;
  protected int pixx, pixy;

  /**
   * The parameteres here must be in a coordinate system where
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

  public static String tilename(long shortcode) {
    return maxisize(shortcode)+"/"+minisize(shortcode)+":"+
        tilex(shortcode)+","+tiley(shortcode)+
        "["+minix(shortcode)+","+miniy(shortcode)+"]";
  }

  // -------------------------------------------------------------------

  private static final int[] PSEUDO_EDGE_COLORS =
    { 0xFF0000, 0x009900, 0x0000EE, 0xDDDD00 };

  /**
   * This is occasionally useful for debugging.
   */
  public static TileBitmap pseudotile(long tile, int background) {
    int tilex = tilex(tile);
    int tiley = tiley(tile);
    int maxipixels = maxisize(tile);
    int minix = minix(tile);
    int miniy = miniy(tile);
    int minitiles = minisPerMaxi(tile);
    int size = minisize(tile);

    var bcolor = new Color(background);

    BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    var g = SwingUtils.startPaint(img.getGraphics());
    g.scale(size/100.0, size/100.0);
    Path2D.Double p = new Path2D.Double();
    p.moveTo(0,0);
    p.lineTo(0,100);
    p.lineTo(100,100);
    p.lineTo(100,0);
    p.closePath();
    g.setColor(bcolor);
    g.fill(p);
    g.setColor(new Color(PSEUDO_EDGE_COLORS[(int)tile & 3]));
    g.setStroke(new BasicStroke(4f));
    g.draw(p);

    double lum =
        bcolor.getRed()*0.3 + bcolor.getGreen()*0.6 + bcolor.getBlue()*0.1;
    if( lum > 128 )
      g.setColor(new Color(0x555555));
    else
      g.setColor(new Color(0xEEEEEE));
    g.setFont(SwingUtils.getANiceDefaultFont().deriveFont(20f));
    var fm = g.getFontMetrics();
    String s = tilex+","+tiley;
    g.drawString(s, 50-fm.stringWidth(s)/2, 40);

    g.setFont(SwingUtils.getANiceDefaultFont().deriveFont(12f));
    fm = g.getFontMetrics();
    s = maxipixels+" px/km";
    g.drawString(s, 50-fm.stringWidth(s)/2, 60);

    s = minix+","+miniy+" of "+minitiles;
    g.drawString(s, 50-fm.stringWidth(s)/2, 80);

    return TileBitmap.of(img);
  }

}
