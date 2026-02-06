package net.makholm.henning.mapwarper.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This map remembers its value only as long as there's a thread doing
 * a computation on it.
 */
public final class SlipperyMap<K, V> {

  private final Function<K, V> fresh;
  private final Map<K, Entry> map = new LinkedHashMap<>();

  public SlipperyMap(Function<K, V> fresh) {
    this.fresh = fresh;
  }

  public Entry get(K key) {
    while(true) {
      Entry e;
      synchronized(map) {
        e = map.computeIfAbsent(key, k->new Entry(key, fresh.apply(key)));
      }
      synchronized(e) {
        if( !e.retired ) {
          e.users++;
          return e;
        }
      }
    }
  }

  public final class Entry implements Supplier<V>, AutoCloseable {
    private final K key;
    private final V value;
    private int users = 0;
    private boolean retired;
    private Entry(K key, V value) {
      this.key = key;
      this.value = value;
    }
    @Override
    public V get() {
      return value;
    }
    @Override
    public void close() {
      synchronized(this) {
        users--;
        if( users == 0 ) {
          retired = true;
          synchronized(map) {
            map.remove(key);
          }
        }
      }
    }
  }

}
