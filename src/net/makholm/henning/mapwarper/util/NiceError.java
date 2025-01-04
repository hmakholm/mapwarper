package net.makholm.henning.mapwarper.util;

import java.util.Locale;

@SuppressWarnings("serial")
public class NiceError extends RuntimeException {

  public NiceError(String message) {
    super(message);
  }

  public static NiceError of(String s) {
    return new NiceError(s);
  }

  public static NiceError of(String fmt, Object... params) {
    return new NiceError(String.format(Locale.ROOT, fmt, params));
  }

}
