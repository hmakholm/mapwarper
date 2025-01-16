package net.makholm.henning.mapwarper.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.files.FSCache;
import net.makholm.henning.mapwarper.gui.files.FilePane;
import net.makholm.henning.mapwarper.gui.files.VectFile;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.overlays.BoxOverlay;
import net.makholm.henning.mapwarper.gui.projection.OrthoProjection;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.gui.projection.TurnedProjection;
import net.makholm.henning.mapwarper.gui.projection.WarpedProjection;
import net.makholm.henning.mapwarper.gui.projection.WarpedProjection.CannotWarp;
import net.makholm.henning.mapwarper.gui.swing.GuiMain;
import net.makholm.henning.mapwarper.gui.swing.PokeReceiver;
import net.makholm.henning.mapwarper.gui.swing.SwingMapView;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.tiles.OpenStreetMap;
import net.makholm.henning.mapwarper.tiles.OpenTopoMap;
import net.makholm.henning.mapwarper.tiles.TileContext;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.track.VisibleTrackData;
import net.makholm.henning.mapwarper.util.MathUtil;
import net.makholm.henning.mapwarper.util.MutableLongRect;
import net.makholm.henning.mapwarper.util.PokePublisher;
import net.makholm.henning.mapwarper.util.XyTree;

/**
 * UI logic for the main slippy map view that <em>doesn't<em> feel like its
 * structure will depend heavily on the framework goes here.
 *
 * There's a 1:1 correspondence between this and {@link SwingMapView}.
 *
 * Except otherwise noted, everything here happens in the UI thread.
 */
public class MapView {

  public final GuiMain window;
  public final SwingMapView swing;
  public final FilePane files;
  public final TileContext tiles;

  public final UndoList undoList = new UndoList(this);

  // Data that defines what _should_ be shown on the screen.
  // SwingMapView will read this before making any decisions.

  public Projection projection = OrthoProjection.ORTHO;
  public long positionX, positionY;

  public Tileset mainTiles;
  public final Tileset fallbackTiles;

  public Tool currentTool;

  public SegmentChain editingChain;
  public VisibleTrackData currentVisible = new VisibleTrackData().freeze();

  public Tileset lensTiles;
  public int lensZoom;
  public BoxOverlay lensRect;

  public boolean disableTempProjectionsOnShift;

  public SegmentChain clipboard;

  // Informational fields that get updated by the SwingMapView when they
  // change for reference for the UI actions. Changing them will not change
  // anything, though.

  public final MutableLongRect visibleArea = new MutableLongRect();

  public Point mouseGlobal = Point.at(574339431, 336098093);
  public Point mouseLocal = mouseGlobal;

  public final PokeReceiver refreshTrigger;
  private final PokeReceiver contentChangeListener;

  // -------------------------------------------------------------------------

  public MapView(GuiMain window,
      FSCache files, String filearg,
      TileContext tiles) {
    this.window = window;
    this.tiles = tiles;

    int pixsize = Coords.zoom2pixsize(16);
    this.projection = OrthoProjection.ORTHO.withScaleAcross(pixsize);

    this.files = new FilePane(this, files, filearg);

    setMainTiles(tiles.tilesets.get("osm"));
    this.fallbackTiles = tiles.tilesets.get("osm");

    this.swing = new SwingMapView(this);

    this.refreshTrigger = new PokeReceiver("deferred refresh MapView",
        swing::refreshScene);
    this.contentChangeListener = new PokeReceiver("content change listener",
        refreshTrigger);

    refreshTrigger.setSources(Arrays.asList(this.files.activeFilePokes));
  }

  void setMainTiles(Tileset tiles) {
    if( mainTiles != tiles ) {
      mainTiles = tiles;
      toggleState &= ~Toggles.DARKEN_MAP.bit();
      if( tiles instanceof OpenStreetMap || tiles instanceof OpenTopoMap )
        toggleState |= Toggles.DARKEN_MAP.bit();

      cancelLens();
      lensTiles = tiles;
    }
  }

  /**
   * The standard case for switching projections preserves the
   * global coordinates of the point the mouse points at.
   */
  public void setProjection(Projection newProj) {
    if( newProj.equals(projection) ) return;

    Point newLocal = setProjection(newProj, mouseLocal);

    swing.refreshScene();
    mouseLocal = newLocal;
    swing.mousePositionAdjusted();
  }

