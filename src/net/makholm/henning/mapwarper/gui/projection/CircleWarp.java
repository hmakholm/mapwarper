package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.maprender.FallbackChain;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.gui.maprender.SupersamplingRenderer;
import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.util.MathUtil;
import net.makholm.henning.mapwarper.util.RootFinder;
import net.makholm.henning.mapwarper.util.TreeList;

public class CircleWarp extends BaseProjection {

  public final PointWithNormal center;
  public final Point focus;
  final double radius;
  final double degxscale;
  final double bearing0;

  public CircleWarp(Point center, Point focus) {
    var radiusVector = center.to(focus);
    this.center = new PointWithNormal(center, radiusVector.normalize());
    this.focus = focus;
    this.radius = radiusVector.length();
    this.degxscale = (180/Math.PI) / radius;
    this.bearing0 = radiusVector.bearing();
  }

  @Override
  public boolean isQuickwarp() {
    return true;
  }

  @Override
  public AxisRect maxUnzoom() {
    return new AxisRect(Point.at(-radius*4, 0),
        Point.at(radius*4, radius+1_000_000));
  }

  @Override
  public AffineTransform createAffine() {
    return null;
  }

  private PointWithNormal local2global(double x, double y) {
    var down = UnitVector.withBearing(bearing0 - x*degxscale);
    return new PointWithNormal(center.plus(y, down), down);
  }

  @Override
  public ProjectionWorker createWorker(Projection owningProjection,
      double xscale, double yscale) {
    return new ProjectionWorker() {
      @Override
      public Projection projection() { return owningProjection; }

      @Override
      public PointWithNormal local2global(Point p) {
        return CircleWarp.this.local2global(p.x*xscale, p.y*yscale);
      }

      @Override
      public Point global2localWithHint(Point global, Point nearbyLocal) {
        return global2local(global);
      }

      @Override
      public Point global2local(Point p) {
        LineSeg v = p.minus(center);
        double degrees = bearing0 - v.bearing();
        while( degrees < -180 ) degrees += 360;
        while( degrees > 180 ) degrees -= 360;
        return Point.at(degrees/(degxscale*xscale), v.length()/yscale);
      }

      @Override
      public List<Bezier> global2local(Bezier global) {
        Point l1 = global2local(global.p1);
        Point l4 = global2local(global.p4);
        double z1 = center.signedDistanceFromNormal(global.p1);
        double z4 = center.signedDistanceFromNormal(global.p4);
        if( !MathUtil.sameSign(z1, z4) && z1 != 0 && z4 != 0 ) {
          double tMid;
          if( z1 < 0 )
            tMid = new RootFinder(xscale) {
            @Override protected double f(double t) {
              return center.signedDistanceFromNormal(global.pointAt(t));
            }
          }.rootBetween(0, z1, 1, z4);
          else
            tMid = new RootFinder(xscale) {
            @Override protected double f(double t) {
              return -center.signedDistanceFromNormal(global.pointAt(t));
            }
          }.rootBetween(0, -z1, 1, -z4);
          var yy = center.signedDistanceAlongNormal(global.pointAt(tMid));
          if( yy < 0 ) {
            var x = Math.copySign(180/(degxscale*xscale), z1);
            var y = -yy/yscale;
            var split = global.split(tMid);
            return TreeList.concat(
                global2local(0, l1, split.front(), Point.at(x,y)),
                List.of(Bezier.cubic(Point.at(x,0), Point.at(x,-y),
                    Point.at(-x,-y), Point.at(-x,0))),
                global2local(0, Point.at(-x,y), split.back(), l4));
          }
        }
        return global2local(0, l1, global, l4);
      }

      private List<Bezier> global2local(int recLevel,
          Point l1, Bezier global, Point l4) {
        if( l1.sqDist(l4) < 8 || recLevel > 5 )
          return List.of(Bezier.line(l1, l4));
        var v1 = delta2local(global.v1, global.p1);
        var v4 = delta2local(global.v4, global.p4);
        Bezier candidate = Bezier.withVs(l1, v1, v4, l4);
        var got = candidate.pointAt(0.5);
        var want = global2local(global.pointAt(0.5));
        if( want.sqDist(got) < 8 || got.y < 0 )
          return List.of(candidate);
        else {
          var split = global.split(0.5);
          return TreeList.concat(
              global2local(recLevel+1, l1, split.front(), want),
              global2local(recLevel+1, want, split.back(), l4));
        }
      }

      private Vector delta2local(Vector global, Point gpoint) {
        var rvec = center.to(gpoint);
        var r = rvec.length();
        var n = rvec.normalize();
        var dy = global.dot(n) / yscale;
        var dx = global.dot(n.turnLeft()) * radius / (r * xscale);
        return Vector.of(dx, dy);
      }
    };
  }

  @Override
  public RenderFactory makeRenderFactory(LayerSpec spec,
      double xscale, double yscale) {
    FallbackChain fallback = new FallbackChain(spec, xscale, yscale);
    long supersamplingChain, fallbackChain;
    if( Toggles.LENS_MAP.setIn(spec.flags()) ) {
      supersamplingChain = fallback.lensChain();
      fallbackChain = 0;
    } else {
      supersamplingChain = fallback.supersampleMain(false);
      fallback.attemptFallbacks(0);
      fallbackChain = fallback.getChain();
    }

    var recipe = SupersamplingRenderer.prepareSupersampler(spec,
        xscale, yscale, supersamplingChain, fallbackChain);
    return target
        -> new SupersamplingRenderer(spec, xscale, yscale, target, recipe) {
          @Override
          protected PointWithNormal locateColumn(double x, double y) {
            return local2global(x, y);
          }
          @Override
          protected boolean renderColumn(int col, double xmid, int ymin,
              int ymax, double ybase) {
            while( ybase + ymin * yscale <= 0 && ymin <= ymax ) {
              target.givePixel(col, ymin, RGB.SINGULARITY);
              if( ymin == ymax ) return true;
              ymin++;
            }
            return super.renderColumn(col, xmid, ymin, ymax, ybase);
          }
        };
  }

  @Override
  public Projection makeQuickwarp(Point local, boolean tryCircle) {
    if( tryCircle )
      return this;
    var down = UnitVector.withBearing(bearing0 - local.x*degxscale);
    var right = down.turnLeft().scale(Math.max(100, Math.abs(local.y))/radius);
    return QuickWarp.ofAffine(center, right, down);
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this ||
        (obj instanceof CircleWarp o &&
            o.center.is(center) &&
            o.focus.is(focus));
  }

  @Override
  protected long longHashImpl() {
    long hash = 202501100230L;
    hash ^= Double.doubleToRawLongBits(center.x);
    hash = hashStep(hash);
    hash ^= Double.doubleToRawLongBits(center.y);
    hash = hashStep(hash);
    hash ^= Double.doubleToRawLongBits(focus.x);
    hash = hashStep(hash);
    hash ^= Double.doubleToRawLongBits(focus.y);
    return hash;
  }

}
