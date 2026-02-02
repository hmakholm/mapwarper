package net.makholm.henning.mapwarper.georaster;

import java.awt.image.BufferedImage;

import net.makholm.henning.mapwarper.util.BadError;

public class TileBitmap {

  public final int numPixels;

  private int[] pixdata;
  private long mask;
  private byte xshift;
  private byte yrot;

  public static TileBitmap of(BufferedImage img, Tile tile) {
    int logTilesize = Integer.numberOfTrailingZeros(img.getHeight());
    if( img.getHeight() != (1 << logTilesize) ||
        img.getHeight() != (1 << logTilesize) ||
        logTilesize > 12 )
      throw BadError.of("%dx%d doesn't look like a tile; should be %s",
          img.getHeight(), img.getWidth(), tile);
    return new TileBitmap(img, tile, logTilesize,
        Coords.BITS - tile.zoom - logTilesize);
  }

  private TileBitmap(BufferedImage img, Tile tile, int logTilesize, int logPixsize) {
    int side = 1 << logTilesize;
    this.numPixels = side * side;
    this.pixdata = new int[numPixels];
    img.getRGB(0, 0, side, side, pixdata, 0, side);
    int onemask = (side-1) << logPixsize;
    this.mask = Coords.wrap(onemask, onemask);
    this.xshift = (byte)(32 + logPixsize);
    this.yrot = (byte)(logPixsize - logTilesize);
  }

  public static TileBitmap blank(int rgb) {
    return new TileBitmap(rgb);
  }

  private TileBitmap(int rgb) {
    this.numPixels = 1;
    this.pixdata = new int[] { rgb };
    this.mask = 0;
    this.xshift = 0;
    this.yrot = 0;
  }

  /** This works only for coordinates inside the tile. */
  public int pixelAt(long coords) {
    long m = coords & mask;
    int i = (int)(m >> xshift) | Integer.rotateRight((int)m, yrot);
    return pixdata[i];
  }

}
