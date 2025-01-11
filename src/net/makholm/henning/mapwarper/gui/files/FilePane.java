package net.makholm.henning.mapwarper.gui.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.makholm.henning.mapwarper.gui.MapView;
import net.makholm.henning.mapwarper.gui.UndoList;
import net.makholm.henning.mapwarper.gui.UndoList.UndoItem;
import net.makholm.henning.mapwarper.gui.projection.WarpedProjection;
import net.makholm.henning.mapwarper.gui.swing.GuiMain;
import net.makholm.henning.mapwarper.gui.swing.PokeReceiver;
import net.makholm.henning.mapwarper.gui.swing.SwingFilePane;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.util.LongHashed;
import net.makholm.henning.mapwarper.util.NiceError;
import net.makholm.henning.mapwarper.util.PokePublisher;

/**
 * UI logic for the map pane, other than actually displaying it.
 *
 * This is also responsible for keeping track of what the active file
 * is, and so forth.
 */
public class FilePane {

  public final GuiMain window;
  public final MapView mapView;
  public final FSCache cache;

  private Path focusDir;
  private VectFile activeFile;
  private Set<Path> showtracks = new LinkedHashSet<>();

  public final PokePublisher activeFilePokes =
      new PokePublisher("activeFileChange");

  public FilePane(MapView map, FSCache cache, String filearg) {
    this.window = map.window;
    this.mapView = map;
    this.cache = cache;
    this.swing = new SwingFilePane(this);

    Path arg = Path.of(filearg == null ? "." : filearg);
    arg = arg.toAbsolutePath().normalize();
    if( arg.getFileName().toString().endsWith(VectFile.EXTENSION) ) {
      focusDir = arg.getParent();
      activeFile = cache.getFile(arg);
    } else if( Files.isDirectory(arg) ) {
      focusDir = arg;
      activeFile = new VectFile(cache, null);
    } else {
      throw NiceError.of("Strange command line argument '%s'", filearg);
    }
    descendWhileUnambiguous();
    updateView();
  }

  public VectFile activeFile() {
    return activeFile;
  }

  public void setActiveFile(VectFile file) {
    if( file != activeFile ) {
      mapView.setEditingChain(null);
      activeFile = file;
      showtracks.remove(file.path);
      mapView.currentTool.activeFileChanged();
      activeFilePokes.poke();
      updateViewEventually();
    }
  }

  public Iterable<Path> showtracks() {
    return showtracks;
  }

  public Iterable<Path> siblingsOf(VectFile vf) {
    Path dir = vf.path != null ? vf.path.getParent() : focusDir;
    return cache.getDirectory(dir).vectFiles.values();
  }

  private void locateEntryFlags() {
    if( activeFile != null ) {
      if( activeFile.path != null )
        addEntryFlag(activeFile.path, ACTIVE_FLAG);
      for( var bound : activeFile.content().usebounds() )
        addEntryFlag(bound, USE_BOUNDS_FLAG);
    }
    for( var p : showtracks )
      addEntryFlag(p, SHOW_TRACK_FLAG);
    for( var vf : cache.getModifiedFiles() )
      if( vf.path != null )
        addEntryFlag(vf.path, MODIFIED_FLAG);
    if( mapView.projection.base() instanceof WarpedProjection wp &&
        wp.sourcename0 != null )
      addEntryFlag(wp.sourcename0, WARP_FLAG);
  }

  public void mouseClicked(Entry entry, boolean iconColumn) {
    switch( entry.kind ) {
    case TRUNK_DIR:
      focusDir = entry.path;
      break;
    case BRANCH_DIR:
    case TIP_DIR:
      focusDir = entry.path;
      descendWhileUnambiguous();
      break;
    case FILE:
      if( activeFile != null && entry.path.equals(activeFile.path) )
        return;
      else if( iconColumn ) {
        toggleVisible(entry.path);
        activeFilePokes.poke();
      } else {
        openFile(entry);
      }
      break;
    }
    updateView();
  }

  public void draggedToMapView(Entry entry) {
    if( entry.kind == EntryKind.FILE ) {
      openFile(entry);
    }
  }

  public void openCommand() {
    if( showBoxIfSavingIsNeeded() ) return;
    Path startWhere = focusDir;
    if( activeFile.path != null &&
        startWhere.startsWith(activeFile.path.getParent()) )
      startWhere = activeFile.path.getParent();

    var fc = new JFileChooser();
    fc.setCurrentDirectory(startWhere.toFile());
    fc.setFileFilter(new FileNameExtensionFilter(
        "Vector track files", VectFile.EXTENSION.substring(1)));
    fc.setAcceptAllFileFilterUsed(false);
    if( fc.showOpenDialog(window) == JFileChooser.APPROVE_OPTION ) {
      Path toOpen = fc.getSelectedFile().toPath();
      var vf = cache.getFile(toOpen);
      openFile(vf);
    }
  }

