package net.makholm.henning.mapwarper.geometry;

import java.awt.geom.AffineTransform;
import java.util.List;

import net.makholm.henning.mapwarper.util.FrozenArray;
import net.makholm.henning.mapwarper.util.ListMapper;
import net.makholm.henning.mapwarper.util.TreeList;

public interface BezierChain {

  public Bezier firstCurveOrNull();
  public List<Bezier> curves();
  public Bezier lastCurveOrNull();

  default public BezierChain transform(AffineTransform aff, TransformHelper th) {
    return list2chain(ListMapper.map(curves(), b->b.transform(aff, th)),
        firstCurveOrNull() != null,
        lastCurveOrNull() != null);
  }

  default public BezierChain reverse() {
    var orig = curves();
    var reversed = new Bezier[orig.size()];
    for( int i = 0; i<reversed.length; i++ )
      reversed[i] = orig.get(reversed.length-1-i).reverse();
    return list2chain(FrozenArray.of(reversed),
        lastCurveOrNull() != null,
        firstCurveOrNull() != null);
  }

  public static BezierChain list2chain(List<Bezier> list,
      boolean hasFirst, boolean hasLast) {
    if( list.isEmpty() ) {
      return new BezierChain() {
        @Override public Bezier firstCurveOrNull() { return null; }
        @Override public List<Bezier> curves() { return List.of(); }
        @Override public Bezier lastCurveOrNull() { return null; }
      };
    } else if( list.size() == 1 && hasFirst && hasLast ) {
      return list.get(0);
    } else {
      return new BezierChain() {
        @Override
        public Bezier firstCurveOrNull() {
          return hasFirst ? list.get(0) : null;
        }
        @Override
        public List<Bezier> curves() { return list; }
        @Override
        public Bezier lastCurveOrNull() {
          return hasLast ? list.get(list.size()-1) : null;
        }
      };
    }
  }

  public static BezierChain list2chain(List<Bezier> list) {
    return list2chain(list, true, true);
  }

  /**
   * A {@code null} argument is allowed, and is different from an empty
   * chain in that it is invisible to the first/last properties, whereas
   * an empty but non-null chain prevents the first/last curve in the
   * other chain from being seen.
   */
  public static BezierChain concat(BezierChain c1, BezierChain c2) {
    if( c1 == null ) return c2;
    if( c2 == null ) return c1;
    return list2chain(TreeList.concat(c1.curves(), c2.curves()),
        c1.firstCurveOrNull() != null, c2.lastCurveOrNull() != null);
  }

}