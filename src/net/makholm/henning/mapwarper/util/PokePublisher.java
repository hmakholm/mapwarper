package net.makholm.henning.mapwarper.util;

import java.util.LinkedHashSet;

public class PokePublisher {

  public final String name;

  public PokePublisher(String name) {
    this.name = name;
  }

  private final LinkedHashSet<Runnable> subscribers = new LinkedHashSet<>();

  /**
   * @return an unsubscribe action
   */
  public final Runnable subscribe(Runnable toCall) {
    synchronized(subscribers) {
      if( !subscribers.add(toCall) )
        throw BadError.of("Double subscription of %s for %s", this, toCall);
    }
    return () -> {
      synchronized(subscribers) {
        subscribers.remove(toCall);
      }
    };
  }

  public void poke() {
    Runnable[] toRun;
    synchronized(subscribers) {
      toRun = subscribers.toArray(new Runnable[subscribers.size()]);
    }
    for( var r : toRun ) r.run();
  }

  public final int subscriberCount() {
    synchronized(subscribers) {
      return subscribers.size();
    }
  }

  public final boolean isEmpty() {
    return subscriberCount() == 0;
  }

  @Override
  public String toString() {
    return name;
  }

}
