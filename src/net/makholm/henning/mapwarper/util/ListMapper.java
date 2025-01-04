package net.makholm.henning.mapwarper.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class ListMapper {

  public static <T,U> List<U> map(List<T> list, Function<T,U> f) {
    if( list == null )
      return null;
    switch( list.size() ) {
    case 0:
      return Collections.emptyList();
    case 1:
      return Collections.singletonList(f.apply(list.get(0)));
    default:
      if( list instanceof FrozenArray ||
          list instanceof ArrayList )
        return FrozenArray.mapIndexing(list, f);
      else
        return FrozenArray.mapIterating(list, f);
    }
  }

}