  public void newCommand() {
    if( showBoxIfSavingIsNeeded() ) return;
    if( activeFile.path != null && activeFile.content().countsAsTrackFile() )
      showtracks.add(activeFile.path);
    VectFile vf = new VectFile(cache, null);
    List<Path> usebounds = new ArrayList<>();
    for( var p : activeFile.content().usebounds() ) usebounds.add(p);
    vf.setContentHarshly(new FileContent(null, List.of(), usebounds));
    setActiveFile(vf);
  }

  private void openFile(Entry e) {
    openFile(cache.getFile(e.path));
  }

  public void openFile(VectFile vf) {
    if( vf == activeFile && vf.error != null ) return;
    if( vf.showBoxOnError(window) ) return;
    if( showBoxIfSavingIsNeeded() ) return;

    handleShownFilesWhenSwitching(activeFile, vf);
    mapView.switchToFile(vf);

    // make sure we show an error early if one of the referenced
    // bounds files cannot be loaded.
    for( var bound : vf.content().usebounds() )
      cache.getFile(bound).content();

    Path prevFocusDir = focusDir;
    focusDir = vf.path.getParent();
    descendForBoundFiles();
    if( prevFocusDir.startsWith(focusDir) )
      focusDir = prevFocusDir;
    else
      descendWhileUnambiguous();

    updateView();
  }

  private boolean showBoxIfSavingIsNeeded() {
    if( unnamedFileNeedsSaving() ) {
      window.showErrorBox("The current anonymous file contains changes "
          + "that would be lost. Please save them first.");
      return true;
    } else {
      return false;
    }
  }

  public boolean unnamedFileNeedsSaving() {
    var active = activeFile();
    return active.path == null && active.isModified();
  }

  public boolean saveAsIfNecessary() {
    if( activeFile.path != null )
      return true;
    else {
      var fc = new JFileChooser();
      fc.setCurrentDirectory(focusDir.toFile());
      fc.setFileFilter(new FileNameExtensionFilter(
          "Vector track files", VectFile.EXTENSION.substring(1)));
      fc.setAcceptAllFileFilterUsed(false);

      if( fc.showSaveDialog(window) != JFileChooser.APPROVE_OPTION ) {
        return false;
      } else {
        Path toSaveTo = fc.getSelectedFile().toPath();
        String filename = toSaveTo.getFileName().toString();
        if( !filename.endsWith(VectFile.EXTENSION) )
          toSaveTo = toSaveTo.resolveSibling(filename+VectFile.EXTENSION);
        if( Files.exists(toSaveTo) ) {
          window.showErrorBox("%s already seems to exist.", toSaveTo);
          return false;
        }
        VectFile vf = cache.getFile(toSaveTo);
        vf.setContentHarshly(activeFile.content());
        try {
          vf.trySaving();
        } catch( NiceError e ) {
          window.showErrorBox("Could not save to %s: %s");
          return false;
        }
        cache.refreshDirectory(toSaveTo.getParent());
        activeFile = vf;
        Path newFocus = toSaveTo.getParent();
        if( !newFocus.startsWith(focusDir) )
          focusDir = toSaveTo.getParent();
        activeFilePokes.poke();
        updateViewEventually();
        return true;
      }
    }
  }

  public void saveAllCommand() {
    if( !saveAsIfNecessary() )
      return;

    List<String> problems = saveAll();
    if( !problems.isEmpty() ) {
      StringBuilder msg = new StringBuilder();
      for( var s : problems ) {
        if( !msg.isEmpty() ) msg.append('\n');
        msg.append(s);
      }
      window.showErrorBox("%s", msg.toString());
    }
    // TODO: we should clear out unused VectFiles from the cache now
  }

  public List<String> saveAll() {
    var badnesses = new ArrayList<String>();
    if( unnamedFileNeedsSaving() ) {
      badnesses.add("The current file has no name to save to.");
    }
    for( var vf : cache.getModifiedFiles() ) {
      if( vf.path == null ) continue;
      try {
        vf.trySaving();
      } catch( NiceError e ) {
        badnesses.add(e.getMessage());
      }
    }
    cache.cleanCache(focusDir);
    return badnesses;
  }

