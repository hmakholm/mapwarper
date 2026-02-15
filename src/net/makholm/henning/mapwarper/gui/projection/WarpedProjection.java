package net.makholm.henning.mapwarper.gui.projection;

import java.awt.geom.AffineTransform;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.files.FSCache;
import net.makholm.henning.mapwarper.gui.files.VectFile;
import net.makholm.henning.mapwarper.gui.maprender.FallbackChain;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.gui.maprender.SupersamplingRenderer;
import net.makholm.henning.mapwarper.track.ChainClass;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackNode;

public final class WarpedProjection extends BaseProjection {

  public final Path sourcename0;
  public final SegmentChain track;
  final Set<FileContent> usedFiles;

  final SegmentChain.Smoothed curves;
  final double[] nodeLeftings;
  final double totalLength;

  private final WarpedProjection withoutSkips;
  private WarpedProjection withSkips;

  final Bezier pseudofirst;
  final Bezier pseudolast;

  record EasyPoint(int segment, double lefting, double downing,
      UnitVector tangent) {}

  final PointWithNormal[] nodesWithNormals;

  final LinkedHashMap<GlobalPoint, EasyPoint> easyPoints =
      new LinkedHashMap<>();
  /** These are all straight in warped coords <em>by construction</em>. */
  final LinkedHashSet<Bezier> easyCurves;

  @SuppressWarnings("serial")
  public static final class CannotWarp extends Exception {
    private CannotWarp(String why) {
      super(why);
    }
  }

  public static WarpedProjection create(VectFile source, FSCache cache,
      SegmentChain... preferredChains) throws CannotWarp {
    var track = findTrack(source, preferredChains);
    if( track.numSegments == 0 )
      throw new CannotWarp(source.shortname() +
          " has just a single point; nothing to warp along.");

    var usedFiles = new LinkedHashSet<FileContent>();
    var mainContent = source.content();
    usedFiles.add(mainContent);
    for( var p : mainContent.usebounds() )
      usedFiles.add(cache.getFile(p).content());
    return new WarpedProjection(null, source.path, usedFiles,
        track, track.smoothed.get());
  }

  private static SegmentChain findTrack(VectFile source,
      SegmentChain[] fallbacks) throws CannotWarp {
    var mainContent = source.content();
    for( var fallback : fallbacks ) {
      if( fallback != null && fallback.isTrack() &&
          mainContent.contains(fallback) )
        return fallback;
    }
    switch( mainContent.numTrackChains ) {
    case 1:
      return mainContent.uniqueChain(ChainClass.TRACK);
    case 0:
      throw new CannotWarp(source.shortname()+
          " contains no track to warp along.");
    default:
      throw new CannotWarp(source.shortname() +
          " contains "+mainContent.numTrackChains+" separate tracks.");
    }
  }

  WarpedProjection(WarpedProjection withoutSkips,
      Path sourcename0, Set<FileContent> usedFiles,
      SegmentChain track, SegmentChain.Smoothed curves) {
    this.sourcename0 = sourcename0;
    this.usedFiles = usedFiles;
    this.track = track;
    this.curves = curves;

    nodesWithNormals = new PointWithNormal[track.numNodes];
    nodeLeftings = new double[track.numNodes];
    double t = 0;
    UnitVector tangent = curves.get(0).dir1();
    GlobalPoint node = GlobalPoint.of(track.nodes.get(0));
    easyPoints.put(node, new EasyPoint(0, 0, 0, tangent));
    nodesWithNormals[0] = new PointWithNormal(node, tangent.turnRight());
    for( int i=0; i<track.numSegments; i++ ) {
      nodeLeftings[i] = t;
      var curve = curves.get(i);
      easyPoints.put(GlobalPoint.of(curve.p1),
          new EasyPoint(i, t, curves.segmentSlew(i), tangent));
      double length = curve.estimateLength();
      if( track.kinds.get(i) == SegKind.PASS )
        length /= 15;
      t += length;
      tangent = curve.dir4();
      easyPoints.put(GlobalPoint.of(curve.p4),
          new EasyPoint(i+1, t, curves.segmentSlew(i), tangent));
      node = GlobalPoint.of(track.nodes.get(i+1));
      easyPoints.put(node, new EasyPoint(i+1, t, curves.nodeSlew(i+1), tangent));
      nodesWithNormals[i+1] = new PointWithNormal(node, tangent.turnRight());
    }
    nodeLeftings[track.numSegments] = totalLength = t;

    easyCurves = new LinkedHashSet<>(curves);

    TrackNode n0 = track.nodes.get(0);
    pseudofirst = Bezier.line(n0, n0.plus(curves.get(0).dir1()));
    TrackNode n9 = track.nodes.last();
    pseudolast = Bezier.line(n9, n9.plus(curves.last().dir4()));

    if( withoutSkips != null ) {
      this.withoutSkips = withoutSkips;
      this.withSkips = this;
    } else {
      this.withoutSkips = this;
      if( WarpMargins.get(this).skips.isEmpty() )
        this.withSkips = this;
    }
  }

