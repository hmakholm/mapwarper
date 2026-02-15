package net.makholm.henning.mapwarper.gui.files;

import java.awt.Cursor;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.Commands;
import net.makholm.henning.mapwarper.gui.GenericEditTool;
import net.makholm.henning.mapwarper.gui.MouseAction;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.overlays.TextOverlay;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.projection.OrthoProjection;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.track.ChainClass;
import net.makholm.henning.mapwarper.track.LengthEstimator;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.VisibleTrackData;
import net.makholm.henning.mapwarper.util.SingleMemo;
import net.makholm.henning.mapwarper.util.XyTree;

public class OpenTool extends TrackHidingTool {

  private CachedState cachedState;
  private final SingleMemo<SegmentChain, Double> chainLength =
      SingleMemo.of(new LengthEstimator());

  public OpenTool(Commands owner) {
    super(owner, "filepick", "Visual file picker");
  }

  @Override
  public int retouchDisplayFlags(int flags) {
    flags |= Toggles.DARKEN_MAP.bit();
    return flags;
  }

  /**
   * Return true if we have
   */
  public void invokeInitially(AxisRect fallbackLocation) {
    makeVisible(fallbackLocation);
    ensureSubscribed();
  }

  @Override
  public void invoke() {
    if( cached().possibilities.isEmpty() ) {
      owner.window.showErrorBox(
          "%s does not contain any files with track definitions",
          owner.files.focusDir());
    } else {
      ensureSubscribed();
      super.invoke();
    }
  }

  @Override
  public void whenSelected() {
    super.whenSelected();
    ensureVisible();
  }

  void ensureVisible() {
    var proj = mapView().projection;
    if( proj.base() == OrthoProjection.ORTHO && !alreadyVisible() )
      makeVisible(null);
  }

  private boolean alreadyVisible() {
    AxisRect localRect = new AxisRect(mapView().visibleArea);
    AxisRect globalRect = localRect.transform(
        mapView().projection::local2projected);
    for( var chain : cached().possibilities.keySet() ) {
      for( var segment : chain.smoothed() ) {
        var bbox = segment.bbox.get();
        if( globalRect.contains(bbox) )
          return true;
      }
    }
    return false;
  }

  private void makeVisible(AxisRect fallback) {
    AxisRect commonBbox = cached().commonBbox;
    if( commonBbox == null ) commonBbox = fallback;
    if( commonBbox != null ) mapView().unzoomTo(commonBbox);
  }

  private boolean subscribedYet;

  private void ensureSubscribed() {
    if( !subscribedYet ) {
      subscribedYet = true;
      owner.files.focusDirClicked.subscribe(() -> {
        if( mapView().currentTool == OpenTool.this ) {
          mapView().swing.invalidateToolResponse();
          makeVisible(null);
          mapView().swing.refreshScene();
        }
      });
    }
  }