  public void revertCommand() {
    int count = cache.getModifiedFiles().size();
    boolean alsoChanged;
    if( count == 0 ) {
      alsoChanged = false;
    } else {
      int answer = JOptionPane.showConfirmDialog(window,
          "Also revert "+count+" unsaved files?",
          "Re-read files from disk", JOptionPane.YES_NO_CANCEL_OPTION);
      if( answer == JOptionPane.CANCEL_OPTION ) return;
      alsoChanged = answer == JOptionPane.YES_OPTION;
    }
    Map<Path, FileContent> undoMap = cache.revertContent(alsoChanged);
    mapView.setEditingChain(null);
    if( undoMap.isEmpty() )
      return;
    mapView.undoList.pushItem(new UndoList.UndoItem() {
      @Override
      public String undoDesc() {
        return "Revert "+undoMap.size()+" files";
      }

      @Override
      public UndoItem apply(MapView mapView) {
        undoMap.forEach((path, content) -> {
          cache.getFile(path).changeContentNoUndo(null, content);
        });
        mapView.setEditingChain(null);
        JOptionPane.showMessageDialog(window,
            undoMap.size()+" files have been reset to their state before "
                +"they were reverted. This leaves the undo machinery in a "
                +"somewhat inconsistent state; you'll probably want "
                +"to save everything now and start over ...",
                "Reverting undone",
                JOptionPane.WARNING_MESSAGE);
        return null;
      }
    });
  }

  /**
   * Heuristics that tries to Do The Right Thing about the also-show
   * flags when we open a new file.
   *
   * TODO: this logic would probably belong better in Teleporter?
   */
  public void handleShownFilesWhenSwitching(VectFile oldActive,
      VectFile newActive) {
    boolean wasShown = showtracks.remove(newActive.path);
    boolean oldIsTrack = oldActive.content().countsAsTrackFile();
    boolean newIsTrack = newActive.content().countsAsTrackFile();
    if( oldIsTrack == newIsTrack ) {
      // For switch between two files of the same class, we'll preserve
      // the number of shown tracks.
      if( wasShown && oldActive.path != null )
        showtracks.add(oldActive.path);
    } else if( newIsTrack ) {
      // moving from a bound file to a track file never makes
      // the bound visible.
      // (perhaps we even ought to clear out existing visible _bound_ files);
    } else {
      // moving from track file to a bound requires us to guess whether
      // they're supposed to be related or we're switching to an entirely
      // part of the world.
      if( oldActive.content().usesBounds(newActive.path) ||
          (oldActive.path != null && newActive.path != null &&
          newActive.path.startsWith(oldActive.path.getParent())) )
        showtracks.add(oldActive.path);
    }
  }

  /**
   * @return the path itself <em>if</em> it was toggled as usebound
   */
  private void toggleVisible(Path p) {
    if( showtracks.contains(p) ) {
      showtracks.remove(p);
    } else if( activeFile.content().usesBounds(p) ) {
      activeFile.rewriteContent(mapView.undoList, "Remove bounds usage",
          f -> f.removeUsebounds(p));
    } else {
      VectFile vf = cache.getFile(p);
      if( vf.showBoxOnError(window) ) {
        return;
      } else if( !vf.content().countsAsTrackFile() ) {
        activeFile.rewriteContent(mapView.undoList, "Add bounds usage",
            fc -> fc.addUsebounds(p));
      } else {
        showtracks.add(p);
      }
    }
  }

  private void descendForBoundFiles() {
    Path target = null;
    for( var bound : activeFile.content().usebounds() ) {
      if( !bound.startsWith(focusDir) ) {
        // Ignore completely external bounds
      } else if( target == null ) {
        target = bound.getParent();
      } else {
        while( !bound.startsWith(target) ) {
          target = target.getParent();
          if( target.getNameCount() <= focusDir.getNameCount() )
            return;
        }
      }
    }
    if( target != null )
      focusDir = target;
  }

  private void descendWhileUnambiguous() {
    for(;;) {
      CachedDirectory dir = cache.getDirectory(focusDir);
      if( dir.subdirs.size() == 1 )
        focusDir = dir.subdirs.values().iterator().next();
      else
        return;
    }
  }

  // -------------------------------------------------------------------------

  public final SwingFilePane swing;

  public Entry[] entryList;

  public static final int PHANTOM_FILE_FLAG = 64;
  public static final int ERROR_FLAG = 32;
  public static final int MODIFIED_FLAG = 16;
  public static final int ACTIVE_FLAG = 8;
  public static final int WARP_FLAG = 4;
  public static final int SHOW_TRACK_FLAG = 2;
  public static final int USE_BOUNDS_FLAG = 1;

  public enum EntryKind { FILE, TRUNK_DIR, BRANCH_DIR, TIP_DIR }

  public static final class Entry extends LongHashed {
    public final Path path;
    public final String name;
    public int displayFlags;
    public final EntryKind kind;

