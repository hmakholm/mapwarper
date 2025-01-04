package net.makholm.henning.mapwarper.util;

/**
 * Base class for (generally immutable) things that should be nicely
 * hashable and comparable for semantic equivalence.
 */
public abstract class LongHashed {

  @Override
  public abstract boolean equals(Object o);

  protected abstract long longHashImpl();

  protected static long hashStep(long v) {
    return LyngHash.step(v);
  }

  protected final void invalidateHash() {
    longHash = 0;
  }


  private long longHash;

  public final long longHash() {
    long hash = longHash;
    if( hash == 0 ) {
      hash = longHashImpl();
      hash = hashStep(hash);
      hash *= LyngHash.MULTIPLIER;
      hash = Long.rotateRight(hash, 32) ^ ((hash-1) >>> 63);
      longHash = hash;
    }
    return hash;
  }

  @Override
  public final int hashCode() {
    return (int)longHash();
  }
}
