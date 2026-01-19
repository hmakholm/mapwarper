package net.makholm.henning.mapwarper.gui;

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.TransformHelper;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.gui.overlays.TextOverlay;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.track.ChainClass;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.track.VisibleTrackData;
import net.makholm.henning.mapwarper.util.MathUtil;

public final class MeasureTool extends Tool {

  private static final List<SegKind> SHOWKIND = List.of(SegKind.TRACK);

  protected MeasureTool(Commands owner) {
    super(owner, "measure", "Measure distance");
    toolCursor = loadCursor("crosshairCursor.png");
  }

  private SegmentChain measuringChain;

  @Override
  public void whenSelected() {
    measuringChain = null;
    super.whenSelected();
  }

  @Override
  public void escapeAction() {
    super.escapeAction();
    measuringChain = null; // just in case this tool is still active
  }

  @Override
  public int retouchDisplayFlags(int flags) {
    flags &= ~Toggles.CROSSHAIRS.bit();
    return flags;
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    if( measuringChain != null )
      return measuringChainResponse(pos, false);

    var found = FindClosest.curve(
        activeFileContent().segmentTree.apply(translator()),
        ChainRef::data, 150, pos, 4);
    if( found == null )
      return NO_RESPONSE;
    var chain = found.chain();
    var i = found.index();
    var vdt = mapView().currentVisible.clone();
    vdt.setHighlight(new TrackHighlight(chain, i, i+1, 0xFFFFFF));
    vdt.freeze();
    var label = mouseResponseLabel(chain, i, pos);

    return new ToolResponse() {
      @Override
      public VisibleTrackData previewTrackData() {
        return vdt;
      }
      @Override
      public VectorOverlay previewOverlay() {
        return label;
      }
      @Override
      public void execute(ExecuteWhy why) {
        measuringChain = null;
      }
    };
  }

  TextOverlay mouseResponseLabel(SegmentChain chain, int i, Point mouse) {
    String typetext = chain.kinds.get(i).desc;
    Bezier curve;
    if( chain.chainClass == ChainClass.TRACK ) {
      curve = chain.smoothed().get(i);
      if( curve.isExactlyALine() )
        typetext += ", straight at "+bearing(curve.v1);
      else if( curve.isPracticallyALine() )
        typetext += ", almost straight";
    } else {
      curve = Bezier.line(chain.nodes.get(i), chain.nodes.get(i+1));
    }
    String line2 = (i+1)+"/"+chain.numSegments+" \u2013 "+length(curve);
    if( curve.isPracticallyALine() ) {
      return placeLabel(curve, mouse,
          TextOverlay.of(owner.window, typetext, line2));
    } else {
      var t = estimateParameterNear(curve, mouse);
      var global = curve.pointAt(t);
      var local = translator().global2localWithHint(global, mouse);
      var radius = 1/curve.signedCurvatureAt(t);
      if( radius < 0 ) {
        radius = -radius;
        curve = curve.reverse();
        t = 1-t;
      }
      if( radius > curve.estimateLength()*10 &&
          radius > 10_000 * WebMercator.unitsPerMeter(global.y) ) {
        typetext += ", r > 10 km";
      } else {
        typetext += ", r = "+WebMercator.showlength(radius, global);
      }
      var angle = curve.v1.bearing() - curve.v4.bearing();
      line2 += String.format(Locale.ROOT, " \u2248 %.1f\u00B0",
          Math.abs((angle+180) % 360 - 180));
      return placeLabel(curve, t, local,
          TextOverlay.of(owner.window, typetext, line2));
    }
  }

  @Override
  public ToolResponse outsideWindowResponse() {
    if( measuringChain != null )
      return measuringChainResponse(Point.ORIGIN, false);
    else
      return NO_RESPONSE;
  }

  @Override
  public MouseAction drag(Point m1, int mod1) {
    int dragWhich = createOrReuseMeasuringChain(m1);
    return (m2, mod2) -> {
      var newNodes = new ArrayList<>(measuringChain.nodes);
      newNodes.set(dragWhich, local2node(m2));
      measuringChain = new SegmentChain(newNodes, SHOWKIND);
      return measuringChainResponse(m2, true);
    };
  }

  private int createOrReuseMeasuringChain(Point dragFrom) {
    int i = pickUpMeasuringChain(dragFrom);
    if( i >= 0 ) return i;
    var n = local2node(dragFrom);
    measuringChain = new SegmentChain(List.of(n,n), SHOWKIND);
    return 1;
  }

  private int pickUpMeasuringChain(Point dragFrom) {
    if( measuringChain != null ) {
      for( int i=measuringChain.numNodes-1; i>=0; i-- ) {
        TrackNode n = measuringChain.nodes.get(i);
        if( translator().global2local(n).dist(dragFrom) < 5 )
          return i;
      }
    }
    return -1;
  }

  private TrackNode local2node(Point local) {
    return GenericEditTool.global2node(translator().local2global(local));
  }

  private ToolResponse measuringChainResponse(Point mouse, boolean dragging) {
    var vdt = mapView().currentVisible.clone();
    vdt.setEditingChain(measuringChain);
    vdt.clearFlag(Toggles.CROSSHAIRS);
    vdt.setFlag(Toggles.MAIN_TRACK);
    vdt.freeze();

    Cursor cursor = dragging || pickUpMeasuringChain(mouse) < 0
        ? null : Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);

    var curve = Bezier.line(measuringChain.nodes.get(0),
        measuringChain.nodes.get(1));
    var label = placeLabel(curve, mouse,
        TextOverlay.of(owner.window, length(curve), bearing(curve.v1)));
    return new ToolResponse() {
      @Override
      public VisibleTrackData previewTrackData() {
        return vdt;
      }
      @Override
      public VectorOverlay previewOverlay() {
        return label;
      }
      @Override
      public Cursor cursor() {
        return cursor;
      }
      @Override
      public void execute(ExecuteWhy why) {
        if( !dragging )
          measuringChain = null;
      }
    };
  }

  private static String length(Bezier curve) {
    double len = curve.estimateLength();
    return WebMercator.showlength(len, curve.pointAt(0.5));
  }

  private static String bearing(Vector global) {
    return String.format(Locale.ROOT, "%.1f\u00B0", global.bearing());
  }

  private double[] PARAMETERS = {0.5, 0, 1, -1};

  private TextOverlay placeLabel(Bezier curve, Point m, TextOverlay label) {
    var viewport = new AxisRect(mapView().visibleArea);
    var translator = translator();
    for( double t: PARAMETERS ) {
      if( t < 0 )
        t = estimateParameterNear(curve, m);
      var gmid = curve.pointAt(t);
      var lmid = translator.global2localWithHint(gmid, m);
      if( viewport.contains(lmid) )
        return placeLabel(curve, t, lmid, label);
    }
    // fall back to letting it follow the mouse
    return label.at(m);
  }

  private TextOverlay placeLabel(Bezier curve, double t, Point local,
      TextOverlay label) {
    label = label.at(local);
    var tangent = new TransformHelper().applyDelta(
        translator().createDifferential(local),
        curve.derivativeAt(t)).normalize();
    // We now want to put the box on the left side of the tangent
    if( tangent.x > 0.01 ) label = label.moveUp();
    if( tangent.y < -0.01 ) label = label.moveLeft();
    return label;
  }

  private double estimateParameterNear(Bezier curve, Point local) {
    var l1 = translator().global2localWithHint(curve.p1, local);
    var l4 = translator().global2localWithHint(curve.p4, local);
    var line = l1.to(l4);
    return MathUtil.clamp(0, line.dot(l1.to(local)) / line.sqnorm(), 1);
  }

}