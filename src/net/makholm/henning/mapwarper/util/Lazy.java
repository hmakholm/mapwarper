package net.makholm.henning.mapwarper.util;

import java.util.function.Supplier;

public final class Lazy<T> implements Supplier<T> {

  private Supplier<T> maker;
  private volatile T made;

  public static <T> Lazy<T> of(Supplier<T> maker) {
    return new Lazy<>(maker);
  }

  public static <T> Lazy<T> eager(T alreadyMade) {
    var result = new Lazy<T>(null);
    result.made = alreadyMade;
    return result;
  }

  public Lazy(Supplier<T> maker) {
    this.maker = maker;
  }

  @Override
  public T get() {
    T got = made;
    if( got != null )
      return got;
    synchronized( this ) {
      if( maker == null )
        return made; // even if it made null
      made = got = maker.get();
      maker = null;
      return got;
    }
  }

}
