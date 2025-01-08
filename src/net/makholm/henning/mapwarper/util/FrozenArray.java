package net.makholm.henning.mapwarper.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class FrozenArray<T> extends AbstractList<T> {

  private final Object[] array;

  private FrozenArray(Object[] array, int ONLY_INTERNAL_USE) {
    this.array = array;
  }

  protected FrozenArray(T[] data) {
    this.array = data;
  }

  public static <T> FrozenArray<T> empty() {
    return new FrozenArray<T>(new Object[0], 0);
  }

  public static <T> FrozenArray<T> of(T[] data) {
    return new FrozenArray<T>(data);
  }

  public static <T> FrozenArray<T> freeze(Collection<T> data) {
    if( data instanceof FrozenArray<T> ia )
      return ia;
    else
      return new FrozenArray<T>(data.toArray(), 0);
  }

  public final <U> FrozenArray<U> map(Function<T,U> f) {
    return map(this, f);
  }

  public static <T,U> FrozenArray<U> map(List<T> list, Function<T,U> f) {
    Object[] mapped = new Object[list.size()];
    if( list instanceof FrozenArray<T> fa ) {
      for( int i=0; i<mapped.length; i++ )
        mapped[i] = f.apply(fa.get(i));
    } else if( list instanceof ArrayList<T> alist ) {
      for( int i=0; i<mapped.length; i++ )
        mapped[i] = f.apply(alist.get(i));
    } else {
      int i = 0;
      for( T val : list )
        mapped[i++] = f.apply(val);
      if( i != mapped.length )
        throw BadError.of("List said it had %d elements, but produced only %d",
            mapped.length, i);
    }
    return new FrozenArray<U>(mapped, 0);
  }

  public static <T> FrozenArray<T> reverse(List<T> list) {
    Object[] reversed = new Object[list.size()];
    int max = reversed.length-1;
    if( list instanceof FrozenArray<T> || list instanceof ArrayList<T> ) {
      for( int i=0; i<reversed.length; i++ )
        reversed[i] = list.get(max-i);
    } else {
      int i = 0;
      for( T val : list ) reversed[max - i++] = val;
      if( i != reversed.length )
        throw BadError.of("List said it had %d elements, but produced only %d",
            reversed.length, i);
    }
    return new FrozenArray<T>(reversed, 0);
  }

  @Override
  public final int size() {
    return array.length;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final T get(int i) {
    return (T)array[i];
  }

  @SuppressWarnings("unchecked")
  public final T last() {
    return (T)array[array.length-1];
  }

  @Override
  public final FrozenArray<T> subList(int fromIndex, int toIndex) {
    if( fromIndex == 0 && toIndex == size() )
      return this;
    Object[] newArray = new Object[toIndex-fromIndex];
    System.arraycopy(array, fromIndex,  newArray,  0,  newArray.length);
    return new FrozenArray<>(newArray, 0);
  }

  @Override
  @SuppressWarnings("unchecked")
  public final void forEach(Consumer<? super T> callback) {
    for( Object v : array ) {
      callback.accept((T)v);
    }
  }

}
