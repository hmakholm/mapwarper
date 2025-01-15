package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.LineSeg;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.gui.overlays.ArrowOverlay;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.projection.CircleWarp;
import net.makholm.henning.mapwarper.gui.projection.OrthoProjection;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.gui.projection.QuickWarp;
import net.makholm.henning.mapwarper.gui.projection.TurnedProjection;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.VisibleTrackData;

public class QuickwarpTool extends ProjectionSwitchingTool {

  protected QuickwarpTool(Commands owner) {
    super(owner, "quickwarp", "Quick-warp");
  }

  @Override
  protected ToolResponse clickResponse(Point pos, int modifiers) {
    if( altHeld(modifiers) ) {
      ChainRef<?> segment = FindClosest.curve(
          activeFileContent().segmentTree.apply(translator()),
          ChainRef::data,
          5, pos, 2);
      if( segment != null && segment.chain().isTrack() ) {
        var curve = segment.chain().smoothed().get(segment.index());
        var curvature = curve.signedCurvatureAt(0.5);
        if( Math.abs(curvature) > 1e-6 ) {
          VisibleTrackData vdt = mapView().currentVisible.clone();
          vdt.setHighlight(new TrackHighlight(segment.chain(),
              segment.index(), segment.index()+1, 0x0066FF));
          vdt.freeze();
          return new ToolResponse() {
            @Override
            public VisibleTrackData previewTrackData() { return vdt; }

            @Override
            public void execute(ExecuteWhy why) {
              var point = curve.pointAt(0.5);
              var normal = curve.derivativeAt(0.5).normalize().turnRight();
              var center = point.plus(1/curvature, normal);
              var circle = new CircleWarp(center, point);
              var proj = mapView().projection.scaleAndSqueezeSimilarly(circle);
              if( curvature > 0 )
                proj = TurnedProjection.invert(proj);
              owner.mapView.setProjection(proj, pos);
              if( why != ExecuteWhy.SHIFT_PRESSED )
                owner.mapView.selectEditingTool();
            }
          };
        }
      }
    }

    if( !mapView().projection.base().isWarp() )
      return NO_RESPONSE;
    else if( translator().local2global(pos) instanceof PointWithNormal pwn ) {
      return why -> {
        var quickwarp = new QuickWarp(pwn, pwn.normal.turnLeft());
        var proj = mapView().projection.scaleAndSqueezeSimilarly(quickwarp);
        mapView().setProjection(proj);
        if( why != ExecuteWhy.SHIFT_PRESSED )
          owner.mapView.selectEditingTool();
      };
    } else {
      System.err.println(translator()+" didn't give us a PointWithNormal.");
      return NO_RESPONSE;
    }
  }

  @Override
  public ToolResponse dragResponse(Point start, Point end) {
    Projection orig = mapView().projection;

    Projection turner;
    LineSeg turnedDelta;
    if( orig.getSqueeze() < 1.1 ) {
      turner = OrthoProjection.ORTHO.withScaleAcross(orig.scaleAcross());
      turnedDelta = start.to(end);
    } else {
      turner = orig;
      Point turnedStart = mapView().projection.local2projected(start);
      Point turnedEnd = mapView().projection.local2projected(end);
      turnedDelta = turnedStart.to(turnedEnd);
    }
    if( turnedDelta.x < 0 && Math.abs(turnedDelta.y) < -turnedDelta.x ) {
      Point tmp = start;
      start = end;
      end = tmp;
    }

    var arrow = start.to(end);
    if( arrow.length() < 5 )
      return NO_RESPONSE;

    var overlay = new ArrowOverlay(arrow, 0x0066FF);
    return new ToolResponse() {
      @Override public VectorOverlay previewOverlay() { return overlay; };

      @Override
      public void execute(ExecuteWhy why) {
        var translator = owner.mapView.translator();
        var midLocal = arrow.a.interpolate(0.5, arrow.b);
        Point a = translator.local2global(arrow.a);
        Point b = translator.local2global(arrow.b);
        UnitVector dir = a.to(b).normalize();
        var quickwarp = new QuickWarp(a, dir);
        var proj = turner.scaleAndSqueezeSimilarly(quickwarp);
        if( proj.getSqueeze() < 5 )
          proj = proj.withSqueeze(5);
        owner.mapView.setProjection(proj, midLocal);
        if( why != ExecuteWhy.SHIFT_PRESSED )
          owner.mapView.selectEditingTool();
      }
    };
  }

}