  public Point setProjection(Projection newProj, Point localFixpoint) {
    if( newProj.equals(projection) ) return localFixpoint;

    long offsetX = (long)Math.floor(localFixpoint.x) - positionX;
    long offsetY = (long)Math.floor(localFixpoint.y) - positionY;
    Point newLocal;
    if( newProj.base().equals(projection.base()) ) {
      Point projected = projection.local2projected(localFixpoint);
      setProjectionOnly(newProj);
      newLocal = projection.projected2local(projected);
    } else {
      Point global = translator().local2global(localFixpoint);
      setProjectionOnly(newProj);
      newLocal = translator().global2local(global);
    }
    positionX = (long)Math.floor(newLocal.x) - offsetX;
    positionY = (long)Math.floor(newLocal.y) - offsetY;
    return newLocal;
  }

  /**
   * The <em>caller</em> is responsible for setting {@link #positionX} and
   * {@link #positionY} right after this call.
   *
   * This should be the only place {@link #projection} is set!
   */
  public void setProjectionOnly(Projection newProj) {
    ProjectionWorker oldTranslator = translator();
    Projection oldProj = projection;
    projection = newProj;

    if( lensRect != null ) {
      // Preserve the midpoint and _projected_ size of the
      // lens area, provided that the direction at the midpoint doesn't
      // change by much.
      PointWithNormal centerGlobalOld =
          oldTranslator.local2global(lensRect.box.center());
      Point newCenterLocal = translator().global2local(centerGlobalOld);
      PointWithNormal centerGlobalNew = translator().local2global(newCenterLocal);
      if( Math.abs(centerGlobalOld.normal.dot(centerGlobalNew.normal)) < 0.9 ) {
        // That's too much of a turn for it to feel meaningful to preserve the lens
        lensRect = null;
      } else {
        var projected = lensRect.box.transform(oldProj::local2projected);
        var oldCP = projected.center();
        var newCP = newProj.local2projected(newCenterLocal);
        lensRect = LensTool.createBox(projected.transform(
            p -> newProj.projected2local(p.plus(newCP.minus(oldCP)))));
      }
    }

    if( !newProj.base().equals(oldProj.base()) ) {
      disableTempProjectionsOnShift = true;
      if( oldProj.base().isWarp() || newProj.base().isWarp() )
        files.updateViewEventually();
    }
  }

  boolean setLens(BoxOverlay box) {
    if( box.box.xmin() >= visibleArea.left &&
        box.box.xmax() <= visibleArea.right &&
        box.box.ymin() >= visibleArea.top &&
        box.box.ymax() <= visibleArea.bottom ) {
      lensRect = box;
      lensZoom = lensTiles.guiTargetZoom();
      return true;
    } else {
      return false;
    }
  }

  void cancelLens() {
    if( lensRect != null ) {
      swing.repaintFor(lensRect);
      lensRect = null;
    }
  }

  private final DoubleSupplier globalWindowDiagonal = () -> {
    ProjectionWorker pw = translator();
    var va = visibleArea;
    Point nw = pw.local2global(Point.at(va.left, va.top));
    Point ne = pw.local2global(Point.at(va.right, va.top));
    Point sw = pw.local2global(Point.at(va.left, va.bottom));
    Point se = pw.local2global(Point.at(va.right, va.bottom));
    return Math.max(nw.to(se).length(), ne.to(sw).length());
  };

  public final LayerSpec dynamicMapLayerSpec = new LayerSpec() {
    @Override public Projection projection() { return projection; }
    @Override public Tileset mainTiles() { return mainTiles; }
    @Override public int targetZoom() { return mainTiles.guiTargetZoom(); }
    @Override public Tileset fallbackTiles() { return fallbackTiles; }
    @Override public DoubleSupplier windowDiagonal() { return globalWindowDiagonal; }

    @Override public int flags() {
      int flags = toggleState;
      if( currentTool instanceof ExploreTool )
        flags &= ~Toggles.DARKEN_MAP.bit();
      return flags & Toggles.MAP_MASK;
    }
  };

  public final LayerSpec dynamicLensSpec = new LayerSpec() {
    @Override public Projection projection() { return projection; }
    @Override public Tileset mainTiles() { return lensTiles; }
    @Override public int targetZoom() { return lensZoom; }
    @Override public Tileset fallbackTiles() { return lensTiles; }
    @Override public DoubleSupplier windowDiagonal() { return globalWindowDiagonal; }

    @Override public int flags() {
      int flags = toggleState;
      flags |= Toggles.SUPERSAMPLE.bit();
      flags |= Toggles.LENS_MAP.bit();
      return flags & Toggles.MAP_MASK;
    }
  };

  // -------------------------------------------------------------------------

