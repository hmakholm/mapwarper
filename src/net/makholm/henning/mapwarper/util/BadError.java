package net.makholm.henning.mapwarper.util;

import java.util.Locale;

@SuppressWarnings("serial")
public class BadError extends RuntimeException {

  public BadError(String message) {
    super("\n" + message);
  }

  public static BadError of(String s) {
    return new BadError(s);
  }

  public static BadError of(String fmt, Object... params) {
    return new BadError(String.format(Locale.ROOT, fmt, params));
  }

}