  @Override
  public boolean isWarp() {
    return true;
  }

  @Override
  public Affinoid getAffinoid() {
    var aff = new Affinoid();
    aff.useSkips = withSkips == this;
    return aff;
  }

  @Override
  public Projection apply(Affinoid aff) {
    aff.assertSqueezable();
    if( aff.useSkips )
      return aff.apply(skippingProjection());
    else
      return aff.apply(withoutSkips);
  }

  private WarpedProjection skippingProjection() {
    if( withSkips != this ) {
      synchronized( this ) {
        if( withSkips == null ) {
          withSkips = new WarpSkipper(this).convert();
        }
      }
    }
    return withSkips;
  }

  @Override
  public AffineTransform createAffine() {
    return null;
  }

  @Override
  protected ProjectionWorker createWorker(Projection owner,
      double xscale, double yscale) {
    return new WarpedProjectionWorker(owner, this, xscale, yscale);
  }

  @Override
  public Projection makeQuickwarp(Point local, boolean circle, Affinoid aff) {
    MinimalWarpWorker worker = new MinimalWarpWorker(this);
    double curvature = worker.curvatureAt(local.x);
    double downing = worker.projected2downing(local.y);
    PointWithNormal point = worker.pointWithNormal(downing);
    aff.squeezefactor(worker.speedAt(local.x));
    if( Math.abs(curvature) > 1.0e-6 ) {
      if( circle ) {
        PointWithNormal pwn = worker.normalAt(local.x);
        var got = new CircleWarp(pwn.pointOnNormal(1/curvature), pwn,
            curvature > 0);
        return got.apply(aff);
      } else {
        double relative = 1 - downing*curvature;
        aff.squeezefactor(Math.abs(relative));
      }
    }
    return new QuickWarp(point, point.normal.turnLeft()).apply(aff);
  }

  @Override
  public boolean suppressMainTileDownload(double squeeze) {
    return false;
  }

  @Override
  protected RenderFactory makeRenderFactory(LayerSpec spec,
      double xscale, double yscale) {
    FallbackChain chains = new FallbackChain(spec, xscale, yscale);
    var recipe = SupersamplingRenderer.prepareSupersampler(spec,
        xscale, yscale, chains.premiumChain, chains.standardChain);
    if( Toggles.LENS_MAP.setIn(spec.flags()) ) {
      return target -> new BaseWarpRenderer(this, spec,
          xscale, yscale, target, recipe);
    } else {
      var margins = WarpMargins.get(this);
      return target -> new MarginedWarpRenderer(this, spec,
          xscale, yscale, target, recipe, margins, chains.marginChain);
    }
  }

  public AxisRect shrinkToMargins(AxisRect r, double scaleAlong) {
    double xmin = Math.max(r.xmin(), 0);
    double xmax = Math.min(r.xmax(), totalLength);
    if( xmin >= xmax )
      return r;
    double minLeft = Double.POSITIVE_INFINITY;
    double maxRight = Double.NEGATIVE_INFINITY;
    WarpMargins m = WarpMargins.get(this);
    MinimalWarpWorker w = new MinimalWarpWorker(this);
    for( double t = xmin+scaleAlong/2; t < xmax; t += scaleAlong ) {
      minLeft = Math.min(minLeft, m.leftMargin(w, t));
      maxRight = Math.max(maxRight, m.rightMargin(w, t));
    }
    double ymin = Math.max(r.ymin(), minLeft);
    double ymax = Math.min(r.ymax(), maxRight);
    if( ymin >= ymax )
      return r;
    return new AxisRect(Point.at(xmin, ymin), Point.at(xmax, ymax));
  }


  @Override
  protected long longHashImpl() {
    // Just add everything together: FileContent never has the same kind of
    // hash as as a SegmentChain (or for that matter a Path) anyway.
    long h = track.longHash();
    if( sourcename0 != null )
      h += sourcename0.hashCode();
    for( var bound : usedFiles )
      h += bound.longHash();
    return h;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof WarpedProjection o &&
        Objects.equals(o.sourcename0, sourcename0) &&
        o.track.equals(track) &&
        o.usedFiles.equals(usedFiles);
  }

  @Override
  public String describe(Path currentFile) {
    return describe("warped", currentFile);
  }

  String describe(String basename, Path currentFile) {
    if( Objects.equals(currentFile, sourcename0) )
      return basename;
    else if( sourcename0 == null )
      return basename + "(anonymous)";
    else
      return basename + "("+sourcename0.getFileName()+")";
  }

}
