package net.makholm.henning.mapwarper.util;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;

public class XyTree<T> extends AxisRect {

  public static boolean isEmpty(XyTree<?> tree) {
    return tree == null;
  }

  public static <T> XyTree<T> singleton(Point p, T data) {
    return new XyTree<>(p, data);
  }

  public static <T> XyTree<T> singleton(AxisRect rect, T data) {
    return new XyTree<>(rect, data);
  }

  public static abstract class Unioner<T> {
    public final XyTree<T> empty() {
      return null;
    }

    /**
     * This will only be called to join non-null data-values; null is
     * implicitly the identity.
     */
    protected abstract T combine(T a, T b);

    public final XyTree<T> union(XyTree<T> t, XyTree<T> u) {
      if( t == null ) return u;
      if( u == null ) return t;
      return new XyTree<>(t, this, u);
    }
  }

  public static <T> Unioner<T> leftWinsJoin() {
    return new Unioner<T>() {
      @Override public T combine(T a, T b) { return a; }
    };
  }

  public static <E> Unioner<List<E>> concatJoin() {
    return new Unioner<List<E>>() {
      @Override public List<E> combine(List<E> a, List<E> b) {
        return TreeList.concat(a,b);
      }
    };
  }

  public interface Callback<T> {
    boolean recurseInto(AxisRect rect);
    void accept(T data);
  }

  /**
   * A tree is not thread safe until this has been called!
   *
   * (Even though it is <em>semantically</em> immutable).
   */
  public static <T> void resolveDeep(XyTree<T> t,
      Consumer<? super T> resolveData) {
    if( t != null ) {
      if( resolveData == null )
        resolveData = NOOP_DATA_RESOLVER;
      t.resolveDeep(resolveData);
    }
  }

  public static <T> void recurse(XyTree<? extends T> t, Point focus,
      Callback<T> callback) {
    if( t != null && callback.recurseInto(t) ) {
      t.resolve();
      if( t.a == null ) {
        recurse(t.b, focus, callback);
      } else if( t.b == null ) {
        recurse(t.a, focus, callback);
      } else if( t.splitter.isA(focus) ) {
        recurse(t.a, focus, callback);
        recurse(t.b, focus, callback);
      } else {
        recurse(t.b, focus, callback);
        recurse(t.a, focus, callback);
      }
      if( t.data != null )
        callback.accept(t.data);
    }
  }

  public static <T> boolean anyIntersection(XyTree<T> t, AxisRect target) {
    class IntersectionCallback implements Callback<T>  {
      boolean found;
      @Override
      public boolean recurseInto(AxisRect rect) {
        return !found && rect.intersects(target);
      }
      @Override
      public void accept(T data) {
        found = true;
      }
    }
    var callback = new IntersectionCallback();
    recurse(t, target.center(), callback);
    return callback.found;
  }

  // ------------------------------------------------------------------------

  private static final class SplitSpec {
    final int level;
    final double splitval;

    boolean isA(Point p) {
      return ((level & 1) != 0 ? p.x : p.y) >= splitval;
    }

    static SplitSpec create(AxisRect r) {
      var splitX = new SplitSpec(r.xmin(), r.xmax(), 1);
      var splitY = new SplitSpec(r.ymin(), r.ymax(), 0);
      return splitX.level > splitY.level ? splitX : splitY;
    }

