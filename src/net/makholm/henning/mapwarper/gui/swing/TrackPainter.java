package net.makholm.henning.mapwarper.gui.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.gui.MapView;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.track.ChainClass;
import net.makholm.henning.mapwarper.track.LocalSegmentChain;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.VisibleTrackData;
import net.makholm.henning.mapwarper.util.FrozenArray;
import net.makholm.henning.mapwarper.util.LongHashed;
import net.makholm.henning.mapwarper.util.TreeList;

public final class TrackPainter extends LongHashed {

  private final Projection projection;
  private final ProjectionWorker translator;
  private final VisibleTrackData trackdata;

  private static final int linewidth = 2;
  private static final BasicStroke BUTT_STROKE =
      new BasicStroke(linewidth,
          BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
  private static final BasicStroke ROUND_STROKE =
      new BasicStroke(linewidth,
          BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
  private static final BasicStroke DASHED_STROKE =
      new BasicStroke(linewidth,
          BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
          10.0f,
          new float[] { 10.0f }, 0);

  public TrackPainter(MapView logic, VisibleTrackData trackdata) {
    projection = logic.projection;
    translator = logic.translator();
    this.trackdata = trackdata;
  }

  @Override
  protected long longHashImpl() {
    long hash = projection.longHash();
    hash = hashStep(hash);
    hash += trackdata.longHash();
    return hash;
  }

  @Override
  public boolean equals(Object o) {
    return o == this || (
        o instanceof TrackPainter other &&
        other.longHash() == longHash() &&
        other.projection.equals(projection) &&
        other.trackdata.equals(trackdata));
  }

  // -------------------------------------------------------------------------

  private Graphics2D g;

  public void paint(Graphics2D g, AxisRect paintBounds) {
    this.g = g;

    if( trackdata.hasFlag(Toggles.SHOW_LABELS) ) {
      trackdata.showTrackChainsIn().forEach((path, content) -> {
        String name = path.getFileName().toString();
        for( var chain : content.chains() ) {
          if( chain.isTrack() )
            showTrackLabel(name, chain);
        }
      });
    }

    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
        RenderingHints.VALUE_STROKE_PURE);

    SegmentChain editingChain = trackdata.editingChain();
    ChainClass editingClass =
        editingChain == null ? null : editingChain.chainClass;

    var showAuxCrosshairsIn = new ArrayList<SegmentChain>();

    drawHighlight(trackdata.highlight());

    if( trackdata.hasFlag(Toggles.STRONG_FOREIGN_TRACK_CHAINS) )
      linestyle(SegKind.TRACK.rgb, BUTT_STROKE);
    else
      linestyle(RGB.OTHER_TRACK, BUTT_STROKE);
    for( var show: trackdata.showTrackChainsIn().values() )
      for( var chain: show.chains() )
        if( chain.isTrack() ) {
          g.draw(chain2Path(chain, 0, chain.numSegments));
        }

    for( var bounds: trackdata.showBoundChainsIn() )
      for( var chain: bounds.chains() )
        if( chain.isBound() ) {
          drawBoundChain(chain, RGB.OTHER_BOUND);
          if( editingClass == ChainClass.BOUND )
            showAuxCrosshairsIn.add(chain);
        }

    if( trackdata.hasFlag(Toggles.CURVATURE) ) {
      Iterable<SegmentChain> which = trackdata.currentFileChains();
      if( editingClass == ChainClass.TRACK )
        which = Collections.singletonList(editingChain);
      for( var chain : which ) {
        if( chain.isTrack() )
          drawCurvature(chain);
      }
    }

    for( var chain : showAuxCrosshairsIn )
      drawCrosshairs(chain, 3, 0x81DC70);

    if( trackdata.hasFlag(Toggles.MAIN_TRACK) ) {
      var trackChains = new ArrayList<SegmentChain>();
      for( var chain : trackdata.currentFileChains() ) {
        if( chain.isTrack() )
          trackChains.add(chain);
        else
          drawBoundChain(chain, SegKind.BOUND.rgb);
      }
      for( var chain : trackChains )
        drawTrackChain(chain);
    }

    for( var chain : trackdata.currentFileChains() ) {
      if( chain == editingChain ) {
        // we will draw this later
      } else if( editingChain == null )
        drawCrosshairs(chain, 64, 0x00BBCC);
      else if( chain.chainClass == editingClass )
        drawCrosshairs(chain, 32, 0xD2D2D2);
    }

    if( editingChain != null ) {
      if( trackdata.hasFlag(Toggles.MAIN_TRACK) ) {
        if( editingChain.isTrack() )
          drawTrackChain(editingChain);
        else
          drawBoundChain(editingChain, SegKind.BOUND.rgb);
      }
      drawCrosshairs(editingChain, 64, 0xFFFF00);
    }

    this.g = null;
  }

  private static final Font labelFont =
      SwingUtils.getANiceDefaultFont().deriveFont(11.0f);

  private void showTrackLabel(String filename, SegmentChain chain) {
    if( chain.numSegments < 1 ) return;
    String[] text = new String[] { filename, chain.numSegments+" segments" };
    g.setFont(labelFont);
    FontMetrics metrics = g.getFontMetrics();

    int xmargin = 2;
    int ymargin = 1;
    int width = 0;
    for( var s : text )
      width = Math.max(width, metrics.stringWidth(s));
    width += 2*xmargin;
    int height = 2*ymargin+text.length*metrics.getHeight();

    var local = chain.localize(translator);
    for( var curve : new Bezier[] {
        local.curves.get(0).get(0),
        local.curves.last().get(local.curves.last().size()-1).reverse() }) {
      var left = Math.round( curve.v1.x > 0 ? curve.p1.x - 3 - width : curve.p1.x + 2);
      var top = Math.round( curve.v1.y > 0 ? curve.p1.y - 3 - height : curve.p1.y + 2);
      var gg = (Graphics2D)g.create();
      gg.translate(left, top);
      gg.setColor(new Color(0xDDEECCAA, true));
      gg.fill(new Rectangle2D.Double(0, 0, width, height));
      var x = xmargin;
      var y = ymargin+metrics.getAscent();
      gg.setColor(Color.BLACK);
      for( var s : text ) {
        gg.drawString(s, x, y);
        y += metrics.getHeight();
      }
    }
  }

  private static final float[] glowWidths = { 16, 13, 10 };
  private static final float wormWidth = 7;

  private void drawHighlight(TrackHighlight hl) {
    if( hl == null ) return;
    Shape toDraw;
    if( hl.fromNode >= hl.toNode ) {
      PointWithNormal pn = hl.chain.localize(translator).nodes.get(hl.fromNode);
      double halfMinilength = linewidth;
      startPath(pn.pointOnNormal(-halfMinilength));
      lineTo(pn.pointOnNormal(halfMinilength));
      toDraw = endPath();
    } else {
      toDraw = chain2Path(hl.chain, hl.fromNode, hl.toNode);
    }
    g.setColor(new Color(hl.rgb, true));
    for( float w: glowWidths ) {
      g.setStroke(new BasicStroke(linewidth * w,
          BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.draw(toDraw);
    }
    g.setColor(new Color(hl.rgb));
    g.setStroke(new BasicStroke(linewidth * wormWidth,
        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(toDraw);
    g.setColor(new Color(0xAAAAAA));
    g.setStroke(new BasicStroke(linewidth * (wormWidth-2),
        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(toDraw);
  }

  private void drawTrackChain(SegmentChain chain) {
    if( chain.numSegments == 0 ) return;
    LocalSegmentChain localChain = chain.localize(translator);
    if( chain.numSegments > 0 )
      drawArrowhead(localChain);
    SegKind drawingKind = null;
    for( int i = 0; i<chain.numSegments; i++ ) {
      SegKind kind = chain.kinds.get(i);
      if( kind != drawingKind ) {
        if( i != 0 ) strokePath();
        drawingKind = kind;
        linestyle(kind.rgb, ROUND_STROKE);
        startPath(localChain.nodes.get(i));
      }
      for( var c : localChain.curves.get(i) ) append(c);
    }
    strokePath();
  }

  private void drawArrowhead(LocalSegmentChain lsc) {
    linestyle(SegKind.TRACK.rgb, BUTT_STROKE);

    Bezier first = lsc.curves.get(0).get(0);
    var across = first.dir1().turnRight().scale(linewidth);
    startPath(first.p1.plus(5, across));
    lineTo(first.p1.plus(-5, across));

    PointWithNormal pLast = lsc.nodes.get(lsc.global.numSegments);
    across = pLast.normal.scale(linewidth);
    var along = across.turnLeft();
    moveTo(pLast.plus(-9, along).plus(-4, across));
    lineTo(pLast);
    lineTo(pLast.plus(-9, along).plus(4, across));
    strokePath();
  }

  private static final int CURVATURE_SAMPLES = 7;

  private void drawCurvature(SegmentChain chain) {
    FrozenArray<Bezier> globalCurves = chain.smoothed.get();
    FrozenArray<List<Bezier>> localCurves = chain.localize(translator).curves;
    g.setStroke(BUTT_STROKE);
    g.setColor(new Color(0x55FF0000, true));
    double stdradius = 400 * WebMercator.unitsPerMeter(chain.nodes.get(0).y);
    double gscale = projection.scaleAcross();
    for( int j=0; j<globalCurves.size(); j++ ) {
      Bezier curve = globalCurves.get(j);
      if( curve.isPracticallyALine() ) continue;
      Point[] toShow = new Point[CURVATURE_SAMPLES];
      boolean anySignificant = false;
      for( int i=0; i<CURVATURE_SAMPLES; i++ ) {
        double t = i / (CURVATURE_SAMPLES-1.0);
        double c = 256 * stdradius * curve.signedCurvatureAt(t);
        if( Math.abs(c) > 1 ) anySignificant = true;
        toShow[i] = curve.pointAt(t).plus(c*gscale,
            curve.derivativeAt(t).normalize().turnLeft());
      }
      if( anySignificant ) {
        Point l1 = translator.global2local(curve.p1);
        Point l4 = translator.global2local(curve.p4);
        startPath(l1);
        localCurves.get(j).forEach(this::append);
        Point[] locals = new Point[CURVATURE_SAMPLES];
        for( int i=0; i<CURVATURE_SAMPLES; i++ ) {
          locals[i] = translator.global2localWithHint(toShow[i],
              i < CURVATURE_SAMPLES/2 ? l1 : l4);
        }
        append(Bezier.through(locals[6], locals[5], locals[4], locals[3]));
        append(Bezier.through(locals[3], locals[2], locals[1], locals[0]));
        g.fill(endPath());
      }
    }
  }

  private void drawBoundChain(SegmentChain chain, int color) {
    if( chain.numSegments <= 0 ) return;
    List<Bezier> outline = List.of();
    for( var cs : chain.localize(translator).curves )
      for( var c : cs ) {
        outline = TreeList.concat(
            c.reverse().offset(10),
            outline,
            List.of(c));
      }
    startPath(outline.get(0).p1);
    outline.forEach(this::append);
    currentPath.closePath();
    g.setColor(new Color(0x50_CCEE00, true));
    g.fill(endPath());
    g.setColor(new Color(color));
    int start = 0;
    for( int end = 1; end < chain.numNodes; end++ ) {
      if( end == chain.numNodes-1 ||
          chain.kinds.get(end) != chain.kinds.get(end-1) ) {
        switch( chain.kinds.get(end-1) ) {
        case LBOUND:
          g.setStroke(DASHED_STROKE);
          break;
        default:
          g.setStroke(BUTT_STROKE);
          break;
        }
        g.draw(chain2Path(chain, start, end));
        start = end;
      }
    }
  }

  private Path2D chain2Path(SegmentChain chain, int fromNode, int toNode) {
    LocalSegmentChain localChain = chain.localize(translator);
    startPath();
    moveTo(localChain.nodes.get(fromNode));
    for( int i = fromNode; i < toNode; i++ ) {
      for( var c : localChain.curves.get(i) ) append(c);
      lineTo(localChain.nodes.get(i+1));
    }
    return endPath();
  }

  private void drawCrosshairs(SegmentChain chain, int maxsize, int rgb) {
    if( !trackdata.hasFlag(Toggles.CROSSHAIRS) ) return;
    g.setColor(new Color(rgb));
    var localCurves = chain.localize(translator);
    var nodes = localCurves.nodes;
    boolean mainChain = chain == trackdata.editingChain();

    int max = nodes.size()-1;
    for( int i=0; i<=max; i++ ) {
      PointWithNormal curPoint = nodes.get(i);
      double dist = 1000;
      if( i > 0   ) dist = Math.min(dist, curPoint.dist(nodes.get(i-1)));
      if( i < max ) dist = Math.min(dist, curPoint.dist(nodes.get(i+1)));
      double size = dist * 1.5;
      size = maxsize - maxsize/(1+(size/maxsize));
      if( mainChain &&
          size > 8*linewidth &&
          chain.nodes.get(i).locksDirection() )
        drawDirectedCrosshair(curPoint, size);
      else
        drawOneCrosshair(curPoint, size, localCurves.curves, i);
    }
  }

  /**
   * Pretend we have a track in this direction at the ends, which would
   * not suppress any of the crosshair arms.
   */
  private static final UnitVector DIAG = Vector.of(1,1).normalize();

  private void drawOneCrosshair(PointWithNormal p, double size,
      FrozenArray<List<Bezier>> curves, int index) {
    int W = linewidth;
    long x = (long)(Math.round(p.x - W*0.5));
    long y = (long)(Math.round(p.y - W*0.5));
    int arm = Math.max(W, (int)(size/2));
    int gap = arm * 4/10;
    if( gap > W * 2 ) {
      drawOuterCrosshair(x, y, gap, arm, curves, index);
      if( gap < W * 4 )
        return;
    } else if( arm > W ) {
      drawOuterCrosshair(x, y, W, arm, curves, index);
    }
    fillRect(x-W, y, 3*W, W);
    fillRect(x, y-W, W, 3*W);
  }

  public void drawOuterCrosshair(long x, long y, int gap, int arm,
      FrozenArray<List<Bezier>> curves, int index) {
    // Omit the outer arms if they would clobber parts of the track.
    UnitVector dIn = DIAG, dOut = DIAG;
    if( index > 0 ) {
      var cl = curves.get(index-1); dIn = cl.get(cl.size()-1).dir4();
    }
    if( index < curves.size() )
      dOut = curves.get(index).get(0).dir1();
    double cosl = 0.95;
    int W = linewidth;
    if( dIn.x<cosl && dOut.x>-cosl ) fillRect(x-arm,   y, arm-gap, W);
    if( dIn.x>-cosl && dOut.x<cosl ) fillRect(x+W+gap, y, arm-gap, W);
    if( dIn.y<cosl && dOut.y>-cosl ) fillRect(x, y-arm,   W, arm-gap);
    if( dIn.y>-cosl && dOut.y<cosl ) fillRect(x, y+W+gap, W, arm-gap);
  }

  private void fillRect(long x, long y, int width, int height) {
    g.fill(new Rectangle2D.Double(x, y, width, height));
  }

  private void drawDirectedCrosshair(PointWithNormal p, double size) {
    var trans = new AffineTransform(size, 0, 0, size, p.x, p.y);
    trans.rotate(p.normal.y, -p.normal.x);

    Path2D.Double arrow = new Path2D.Double();
    arrow.moveTo(-0.3, 0.2);
    arrow.lineTo(0.3, 0.2);
    // arrow.lineTo(0.5, 0);
    arrow.moveTo(0.3, -0.2);
    arrow.lineTo(-0.3, -0.2);
    arrow.moveTo(0, -0.5);
    arrow.lineTo(0, -0.1);
    arrow.moveTo(0, 0.1);
    arrow.lineTo(0, 0.5);
    arrow.transform(trans);
    g.draw(arrow);
  }

  private void linestyle(int rgb, Stroke stroke) {
    g.setColor(new Color(rgb));
    g.setStroke(stroke);
  }

  private Path2D.Double currentPath;
  private Point currentPoint;


  private void startPath(Point p) {
    currentPath = new Path2D.Double();
    currentPath.moveTo(p.x, p.y);
    currentPoint = p;
  }

  private void startPath() {
    currentPath = new Path2D.Double();
    currentPoint = null;
  }

  private void moveTo(Point p) {
    if( currentPoint != null && currentPoint.is(p) ) {
      // nothing to do
    } else {
      currentPath.moveTo(p.x, p.y);
      currentPoint = p;
    }
  }

  private void lineTo(Point p) {
    if( currentPoint == null ) {
      moveTo(p);
    } else if( currentPoint.is(p) ) {
      // nothing to do
    } else {
      currentPath.lineTo(p.x, p.y);
      currentPoint = p;
    }
  }

  private void append(Bezier c) {
    lineTo(c.p1);
    if( c.isPracticallyALine() )
      lineTo(c.p4);
    else {
      currentPath.curveTo(
          c.p2.x, c.p2.y,
          c.p3.x, c.p3.y,
          c.p4.x, c.p4.y);
      currentPoint = c.p4;

    }
  }

  private void strokePath() {
    g.draw(endPath());
  }

  private Path2D.Double endPath() {
    var result = currentPath;
    currentPath = null;
    return result;
  }

}