  public void setInitialPosition() {
    VectFile active = files.activeFile();
    XyTree<ChainRef<TrackNode>> nodes = active.allShownNodes();
    if( nodes == null ) {
      var joiner = XyTree.<ChainRef<TrackNode>>leftWinsJoin();
      for( Path sibling : files.siblingsOf(active) ) {
        VectFile vf = files.cache.getFile(sibling);
        nodes = joiner.union(nodes, vf.content().nodeTree.get());
      }
    }
    AxisRect bbox = nodes;
    if( bbox == null ) {
      MutableLongRect copenhagen = new MutableLongRect();
      copenhagen.left = 574130516;
      copenhagen.right = 574431404;
      copenhagen.top = 335858916;
      copenhagen.bottom = 336208754;
      bbox = new AxisRect(copenhagen);
    }
    new Teleporter(this, active, bbox).apply();
  }

  public void switchToFile(VectFile newActive) {
    new Teleporter(this).forOpening(newActive).apply();
  }

  private void teleport(Teleporter dest) {
    if( dest == null ) {
      window.showErrorBox("The current file is empty, "
          + "so there is nowhere in particular to go.");
    } else {
      dest.apply();
    }
  }

  void unzoomCommand() {
    teleport(Teleporter.unzoom(this));
  }

  void teleportCommand() {
    teleport(Teleporter.teleport(this));
  }

  void refreshWarpCommand() {
    List<String> problems = files.saveAll();
    for( String problem : problems ) {
      System.err.println("Ignoring save problem: "+problem);
    }

    if( projection.base() instanceof WarpedProjection wp ) {
      VectFile vf;
      if( wp.sourcename0 != null )
        vf = files.cache.getFile(wp.sourcename0);
      else
        vf = files.activeFile();
      try {
        var newWarp = new WarpedProjection(vf, files.cache);
        var proj = projection.scaleAndSqueezeSimilarly(newWarp);
        setProjection(proj);
      } catch( CannotWarp e ) {
        // Hmm, showing a box in this case might be too invasive.
        // Let's just beep.
        SwingUtils.beep();
        System.err.println("Cannot refresh warp: "+e);
      }
    }
  }

  void escapePressed() {
    if( lensRect != null ) {
      cancelLens();
    } else if( editingChain != null ) {
      setEditingChain(null);
      return;
    } else if( currentTool instanceof ExploreTool ) {
      currentTool = window.commands.move;
      return;
    } else {
      SwingUtils.beep();
    }
  }

  void selectEditingTool() {
    if( editingChain != null ) {
      currentTool = editingChain.isTrack() ?
          window.commands.trackTool : window.commands.boundTool;
    }
  }

  // -------------------------------------------------------------------------

  int toggleState =
      Toggles.SUPERSAMPLE.bit() |
      Toggles.SHOW_LABELS.bit() |
      Toggles.EXT_BOUNDS.bit() |
      Toggles.CROSSHAIRS.bit() |
      Toggles.MAIN_TRACK.bit();

  void rotateCommand() {
    setProjection(TurnedProjection.turnCounterclockwise(projection));
  }

  public void defaultTilesetClickAction(Tileset targetTiles) {
    if( lensRect != null ) {
      lensTiles = targetTiles;
      lensZoom = targetTiles.guiTargetZoom();
    } else if( targetTiles.isOverlayMap() ) {
      SwingUtils.beep();
    } else
      orthoCommand(targetTiles);
  }

  void orthoCommand(Tileset targetTiles) {
    int logPixsize = Coords.zoom2logPixsize(targetTiles.guiTargetZoom());
    double log2along = MathUtil.log2(projection.scaleAlong());
    double log2across = MathUtil.log2(projection.scaleAlong());
    // the "along" direction is the one that can have a larger pixsize
    if( logPixsize > log2along )
      logPixsize = (int) Math.ceil(log2along);
    else if( logPixsize < log2across )
      logPixsize = (int) Math.floor(log2across);
    setMainTiles(targetTiles);
    setProjection(OrthoProjection.ORTHO.withScaleAcross(1 << logPixsize));
  }

  boolean canWarp() {
    try {
      new WarpedProjection(files.activeFile(), files.cache);
      return true;
    } catch( WarpedProjection.CannotWarp e ) {
      return false;
    }
  }

  void warpCommand(Tileset targetTiles) {
    try {
      double squeeze = Math.rint(projection.getSqueeze());
      if( squeeze <= 1 ) squeeze = 5;
      double scale = Math.min(projection.scaleAcross(),
          Coords.zoom2pixsize(targetTiles.guiTargetZoom()));
      var baseWarp = new WarpedProjection(files.activeFile(), files.cache);
      setMainTiles(targetTiles);
      setProjection(baseWarp.withScaleAndSqueeze(scale, squeeze));
    } catch( WarpedProjection.CannotWarp e ) {
      window.showErrorBox("Cannot create warped projection: %s", e.getMessage());
    }
  }

