package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.files.VectFile;
import net.makholm.henning.mapwarper.gui.projection.OrthoProjection;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.util.FrozenArray;

public class Teleporter {

  private final MapView mapView;

  private final VectFile file;
  private final Projection projection;
  private final ProjectionWorker translator;
  private final AxisRect visible;
  private final Tileset tiles;

  public void apply() {
    mapView.files.setActiveFile(file);
    mapView.setProjectionOnly(projection);
    mapView.setMainTiles(tiles);
    mapView.positionX = Math.round(visible.center().x
        - mapView.visibleArea.width()*0.5);
    mapView.positionY = Math.round(visible.center().y
        - mapView.visibleArea.height()*0.5);

    // Make sure the cached mouse position, visible area, and so forth
    // gets updated immediately.
    mapView.swing.refreshScene();
  }

  public Teleporter(MapView owner) {
    mapView = owner;
    file = mapView.files.activeFile();
    projection = mapView.projection;
    translator = mapView.translator();
    visible = new AxisRect(mapView.visibleArea);
    tiles = mapView.mainTiles;
  }

  /**
   * Ignore the current position and locate to the given position from scratch.
   */
  Teleporter(MapView mapView, VectFile file, AxisRect globalTarget) {
    this.mapView = mapView;
    this.file = file;
    tiles = mapView.tiles.tilesets.get("osm");

    long width = mapView.visibleArea.width();
    long height = mapView.visibleArea.height();
    // these inverse scales are in pixels per global unit.
    double invXscale = Math.max(256, width-20) / globalTarget.width();
    double invYscale = Math.max(256, height-20) / globalTarget.height();
    double invScale = Math.min(invXscale, invYscale);
    // convert to an actual pixsize while also rounding to a power of 1
    double scale = Math.scalb(1, -Math.getExponent(invScale));
    int naturalScale = Coords.zoom2pixsize(tiles.guiTargetZoom());
    scale = Math.max(scale, naturalScale);
    projection = OrthoProjection.ORTHO.withScaleAcross(scale);
    translator = projection.createWorker();

    var center = projection.projected2local(globalTarget.center());
    Vector diag = Vector.of(width, height);
    visible = new AxisRect(center.plus(0.5,diag), center.plus(-0.5,diag));
  }

  private Teleporter(Teleporter orig, VectFile newFile, Vector translateBy) {
    mapView = orig.mapView;
    file = newFile;
    tiles = orig.tiles;
    projection = orig.projection;
    translator = orig.translator;
    visible = orig.visible.translate(translateBy);
  }

  static Teleporter unzoom(MapView mapView) {
    VectFile active = mapView.files.activeFile();
    return unzoom(mapView, active);
  }

  private static Teleporter unzoom(MapView mapView, VectFile active) {
    AxisRect bbox = active.allShownNodes();
    if( bbox == null ) {
      return null;
    } else {
      return new Teleporter(mapView, active, bbox);
    }
  }

  Teleporter forOpening(VectFile file) {
    if( file == this.file )
      return this;
    else
      return teleport(file, visible.center(), true);
  }

  static Teleporter teleport(MapView mapView) {
    Teleporter t0 = new Teleporter(mapView);
    return t0.teleport(t0.file, mapView.mouseLocal, false);
  }

  private Teleporter teleport(VectFile file,
      Point center, boolean forOpen) {
    double bestScore = Double.NEGATIVE_INFINITY;
    SegmentChain bestChain = null;
    int bestIndex = -1;
    AxisRect bbox = null;

    for( var chain : file.content().chains() ) {
      FrozenArray<TrackNode> nodes = chain.nodes;
      for( int i=0; i<nodes.size(); i++ ) {
        TrackNode n = nodes.get(i);
        var p = translator.global2local(n);
        bbox = AxisRect.extend(bbox, p);
        double score;
        if( visible.contains(p) ) {
          score = 1/center.sqDist(p);
        } else if( bestScore >= 0 ) {
          continue;
        } else {
          score = -center.sqDist(p);
          if( !chain.isTrack() )
            score *= 4;
        }
        if( score > bestScore ) {
          bestScore = score;
          bestChain = chain;
          bestIndex = i;
        }
      }
    }
    if( bbox == null ) {
      if( forOpen )
        return new Teleporter(this, file, Vector.ZERO);
      else
        return null;
    }
    if( bbox.height() <= 10 && bbox.width() <= 10 )
      return unzoom(mapView, file);

    if( bestScore > 0 && forOpen )
      return new Teleporter(this, file, Vector.ZERO);

    Point target;
    Point near = translator.global2local(bestChain.nodes.get(bestIndex));
    if( forOpen || bestScore < 0 || bestChain.numNodes == 1 ) {
      target = near;
    } else {
      Point far;
      if( bestIndex == 0 )
        far = translator.global2local(bestChain.nodes.get(1));
      else if( bestIndex == bestChain.numNodes-1 )
        far = translator.global2local(bestChain.nodes.get(bestIndex-1));
      else {
        var f1 = translator.global2local(bestChain.nodes.get(bestIndex-1));
        var f2 = translator.global2local(bestChain.nodes.get(bestIndex+1));
        if( neighborness(near, center, f1) > neighborness(near, center, f2) )
          far = f1;
        else
          far = f2;
      }
      double dist = near.dist(far);
      target = far.interpolate( dist < 10 ? 0.5 : 10/dist, near);
    }

    double limit = 10 * Math.max(visible.width(), visible.height());
    if( center.dist(target) > limit )
      return unzoom(mapView, file);

    return new Teleporter(this, file, center.to(target));
  }

  static double neighborness(Point near, Point center, Point far) {
    return far.sqDist(near) / far.sqDist(center);
  }

}