    Entry(Path path, EntryKind kind) {
      this.path = path;
      if( path.getNameCount() == 0 )
        this.name = "";
      else
        this.name = path.getFileName().toString();
      this.kind = kind;
    }

    void addFlags(int flags) {
      displayFlags |= flags;
      invalidateHash();
    }

    @Override
    protected long longHashImpl() {
      return path.hashCode() +
          ((long)kind.ordinal() << 32) +
          ((long)displayFlags << 35);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Entry other &&
          other.longHash() == longHash() &&
          other.displayFlags == displayFlags &&
          other.kind == kind &&
          other.path.equals(path);
    }
  }

  // These are only populated while we're rebuilding the entry list
  private final List<PokePublisher> neededSubscriptions = new ArrayList<>();
  private final Map<Path, Entry> allEntries = new LinkedHashMap<>();
  private final Map<Path, TreeMap<String, Entry>> branches = new LinkedHashMap<>();

  private final PokeReceiver updateViewTrigger =
      new PokeReceiver("updateFilePane", this::updateView);

  public void updateViewEventually() {
    updateViewTrigger.poke();
  }

  private void updateView() {
    try {
      List<CachedDirectory> trunk = createTrunk();
      locateEntryFlags();
      entryList = collectEntries(trunk);
      updateViewTrigger.setSources(neededSubscriptions);
    } finally {
      allEntries.clear();
      branches.clear();
      neededSubscriptions.clear();
      neededSubscriptions.add(cache.modifiedFilesPokes);
    }
    swing.refreshScene(entryList);
  }

  private List<CachedDirectory> createTrunk() {
    var trunk = new ArrayList<CachedDirectory>();
    for( Path p0 = focusDir; p0 != null; p0 = p0.getParent() ) {
      var branchmap = new TreeMap<String, Entry>();
      var dir = cache.getDirectory(p0);
      if( p0 == focusDir ) {
        dir.subdirs.forEach((name, path) -> {
          if( !allEntries.containsKey(path) ) {
            branchmap.put(name, createEntry(path, EntryKind.TIP_DIR));
          }
        });
      }
      dir.vectFiles.forEach((name, path) -> {
        allEntries.put(path, createEntry(path, EntryKind.FILE));
      });
      createEntry(p0, EntryKind.TRUNK_DIR);
      branches.put(p0, branchmap);
      trunk.add(dir);
    }
    return trunk;
  }

  private void addEntryFlag(Path p, int flag) {
    Entry e = allEntries.get(p);
    if( e != null ) {
      e.addFlags(flag);
      return;
    }
    Path parent = p.getParent();
    e = allEntries.get(parent);
    if( e != null && e.kind == EntryKind.TRUNK_DIR ) {
      // Special case: this file _should_ have appeared in
      // the directory listing.
      e = createEntry(p, EntryKind.FILE);
      // not quite the right place to put it ...
      branches.get(parent).put(e.name, e);
      e.addFlags(flag | PHANTOM_FILE_FLAG);
      return;
    } else {
      // The common case for files outside the trunk is to scan
      // upwards until we find something that does have an entry.
      while( e == null ) {
        if( parent == null ) {
          // huh?
          // (perhaps we're on Windows and it's a different drive?)
          return;
        }
        p = parent;
        parent = p.getParent();
        e = allEntries.get(parent);
      }
      switch( e.kind ) {
      case BRANCH_DIR:
      case TIP_DIR:
        e.addFlags(flag);
        return;
      case TRUNK_DIR:
        e = createEntry(p, EntryKind.BRANCH_DIR);
        branches.get(parent).put(e.name, e);
        e.addFlags(flag);
        return;
      case FILE:
        // Huh?
        return;
      }
    }
  }

  private Entry createEntry(Path p, EntryKind kind) {
    var e = new Entry(p, kind);
    allEntries.put(p, e);
    if( kind == EntryKind.FILE ) {
      VectFile vf = cache.getFile(p);
      neededSubscriptions.add(vf.changePokes);
    }
    return e;
  }

  private Entry[] collectEntries(List<CachedDirectory> trunk) {
    var list = new ArrayList<Entry>(allEntries.size());
    for( int i=trunk.size()-1; i>=0; i-- ) {
      CachedDirectory dir = trunk.get(i);
      list.add(allEntries.get(dir.path));
      for( Path p : dir.vectFiles.values() ) {
        Entry e = allEntries.get(p);
        if( cache.getFile(p).error != null )
          e.addFlags(ERROR_FLAG);
        list.add(e);
      }
      list.addAll(branches.get(dir.path).values());
    }
    return list.toArray(new Entry[list.size()]);
  }

}
