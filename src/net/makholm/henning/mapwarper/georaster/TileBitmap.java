package net.makholm.henning.mapwarper.georaster;

import java.awt.image.BufferedImage;

public class TileBitmap {

  public final int numPixels;

  private int[] pixdata;

  public static TileBitmap of(BufferedImage img) {
    return new TileBitmap(img);
  }

  public TileBitmap(int[] pixdata) {
    this.pixdata = pixdata;
    this.numPixels = pixdata.length;
  }

  private TileBitmap(BufferedImage img) {
    int height = img.getHeight();
    int width = img.getWidth();
    this.numPixels = height * width;
    this.pixdata = new int[numPixels];
    img.getRGB(0, 0, width, height, pixdata, 0, width);
  }

  public static TileBitmap blank(int rgb) {
    return new TileBitmap(rgb);
  }

  private TileBitmap(int rgb) {
    this.numPixels = 1;
    this.pixdata = new int[] { rgb };
  }

  public int pixelByIndex(int i) {
    return pixdata[i];
  }

}