  void lensCommand(Tileset targetTiles) {
    cancelLens();
    lensTiles = targetTiles;
    window.commands.lens.invoke();
  }

  Runnable squeezeCommand() {
    if( projection.base().isOrtho() )
      return null;
    else return () -> {
      setProjection(projection.withSqueeze(projection.getSqueeze()+1));
    };
  }

  Runnable stretchCommand() {
    if( projection.getSqueeze() < 2 )
      return null;
    else return () -> {
      setProjection(projection.withSqueeze(projection.getSqueeze()-1));
    };
  };

  Runnable copyCommand() {
    if( editingChain == null ) return null;
    return () -> { clipboard = editingChain; };
  }

  Runnable cutCommand() {
    if( editingChain == null ) return null;
    return () -> {
      clipboard = editingChain;
      files.activeFile().rewriteContent(undoList, "Cut track",
          content -> {
            var chains = content.chainsCopy();
            chains.remove(editingChain);
            return content.withChains(chains);
          });
      setEditingChain(null);
    };
  }

  Runnable pasteCommand() {
    if( clipboard == null ||
        files.activeFile().content().contains(clipboard) )
      return null;
    return () -> {
      files.activeFile().rewriteContent(undoList, "Paste track",
          content -> {
            var chains = content.chainsCopy();
            chains.add(clipboard);
            return content.withChains(chains);
          });
      setEditingChain(clipboard);
    };
  }

  public void setEditingChain(SegmentChain chain) {
    editingChain = chain;
    if( chain != null &&
        currentTool instanceof EditTool et &&
        chain.chainClass != et.chainClass )
      currentTool = currentTool.owner.move;
  }

  // -------------------------------------------------------------------------

  Runnable reverseCommand() {
    if( editingChain == null ||
        editingChain.numNodes == 1 ) return null;
    return () -> {
      if( editingChain == null ) return;
      var nodeList = new ArrayList<>(editingChain.nodes);
      var kindList = new ArrayList<>(editingChain.kinds);
      Collections.reverse(nodeList);
      Collections.reverse(kindList);
      var newChain = new SegmentChain(nodeList, kindList);
      files.activeFile().rewriteContent(undoList, "Reverse track",
          content -> {
            var chains = content.chainsCopy();
            chains.remove(editingChain);
            chains.add(newChain);
            return content.withChains(chains);
          });
      setEditingChain(newChain);
    };
  }

  // -------------------------------------------------------------------------

  private void addUsebounds(List<PokePublisher> pokes, VisibleTrackData vdt,
      FileContent middle) {
    if( !Toggles.EXT_BOUNDS.setIn(toggleState) ) return;
    for( var p : middle.usebounds() ) {
      VectFile vf = files.cache.getFile(p);
      if( vf != files.activeFile() ) {
        pokes.add(vf.changePokes);
        vdt.showBoundChainsIn(vf.content());
      }
    }
  }

  public VisibleTrackData collectVisibleTrackData() {
    var pokes = new ArrayList<PokePublisher>();
    VisibleTrackData vdt = new VisibleTrackData();
    VectFile current = files.activeFile();
    pokes.add(current.changePokes);
    FileContent currentContent = files.activeFile().content();
    vdt.setCurrentChains(currentContent);
    if( editingChain != null ) {
      vdt.removeCurrentChain(editingChain);
      vdt.setEditingChain(editingChain);
    }
    addUsebounds(pokes, vdt, currentContent);
    for( var showpath : files.showtracks() ) {
      VectFile vf = files.cache.getFile(showpath);
      pokes.add(vf.changePokes);
      FileContent showContent = vf.content();
      vdt.showTrackChainsIn(showpath, showContent);
      addUsebounds(pokes, vdt, showContent);
    }
    vdt.setFlags(toggleState);
    contentChangeListener.setSources(pokes);
    vdt.freeze();
    if( vdt.equals(currentVisible) )
      return currentVisible;
    else
      return currentVisible = vdt;
  }

  // -------------------------------------------------------------------------

  private ProjectionWorker cachedTranslator;

  /**
   * Get a projection translator for the current projection which can be
   * used generally within the UI thread.
   */
  public ProjectionWorker translator() {
    if( cachedTranslator == null ||
        cachedTranslator.projection() != projection ) {
      cachedTranslator = projection.createWorker();
    }
    return cachedTranslator;
  }

}