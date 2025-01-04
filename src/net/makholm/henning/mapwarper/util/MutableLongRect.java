package net.makholm.henning.mapwarper.util;

public class MutableLongRect {

  public long left, right, top, bottom;

  public long width() {
    return right - left;
  }

  public long height() {
    return bottom - top;
  }

}
