package net.makholm.henning.mapwarper.util;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.makholm.henning.mapwarper.gui.hairy.UIExecutor;

public class PokeReceiver {

  public final String name;

  private final UIExecutor executor;
  private final Runnable action;

  private final Map<PokePublisher, Runnable> subscriptions =
      new LinkedHashMap<>();
  private boolean pokePending;

  /**
   * @param action can be null in a subclass that overrides {@link #run()}.
   */
  public PokeReceiver(String name,
      Runnable action, UIExecutor executor) {
    this.name = name;
    this.action = action;
    this.executor = executor;
  }

  public PokeReceiver(String name, PokeReceiver justPokeThis) {
    this.name = name;
    this.action = justPokeThis::poke;
    this.executor = null;
  }

  protected void run() {
    action.run();
  }

  public final void setSources(Iterable<PokePublisher> wanted) {
    Set<PokePublisher> toUnsubscribe =
        new LinkedHashSet<>(subscriptions.keySet());
    for( var source : wanted ) {
      if( subscriptions.containsKey(source) ) {
        toUnsubscribe.remove(source);
      } else {
        subscriptions.put(source, source.subscribe(myEntryPoint));
      }
    }
    for( var source : toUnsubscribe ) {
      Runnable unsubscriber = subscriptions.remove(source);
      if( unsubscriber != null )
        unsubscriber.run();
    }
  }

  private final Runnable myEntryPoint = new Runnable() {
    @Override
    public void run() {
      poke();
    }

    @Override
    public String toString() {
      return "("+name+")";
    }
  };

  public void poke() {
    if( executor == null ) {
      this.run();
    } else {
      synchronized(PokeReceiver.this) {
        if( pokePending ) return;
        pokePending = true;
      }
      executor.scheduleForUIThread(myDelivery);
    }
  }

  private final Runnable myDelivery = new Runnable() {
    @Override
    public void run() {
      synchronized(PokeReceiver.this) {
        pokePending = false;
      }
      PokeReceiver.this.run();
    }

    @Override
    public String toString() {
      return "["+name+"]";
    }
  };

  @Override
  public String toString() {
    return name;
  }

}