  // ------------------------------------------------------------------------

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    return cached().mouseResponse(pos, modifiers);
  }

  @Override
  public ToolResponse outsideWindowResponse() {
    return cached().responseByFilePane();
  }

  @Override
  public MouseAction drag(Point p, int modifiers) {
    return DRAG_THE_MAP;
  }

  private CachedState cached() {
    if( cachedState == null || !cachedState.isValid() )
      cachedState = new CachedState();
    return cachedState;
  }

  private record SegWithPath(VectFile vf, SegmentChain chain, Bezier curve) {}

  private class CachedState {
    final int cacheInvalidateCount = owner.files.cache.invalidateCount;
    AxisRect commonBbox;
    final Path focusDir = owner.files.focusDir();

    boolean isValid() {
      return owner.files.cache.invalidateCount == cacheInvalidateCount &&
          owner.files.focusDir().equals(focusDir);
    }

    final Map<SegmentChain, VectFile> possibilities;
    final VisibleTrackData toShow;
    final SingleMemo<ProjectionWorker, XyTree<List<SegWithPath>>> lookupTree;
    final SingleMemo<SegWithPath, TextOverlay> baseLabels =
        SingleMemo.of(this::makeLabel);

    CachedState() {
      var cache = owner.files.cache;
      possibilities = new LinkedHashMap<SegmentChain, VectFile>();
      toShow = new VisibleTrackData();
      boolean seenAnyVectFiles = false;
      for( var entry : owner.files.entryList ) {
        switch( entry.kind ) {
        case FILE:
          seenAnyVectFiles = true;
          addPossibility(cache.getFile(entry.path));
          break;
        case TRUNK_DIR:
          if( !cache.getDirectory(entry.path).vectFiles.isEmpty() )
            seenAnyVectFiles = true;
          break;
        default:
          break;
        }
      }
      if( seenAnyVectFiles )
        recursivelyScanSubdirs(cache, 0, owner.files.focusDir());
      for( var path : owner.files.showtracks() )
        addPossibility(cache.getFile(path));
      toShow.setFlags(Toggles.STRONG_FOREIGN_TRACK_CHAINS.bit());
      toShow.freeze();
      lookupTree = SingleMemo.of(ProjectionWorker::projection, this::makeLookupTree);

      if( commonBbox == null )
        for( var chain : possibilities.keySet() )
          commonBbox = chain.curveTree.get().union(commonBbox);
    }

    private void recursivelyScanSubdirs(FSCache cache, int level, Path p) {
      if( level > 5 ) return; // guard against symlink loops etc
      var dir = cache.getDirectory(p);
      if( level > 0 )
        dir.vectFiles.forEach((k,pp) -> addPossibility(cache.getFile(pp)));
      for( var pp : dir.subdirs.values() )
        recursivelyScanSubdirs(cache, level+1, pp);
    }

    private void addPossibility(VectFile vf) {
      var content = vf.content();
      toShow.showTrackChainsIn(vf.path, content);
      for( var chain : content.chains() )
        if( chain.chainClass == ChainClass.TRACK ) {
          possibilities.put(chain, vf);
          if( vf.path.startsWith(focusDir) )
            commonBbox = chain.curveTree.get().union(commonBbox);
        }
    }

    private XyTree<List<SegWithPath>> makeLookupTree(ProjectionWorker worker) {
      var joiner = XyTree.<SegWithPath>concatJoin();
      var result = joiner.empty();
      for( var chain : possibilities.keySet() ) {
        var path = possibilities.get(chain);
        var local = chain.localizePerhapsTiny(worker);
        for( var segment : local.curves )
          for( var curve : segment )
            result = joiner.union(result, XyTree.singleton(curve.bbox.get(),
                List.of(new SegWithPath(path, chain, curve))));
      }
      return result;
    }

    private final ToolResponse noResponse = new ToolResponse() {
      @Override
      public VisibleTrackData previewTrackData() {
        return toShow;
      }
      @Override
      public void execute(ExecuteWhy why) {
        SwingUtils.beep();
      }
    };

    ToolResponse mouseResponse(Point p, int modifiers) {
      SegWithPath found = GenericEditTool.pickCurve(p, SegWithPath::curve,
          lookupTree.apply(mapView().translator()));
      if( found == null )
        return responseByFilePane();
      var withHighlight = toShow.clone();
      withHighlight.setHighlight(new TrackHighlight(found.chain, 0xDDFFDD));
      withHighlight.freeze();
      VectorOverlay label = placeLabel(p, found);
      return new ToolResponse() {
        @Override
        public VisibleTrackData previewTrackData() {
          return withHighlight;
        }
        @Override
        public VectorOverlay previewOverlay() {
          return label;
        }
        @Override
        public Cursor cursor() {
          return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        }
        @Override
        public void execute(ExecuteWhy why) {
          if( altHeld(modifiers) ) {
            owner.files.setAsOnlyShownFile(found.vf());
            mapView().perhapsRewarpTo(found.vf());
          } else {
            owner.files.openFile(found.vf());
          }
          activeFileChanged();
        }
      };
    }

    ToolResponse responseByFilePane() {
      if( owner.window.filePaneVisible() ) {
        var vf = owner.files.selectedFile();
        if( vf != null && vf.content().numTrackChains == 1 ) {
          var chain = vf.content().uniqueChain(ChainClass.TRACK);
          var withHighlight = toShow.clone();
          withHighlight.setHighlight(new TrackHighlight(chain, 0x664444));
          withHighlight.freeze();
          return new ToolResponse() {
            @Override
            public VisibleTrackData previewTrackData() {
              return withHighlight;
            }
            @Override
            public void execute(ExecuteWhy why) {
              SwingUtils.beep();
            }
          };
        }
      }
      return noResponse;
    }

    private TextOverlay makeLabel(SegWithPath swp) {
      Path path = basedirForLabel().relativize(swp.vf().path);
      return TextOverlay.of(owner.window,
          path.toString(),
          Coords.showlength(chainLength.apply(swp.chain)));
    }

    private Path basedirForLabel() {
      Path apath = owner.files.activeFile().path;
      Path fpath = owner.files.focusDir();
      if( apath != null && apath.getParent() != null &&
          fpath.startsWith(apath.getParent()) )
        return apath.getParent();
      else
        return fpath;
    }

    private TextOverlay placeLabel(Point p, SegWithPath found) {
      TextOverlay label = baseLabels.apply(found).at(p);
      if( p.y - label.boundingBox().height() >= mapView().visibleArea.top )
        label = label.moveUp();
      if( p.x - label.boundingBox().width() >= mapView().visibleArea.left )
        label = label.moveLeft();
      return label;
    }
  }
}
