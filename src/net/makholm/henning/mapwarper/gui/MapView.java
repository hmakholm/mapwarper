package net.makholm.henning.mapwarper.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.PointWithNormal;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.Tile;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.gui.files.FSCache;
import net.makholm.henning.mapwarper.gui.files.FilePane;
import net.makholm.henning.mapwarper.gui.files.VectFile;
import net.makholm.henning.mapwarper.gui.maprender.FallbackChain;
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
import net.makholm.henning.mapwarper.tiles.NomapTiles;
import net.makholm.henning.mapwarper.tiles.OpenStreetMap;
import net.makholm.henning.mapwarper.tiles.OpenTopoMap;
import net.makholm.henning.mapwarper.tiles.TileContext;
import net.makholm.henning.mapwarper.tiles.TileSpec;
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

  void setLens(BoxOverlay box) {
    lensRect = box;
    lensZoom = Math.min(naturalLensZoom(),
        dynamicLensSpec.mainTiles().guiTargetZoom());
  }

  void cancelLens() {
    if( lensRect != null ) {
      swing.repaintFor(lensRect);
      lensRect = null;
    }
  }

  int naturalLensZoom() {
    return FallbackChain.naturalZoom(
        projection.scaleAcross(), dynamicLensSpec.mainTiles());
  }

  boolean isExportLens() {
    return lensTiles instanceof NomapTiles;
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
    @Override public Tileset mainTiles() {
      return isExportLens() ? mainTiles : lensTiles;
    }
    @Override public int targetZoom() { return lensZoom; }
    @Override public Tileset fallbackTiles() { return lensTiles; }
    @Override public DoubleSupplier windowDiagonal() { return globalWindowDiagonal; }

    @Override public int flags() {
      int flags = toggleState;
      flags |= Toggles.SUPERSAMPLE.bit();
      flags |= Toggles.OVERLAY_MAP.bit();
      if( isExportLens() ) {
        flags |= Toggles.BLANK_OUTSIDE_MARGINS.bit();
      } else {
        flags |= Toggles.LENS_MAP.bit();
      }
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
        var newWarp = new WarpedProjection(vf, files.cache,
            editingChain, wp.track);
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
    if( swing.cancelDrag() ) {
      // That's all
    } else if( lensRect != null ) {
      cancelLens();
    } else {
      currentTool.escapeAction();
    }
  }

  public void selectTool(Tool tool) {
    currentTool = tool;
    currentTool.sanitizeEditingStateWhenSelected();
  }

  void selectEditingTool() {
    if( editingChain != null ) {
      selectTool(editingChain.isTrack() ?
          window.commands.trackTool : window.commands.boundTool);
    }
  }

  // -------------------------------------------------------------------------

  public int toggleState =
      Toggles.DOWNLOAD.bit() |
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
      setMainTiles(targetTiles);
  }

  void orthoCommand(Tileset targetTiles, boolean downloading) {
    int logPixsize = Coords.zoom2logPixsize(targetTiles.guiTargetZoom());
    int naturalLogPixsize = logPixsize;
    double log2along = MathUtil.log2(projection.scaleAlong());
    double log2across = MathUtil.log2(projection.scaleAlong());
    // the "along" direction is the one that can have a larger pixsize
    if( logPixsize > log2along )
      logPixsize = (int) Math.ceil(log2along);
    else if( logPixsize < log2across )
      logPixsize = (int) Math.floor(log2across);
    setMainTiles(targetTiles);
    if( downloading ) {
      toggleState |= Toggles.DOWNLOAD.bit();
    } else {
      toggleState &= ~Toggles.DOWNLOAD.bit();
      toggleState |= Toggles.DARKEN_MAP.bit();
      logPixsize = Math.min(logPixsize,
          naturalLogPixsize + OrthoProjection.WEAK_SHRINK);
    }
    setProjection(OrthoProjection.ORTHO.withScaleAcross(1 << logPixsize));
  }

  private WarpedProjection makeWarpedProjection() throws CannotWarp {
    var file = files.activeFile();
    if( editingChain != null && editingChain.isTrack() )
      return new WarpedProjection(file, files.cache, editingChain);

    SegmentChain currentWarped = null;
    Path currentSource = null;
    if( projection.base() instanceof WarpedProjection wp ) {
      currentWarped = wp.track;
      currentSource = wp.sourcename0;
    }

    if( file.content().numTrackChains > 0 )
      return new WarpedProjection(file, files.cache, currentWarped);

    // If we're showing just one other track file, warp along that
    Iterator<Path> it = files.showtracks().iterator();
    if( it.hasNext() ) {
      Path shown = it.next();
      if( !it.hasNext() ) {
        return new WarpedProjection(files.cache.getFile(shown),
            files.cache, currentWarped);
      }
    }

    // As the last fallback, attempt to refresh the current warp
    if( currentSource != null ) {
      return new WarpedProjection(files.cache.getFile(currentSource),
          files.cache, currentWarped);
    }

    // Throw the right error by making a last doomed attempt
    return new WarpedProjection(file, files.cache);
  }

  boolean canWarp() {
    try {
      makeWarpedProjection();
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
      var baseWarp = makeWarpedProjection();
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

  private Runnable cancelLastGetTile = () -> {};
  private static final Consumer<TileBitmap> dummyDownloadConsumer =
      new Consumer<TileBitmap>() {
    @Override public void accept(TileBitmap ignore) {}
  };

  Runnable singleTileDownloadCommand() {
    if( projection.base().usesDownloadFlag() &&
        Toggles.DOWNLOAD.setIn(toggleState) ) {
      return null;
    } else return () -> {
      cancelLens();
      long coords = Coords.point2pixcoord(mouseGlobal);
      long shortcode = Tile.codedContaining(coords, mainTiles.guiTargetZoom());
      cancelLastGetTile.run();
      cancelLastGetTile = mainTiles.context.downloader.request(
          new TileSpec(mainTiles, shortcode), dummyDownloadConsumer);
    };
  }

  void squeezeCommand() {
    double newSqueeze = projection.getSqueeze()+1;
    setProjection(projection.makeSqueezeable().withSqueeze(newSqueeze));
  }

  Runnable stretchCommand() {
    double oldSqueeze = projection.getSqueeze();
    double newSqueeze = Math.max(1, oldSqueeze-1);
    if( newSqueeze == oldSqueeze )
      return null;
    else return () -> {
      Projection p = projection.withSqueeze(newSqueeze);
      Projection q = p.perhapsOrthoEquivalent();
      setProjection(q != null ? q : p);
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
      selectTool(currentTool.owner.move);
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