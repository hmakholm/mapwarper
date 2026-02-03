package net.makholm.henning.mapwarper.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class MemoryMapped {

  private static Object theUnsafe;
  private static Method invokeCleaner;

  public static void close(ByteBuffer buf) {
    synchronized(MemoryMapped.class) {
      if( invokeCleaner == null ) {
        try {
          var unsafeClass = Class.forName("sun.misc.Unsafe");
          var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
          unsafeField.setAccessible(true);
          theUnsafe = unsafeField.get(null);
          invokeCleaner = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
        } catch( Exception e ) {
          throw BadError.of("Could not initialize Unsafe: %s", e);
        }
      }
    }
    try {
      invokeCleaner.invoke(theUnsafe, buf);
    } catch (IllegalAccessException |
        IllegalArgumentException |
        InvocationTargetException e) {
      throw BadError.of("Could not clean up ByteBuffer: %s", e);
    }
  }

}
