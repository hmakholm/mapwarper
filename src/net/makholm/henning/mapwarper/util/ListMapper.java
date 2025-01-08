package net.makholm.henning.mapwarper.util;

import java.util.List;
import java.util.function.Function;

public class ListMapper {

  public static <T,U> List<U> map(List<T> list, Function<T,U> f) {
    if( list == null )
      return null;
    switch( list.size() ) {
    case 0:
      return List.of();
    case 1:
      return List.of(f.apply(list.get(0)));
    case 2:
      return List.of(f.apply(list.get(0)), f.apply(list.get(1)));
    default:
      return FrozenArray.map(list, f);
    }
  }

  public static <T> List<T> reverse(List<T> list) {
    if( list == null || list.size() <= 1 )
      return list;
    else if( list.size() == 2 )
      return List.of(list.get(1), list.get(0));
    else
      return FrozenArray.reverse(list);
  }

}
