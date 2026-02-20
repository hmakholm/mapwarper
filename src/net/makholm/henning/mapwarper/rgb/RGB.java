package net.makholm.henning.mapwarper.rgb;

public class RGB {

  public static final int OPAQUE = 0xFF000000;

  public static final int OUTSIDE_BITMAP   = 0x00DEAD00;

  public static final int OTHER_BOUND     = 0x80C080 | OPAQUE;

  // ----------------------------------------------------------------

  public static final boolean isTransparent(int rgb) {
    return (rgb >> 24) == 0;
  }

  public static final boolean anyTransparency(int rgb) {
    return (rgb >> 24) != -1;
  }

  public static final boolean isOpaque(int rgb) {
    return (rgb >> 24) == -1;
  }

  // ----------------------------------------------------------------

  public interface TransferFunction {
    int toARGB(int pixel);
  }

  public static final TransferFunction OPAQUIFY = pixel -> pixel | OPAQUE;
  public static final TransferFunction ARGB = pixel -> pixel;

}
