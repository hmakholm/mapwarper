package net.makholm.henning.mapwarper.util;

import java.util.function.Function;

public final class SingleMemo<T, U> implements Function<T,U> {

  private final Function<? super T, ?> keymaker;
  private final Function<? super T, ? extends U> maker;
  private volatile Made<U> made;

  public static <T,U> SingleMemo<T,U> of(
      Function<? super T,? extends U> maker) {
    return new SingleMemo<>(t->t, maker);
  }

  public static <T,U> SingleMemo<T,U> of(
      Function<? super T,?> keymaker,
      Function<? super T,? extends U> maker) {
    return new SingleMemo<>(keymaker, maker);
  }

  public SingleMemo(Function<? super T,?> keymaker,
      Function<? super T,? extends U> maker) {
    this.keymaker = keymaker;
    this.maker = maker;
  }

  @Override
  public U apply(T input) {
    Object inputKey = keymaker.apply(input);
    Made<U> got = made;
    if( got != null && got.inputKey().equals(inputKey) )
      return got.output();
    synchronized( this ) {
      got = made;
      if( got != null && got.inputKey().equals(inputKey) )
        return got.output();
      U output = maker.apply(input);
      made = new Made<>(inputKey, output);
      return output;
    }
  }

  private record Made<U>(Object inputKey, U output) {}

}
