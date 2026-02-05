package net.makholm.henning.mapwarper.util;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public abstract class BackgroundThread extends Thread {

  public BackgroundThread(String name) {
    super(name);
    setDaemon(true);
  }

  public static Executor executor(String name) {
    return Executors.newSingleThreadExecutor(r -> new BackgroundThread(name) {
      @Override
      public void run() {
        r.run();
      }
    });
  }

  @Override
  public abstract void run();

  public static void scheduleAbort(String name,
      Throwable rExn, String rString) {
    new BackgroundThread(name) {
      @Override
      public void run() {}
    }.scheduleAbort(rExn, rString);
  }

  protected final void scheduleAbort(Throwable rExn, String rString) {
    if( where == null ) {
      if( rString == null && rExn != null )
        rString = rExn.getClass().getSimpleName();
      if( rExn == null )
        rExn = new Throwable("(dummy stacktrace)");
      synchronized(ERRORPOKE) {
        where = this;
        exn = rExn;
        string = rString;
      }
      ERRORPOKE.poke();
    }
  }

  // --------------------------------------------------------

  public static final PokePublisher ERRORPOKE =
      new PokePublisher("BackgroundAbort");

  public static boolean shouldAbort() {
    return where != null;
  }

  public static void printStackTrace() {
    synchronized(ERRORPOKE) {
      System.err.println("Deferred error in "+where.getName()+": "+string);
      if( exn != null )
        exn.printStackTrace();
      where = null;
    }
  }

  // --------------------------------------------------------

  private static BackgroundThread where;
  private static Throwable exn;
  private static String string;

}
