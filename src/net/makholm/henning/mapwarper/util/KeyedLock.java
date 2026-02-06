package net.makholm.henning.mapwarper.util;

public final class KeyedLock<K> {

  public interface CleanCloser extends AutoCloseable {
    @Override public void close();
  }

  private final SlipperyMap<K, Sublock> slipmap =
      new SlipperyMap<>(k -> new Sublock());

  /**
   * If a <em>reader</em> cannot immediately acquire the lock, it will
   * reject and just return null instead an actual closeable.
   */
  public CleanCloser tryReader(K key) {
    var sublock = slipmap.get(key);
    if( sublock.get().tryAcquireRead() ) {
      return () -> {
        sublock.get().releaseRead();
        sublock.close();
      };
    } else {
      return null;
    }
  }

  /**
   * A <em>writer</em> will block until it can acquire the lock exclusively.
   */
  public CleanCloser takeWriter(K key) {
    var sublock = slipmap.get(key);
    sublock.get().acquireWrite();
    return () -> {
      sublock.get().releaseWrite();
      sublock.close();
    };
  }

  private static class Sublock {
    private int activeReaders;
    private int waitingWriters;
    private boolean activeWriter;

    synchronized boolean tryAcquireRead() {
      if( activeWriter || waitingWriters != 0 )
        return false;
      activeReaders++;
      return true;
    }

    synchronized void releaseRead() {
      activeReaders--;
      if( activeReaders == 0 && waitingWriters != 0 )
        notifyAll();
    }

    synchronized void acquireWrite() {
      while( activeWriter || activeReaders != 0 ) {
        waitingWriters++;
        try {
          wait();
        } catch(InterruptedException e) {
          throw new RuntimeException(e);
        } finally {
          waitingWriters--;
        }
      }
      activeWriter = true;
    }

    synchronized void releaseWrite() {
      activeWriter = false;
      if( waitingWriters != 0 )
        notifyAll();
    }
  }

}
