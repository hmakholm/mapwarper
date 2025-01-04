package net.makholm.henning.mapwarper.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class SetFreezer {

  public static <T> Set<T> freeze(Collection<T> input) {
    switch( input.size() ) {
    case 0: return Collections.emptySet();
    case 1: return Collections.singleton(input.iterator().next());
    default:
      var result = new LinkedHashSet<T>();
      input.forEach(result::add);
      return result;
    }
  }

}
