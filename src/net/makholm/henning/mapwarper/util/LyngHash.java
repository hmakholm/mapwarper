package net.makholm.henning.mapwarper.util;

public class LyngHash {

  public static final long MULTIPLIER = 0xb3467b182d4b41bbL;

  public static long step(long v) {
    v *= MULTIPLIER;
    v = Long.reverseBytes(v) ^ (v >>> 4);
    return v;
  }

  public static int hash64to32(long v) {
    v ^= v >>> 21;
    v = step(v);
    v *= MULTIPLIER;
    return (int) (v >>> 32);
  }

  public static int hash32to32(int i) {
    long v = i * MULTIPLIER;
    v = Long.reverseBytes(v) ^ (v >>> 4);
    v *= MULTIPLIER;
    return (int) (v >>> 32);
  }

  public static long hash64to64(long v) {
    v ^= v >>> 21;
    v = step(v);
    v *= MULTIPLIER;
    return v ^ (v >> 21);
  }

}