    private SplitSpec(double min, double max, int levelbase) {
      int log2size;
      long splitBits;
      long minBits = Double.doubleToRawLongBits(min);
      long maxBits = Double.doubleToRawLongBits(max);

      // Remove negative zero, for consistency with Java's >=
      if( minBits == Long.MIN_VALUE ) { minBits = 0; }
      if( maxBits == Long.MIN_VALUE ) { maxBits = 0; }

      int diffBits = 64 - Long.numberOfLeadingZeros(minBits ^ maxBits);
      if( diffBits <= 52 ) {
        // Same signs and exponents. This is the overwhelmingly common
        // case (and the one where we need to use bit fiddling to do things
        // efficiently, which is why the other cases work on bits too).
        splitBits = (minBits & (-1L << diffBits)) +
            ((1L << 51) >> (52-diffBits));
        log2size = Math.getExponent(min);
        if( log2size < Double.MIN_EXPONENT ) {
          // Denormals have their mantissa shifted by one bit relative
          // to what getExponent reports
          log2size++;
        }
        log2size -= (52-diffBits);
      } else if( diffBits != 64 ) {
        // Same signs, different exponents
        splitBits = (minBits >= 0 ? maxBits : minBits) & (-1L << 52);
        log2size = Math.max(Math.getExponent(min), Math.getExponent(max))-1;
      } else {
        // Different signs (including 0.0 versus -0.0)
        splitBits = 0;
        log2size = 9999;
      }

      if( splitBits < 0 ) {
        // the bits computed are for the first double that belongs in the
        // box _away from_ 0. When we're comparing with >= we actually need
        // the last one that belongs in the box _closer to_ 0.
        // That is, the next _larger_ double, which is the next _smaller_
        // long.
        splitBits--;
        // if the split value was -0.0, this would roll over to a quiet
        // +NaN, but -0.0 has already been rewritten to +0.0.
        // (And even so, it would only happen for a one-point interval,
        // in which case it doesn't really matter what the comparison
        // in isA() produces anyway).
      }
      // Note: a clever JIT might optimize the above conditional to
      //    splitBits -= splitBits >>> 63
      // but that's hardly _readable_ and this code is not _that_ hot.

      splitval = Double.longBitsToDouble(splitBits);
      level = 2_0000_0 + 10 * log2size + levelbase;
    }
  }

  // ------------------------------------------------------------------------

  private final Unioner<T> unioner;
  private final SplitSpec splitter;
  private final int level;

  // These are only valid after calling resolve()
  private XyTree<T> a, b;
  private T data;

  /** Must only be accessed with the lock held (or when constructing) */
  private List<XyTree<T>> deferredOperands;

  private XyTree(Point p, T data) {
    super(p);
    unioner = null;
    splitter = SplitSpec.create(this);
    level = splitter.level;
    this.data = data;
  }

  private XyTree(AxisRect r, T data) {
    super(r);
    unioner = null;
    splitter = SplitSpec.create(this);
    level = splitter.level;
    this.data = data;
  }

  private XyTree(XyTree<T> p, Unioner<T> unioner, XyTree<T> q) {
    super(p,q);
    this.unioner = unioner;
    splitter = SplitSpec.create(this);
    level = splitter.level;
    deferredOperands = TreeList.concat(
        p.asOperands(unioner), q.asOperands(unioner));
  }

  private List<XyTree<T>> asOperands(Unioner<T> parentUnioner) {
    if( parentUnioner == unioner ) {
      synchronized(this) {
        if( deferredOperands != null )
          return deferredOperands;
      }
    }
    return Collections.singletonList(this);
  }

  /**
   * Once this has been called, the node object is truly immutable, and
   * it is okay to refer to {@link #a}, {@link #b}, {@link #data} fields
   * without further locking.
   */
  private void resolve() {
    synchronized( this ) {
      if( deferredOperands != null ) {
        for( var op : deferredOperands )
          processOperand(op);
        deferredOperands = null;
      }
    }
  }

  private void processOperand(XyTree<T> operand) {
    if( operand.level >= level ) {
      operand.resolve();
      if( operand.data != null ) {
        if( data == null )
          data = operand.data;
        else
          data = unioner.combine(data, operand.data);
      }
      a = unioner.union(a, operand.a);
      b = unioner.union(b, operand.b);
    } else if( splitter.isA(operand.center()) ) {
      a = unioner.union(a, operand);
    } else {
      b = unioner.union(b, operand);
    }
  }

  private Consumer<? super T> resolvedDeep;

  private void resolveDeep(Consumer<? super T> resolveData) {
    if( resolvedDeep != resolveData ) {
      resolve();
      resolveDeep(a, resolveData);
      resolveDeep(b, resolveData);
      if( data != null )
        resolveData.accept(data);
      resolvedDeep = resolveData;
    }
  }

  private static final Consumer<Object> NOOP_DATA_RESOLVER = blah -> {};

}
