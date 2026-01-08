package net.makholm.henning.mapwarper.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import net.makholm.henning.mapwarper.gui.files.FilePane;
import net.makholm.henning.mapwarper.gui.swing.Command;
import net.makholm.henning.mapwarper.gui.swing.GuiMain;
import net.makholm.henning.mapwarper.gui.swing.SwingMapView;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.gui.swing.ToggleCommand;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.track.SegKind;

public class Commands {

  public final GuiMain window;
  public final MapView mapView;
  public final SwingMapView swing;
  public final FilePane files;

  public final Map<String, Command> commandRegistry = new LinkedHashMap<>();

  public Commands(MapView main) {
    window = main.window;
    mapView = main;
    swing = main.swing;
    files = main.files;

    mapView.currentTool = move;
  }

  private final ToggleCommand[] toggles; {
    toggles = new ToggleCommand[Toggles.values().length];
    for( Toggles t : Toggles.values() )
      toggles[t.ordinal()] = new ToggleCommand(this, t.codename, t.niceName) {
      @Override
      public boolean getCurrentState() {
        return t.setIn(mapView.toggleState);
      }

      @Override
      public void setNewState(boolean b) {
        if( b )
          mapView.toggleState |= t.bit();
        else
          mapView.toggleState &= ~t.bit();
      }
    };
  }

  public ToggleCommand toggle(Toggles t) {
    return toggles[t.ordinal()];
  }

  private final ZoomTool zoomTool = new ZoomTool(this);
  public final LensTool lens = new LensTool(this);
  private final Tool quickwarp = new QuickwarpTool(this);
  private final Tool explore = new ExploreTool(this);
  public final Tool move = new MoveTool(this);

  private final Tool weakTrackTool =
      new TrackEditTool(this, SegKind.WEAK, "weak track");
  final Tool trackTool =
      new TrackEditTool(this, SegKind.TRACK, "curved track");
  private final Tool straightTool =
      new TrackEditTool(this, SegKind.STRONG, "strong track");
  private final Tool slewTool =
      new TrackEditTool(this, SegKind.SLEW, "displacement joiner");
  private final Tool magicTool =
      new TrackEditTool(this, SegKind.MAGIC, "arc joiner");
  final Tool boundTool = new BoundEditTool(this);
  private final Tool localBoundTool =
      new BoundSnappingTool(this, SegKind.LBOUND, "locally straight bound");

  private final Cmd unzoom = simple("unzoom", "Fit visible",
      self -> self.mapView.unzoomCommand());

  private final Cmd teleport = simple("teleport", "Jump to visible",
      self -> self.mapView.teleportCommand());

  public final Command zoomIn = check("zoomIn", "Zoom in",
      self -> self.zoomTool.zoomInCommand());

  public final Command zoomOut = check("zoomOut", "Zoom out",
      self -> self.zoomTool.zoomOutCommand());

  private final Cmd zoom100 = simple("zoom100", "Zoom 100%",
      self -> self.zoomTool.zoom100Command());

  private final Cmd rotate = simple("rotate", "Rotate 90°",
      self -> self.mapView.rotateCommand());

  private final Cmd squeeze = check("squeeze", "Increase squeeze factor",
      self -> self.mapView.squeezeCommand());

  private final Cmd stretch = check("stretch", "Decrease squeeze factor",
      self -> self.mapView.stretchCommand());

  private final Cmd lensPlus = check("lensPlus", "Increase lens resoltion",
      self -> self.lens.lensPlusCommand());

  private final Cmd lensMinus = check("lensMinus", "Decrease lens resolution",
      self -> self.lens.lensMinusCommand());

  private final Cmd downloadTile = simple("getTile", "Force-fetch map tile",
      self -> self.mapView.singleTileDownloadCommand());

  private final Cmd repaint = simple("repaint", "Repaint from scratch",
      self -> self.swing.repaintFromScratch());

  private final Cmd refresh = simple("refresh", "Save and refresh warp",
      self -> self.mapView.refreshWarpCommand());

  private final Cmd reverse = check("reverse", "Reverse track",
      self -> self.mapView.reverseCommand());

  private Command toggleFilePane = new ToggleCommand(this,
      "toggleFilePane", "Show file pane") {
    @Override public boolean getCurrentState() {
      return window.filePaneVisible();
    }
    @Override public void setNewState(boolean b) {
      window.setFilePaneVisible(b);
    }
  };

  private Command toggleTilesetPane = new ToggleCommand(this,
      "toggleTilePane", "Show tile provider pane") {
    @Override public boolean getCurrentState() {
      return window.tilesetPaneVisible();
    }
    @Override public void setNewState(boolean b) {
      window.setTilesetPaneVisible(b);
    }
  };

  private class TilesetCommand extends Command {
    final Predicate<MapView> enable;
    final BiConsumer<MapView, Tileset> doIt;
    final Tileset tiles;
    public TilesetCommand(String codename, String niceName,
        Predicate<MapView> enable,
        BiConsumer<MapView, Tileset> doIt, Tileset tiles) {
      super(Commands.this, codename+"."+tiles.name, niceName);
      this.enable = enable;
      this.doIt = doIt;
      this.tiles = tiles;
    }
    @Override public boolean makesSenseNow() { return enable.test(mapView); }
    @Override public void invoke() { doIt.accept(mapView, tiles); }
  }

  private class TilesetCommands {
    final Command just, ortho, weakOrtho, warp, lens;
    TilesetCommands(String name) {
      Tileset tiles = mapView.tiles.tilesets.get(name);
      if( tiles != null ) {
        if( tiles.isOverlayMap() )
          just = ortho = weakOrtho = warp = null;
        else {
          just = new TilesetCommand("just", "Use in current projection",
              mv -> mv.mainTiles != tiles,
              MapView::setMainTiles, tiles);
          ortho = new TilesetCommand("ortho", "Use in standard projection",
              mv -> true, (mv,t) -> mv.orthoCommand(t, true), tiles);
          if( tiles != mapView.fallbackTiles )
            weakOrtho = new TilesetCommand("weakOrtho",
                "Already downloaded tiles in standard projection",
                mv -> true, (mv,t) -> mv.orthoCommand(t, false), tiles);
          else
            weakOrtho = null;
          warp = new TilesetCommand("warp", "Use in fresh warped projection",
              MapView::canWarp, MapView::warpCommand, tiles);
        }
        lens = new TilesetCommand("lens", "Use with lens tool",
            mv -> true, MapView::lensCommand, tiles);
      } else {
        just = ortho = weakOrtho = warp = lens =
            simple("nosuchtiles."+name, "NOSUCHTILES",
                self -> self.window.showErrorBox(
                    "This key would use the unknown tileset '%s'.", name));
      }
    }
    final void defineMenu(IMenu menu) {
      if( just != null ) menu.add(just);
      if( just != null ) menu.add(ortho);
      if( weakOrtho != null ) menu.add(weakOrtho);
      if( just != null ) menu.add(warp);
      menu.add(lens);
    }
  }

  private final Map<String, TilesetCommands> tilesetCommands =
      new LinkedHashMap<>();

  private TilesetCommands tilesetCommands(String name) {
    return tilesetCommands.computeIfAbsent(name, TilesetCommands::new);
  }

  private final Command export =
      new Exporter(this, "export", "Export PNG ...", false);
  private final Command exportWithTracks =
      new Exporter(this, "export-with", "Export PNG with tracks ...", true);

  private final Cmd newFile = simple("new", "New",
      self -> self.files.newCommand());

  private final Cmd open = simple("open", "Open ...",
      self -> self.files.openCommand());

  private final Cmd saveAll = simple("save", "Save",
      self -> self.files.saveAllCommand());

  private final Cmd revert = simple("revert", "Reread all from disk",
      self -> self.files.revertCommand());

  private final Cmd quit = simple("quit", "Quit",
      self -> self.window.quitCommand());

  private final Cmd copy = check("copy", "Copy",
      self -> self.mapView.copyCommand());

  private final Cmd cut = check("cut", "Cut",
      self -> self.mapView.cutCommand());

  private final Cmd paste = check("paste", "Paste",
      self -> self.mapView.pasteCommand());

  // -------------------------------------------------------------------------

  public void defineKeyBindings(BiConsumer<String, Command> keymap) {
    keymap.accept("Escape", simple("esc", "Escape",
        self -> self.mapView.escapePressed()));

    // Letters with Ctrl, in alphabetical order
    keymap.accept("C-C", copy);
    keymap.accept("C-I", reverse);
    keymap.accept("C-L", repaint);
    keymap.accept("C-N", newFile);
    keymap.accept("C-O", open);
    keymap.accept("C-R", revert);
    keymap.accept("C-S", saveAll);
    keymap.accept("C-V", paste);
    keymap.accept("C-X", cut);
    keymap.accept("C-Y", mapView.undoList.redo.getCommand(this, 1));
    keymap.accept("C-Z", mapView.undoList.undo.getCommand(this, 1));

    // Without Ctrl, but possibly shifted, in QUERTY order
    keymap.accept("S-F2", simple("GC", "Force GC", self -> HeapDebugCommand.run(mapView.tiles)));
    keymap.accept("F2", Toggles.TILEGRID.command(this));
    keymap.accept("F3", new NearestNodeDebugTool(this));
    keymap.accept("F4", new TilecacheDebugTool(this));
    keymap.accept("S-F5", Toggles.DOWNLOAD.command(this));
    keymap.accept("F5", Toggles.SUPERSAMPLE.command(this));
    keymap.accept("F6", Toggles.DARKEN_MAP.command(this));
    keymap.accept("F7", Toggles.SHOW_LABELS.command(this));
    keymap.accept("F8", Toggles.CURVATURE.command(this));
    keymap.accept("F9", Toggles.EXT_BOUNDS.command(this));
    keymap.accept("F10", Toggles.CROSSHAIRS.command(this));
    keymap.accept("F11", Toggles.MAIN_TRACK.command(this));
    keymap.accept("F12", explore);

    keymap.accept("1", zoom100);
    keymap.accept("2", rotate);
    keymap.accept("-", zoomOut);
    keymap.accept("+", zoomIn);

    keymap.accept("Tab", toggleFilePane);
    keymap.accept("Q", quickwarp);
    keymap.accept("W", tilesetCommands("google").warp);
    keymap.accept("S-W", tilesetCommands("bing").lens);
    keymap.accept("E", tilesetCommands("google").weakOrtho);
    keymap.accept("S-E", tilesetCommands("google").lens);
    keymap.accept("R", tilesetCommands("osm").ortho);
    keymap.accept("S-R", tilesetCommands("osm").lens);
    keymap.accept("S-T", tilesetCommands("openrail").lens);
    keymap.accept("U", teleport);
    keymap.accept("S-U", unzoom);
    keymap.accept("[", lensMinus);
    keymap.accept("]", lensPlus);
    keymap.accept("\\", toggleTilesetPane);

    keymap.accept("A", magicTool);
    keymap.accept("S", straightTool);
    keymap.accept("D", slewTool);
    keymap.accept("G", downloadTile);
    keymap.accept("L", lens);

    keymap.accept("Z", zoomTool);
    keymap.accept("X", move);
    keymap.accept("C", trackTool);
    keymap.accept("V", weakTrackTool);
    keymap.accept("B", boundTool);
    keymap.accept("N", localBoundTool);
    keymap.accept("<", squeeze);
    keymap.accept(">", stretch);

    keymap.accept("Space", refresh);
  }

  public void defineMenu(IMenu menu) {
    var files = menu.addSubmenu("File");
    files.add(open);
    files.add(newFile);
    files.add(saveAll);
    files.add(revert);
    files.addSeparator();
    files.add(export);
    files.add(exportWithTracks);
    files.addSeparator();
    files.add(quit);

    var edit = menu.addSubmenu("Edit");
    mapView.undoList.addToEditMenu(this, edit);
    edit.add(cut);
    edit.add(copy);
    edit.add(paste);
    edit.addSeparator();
    edit.add(straightTool);
    edit.add(trackTool);
    edit.add(weakTrackTool);
    edit.add(slewTool);
    edit.add(magicTool);
    edit.add(boundTool);
    edit.addSeparator();
    edit.add(reverse);

    var view = menu.addSubmenu("View");
    view.add(toggleFilePane);
    view.add(toggleTilesetPane);
    view.addSeparator();
    var zoom = view;//.addSubmenu("Zoom / Projection");
    zoom.add(zoomIn);
    zoom.add(zoomOut);
    zoom.add(zoom100);
    zoom.add(unzoom);
    zoom.add(squeeze);
    zoom.add(stretch);
    zoom.addSeparator();
    zoom.add(rotate);
    zoom.add(teleport);
    view.addSeparator();

    view.add(Toggles.MAIN_TRACK.command(this));
    view.add(Toggles.CROSSHAIRS.command(this));
    view.add(Toggles.EXT_BOUNDS.command(this));
    view.add(Toggles.CURVATURE.command(this));
    view.add(Toggles.SHOW_LABELS.command(this));
    view.add(Toggles.DARKEN_MAP.command(this));
    view.add(Toggles.SUPERSAMPLE.command(this));
    view.add(Toggles.DOWNLOAD.command(this));
    view.add(Toggles.TILEGRID.command(this));
    view.add(repaint);
    view.add(refresh);

    var tools = menu.addSubmenu("Tools");
    tools.add(straightTool);
    tools.add(trackTool);
    tools.add(weakTrackTool);
    tools.add(slewTool);
    tools.add(magicTool);
    tools.add(boundTool);
    tools.add(localBoundTool);
    tools.addSeparator();
    tools.add(quickwarp);
    tools.add(lens);
    tools.add(zoomTool);
    tools.add(move);
    tools.add(explore);

    var byPointer = menu.addSubmenu("Actions at pointer");
    byPointer.add(downloadTile);
  }

  public void defineTilesetMenu(Tileset tiles, IMenu menu) {
    tilesetCommands(tiles.name).defineMenu(menu);
  }

  // -------------------------------------------------------------------------

  private Cmd simple(String codename, String niceName,
      Consumer<Commands> action) {
    return new Cmd(codename, niceName, self -> () -> action.accept(self));
  }

  private Cmd check(String codename, String niceName,
      Function<Commands, Runnable> action) {
    return new Cmd(codename, niceName, action);
  }

  private final class Cmd extends Command {
    final Function<Commands, Runnable> action;
    Cmd(String codename, String niceName,
        Function<Commands, Runnable> action) {
      super(Commands.this, codename, niceName);
      this.action = action;
    }

    @Override
    public boolean makesSenseNow() {
      return action.apply(owner) != null;
    }

    @Override
    public void invoke() {
      var toRun = action.apply(owner);
      if( toRun == null )
        SwingUtils.beep();
      else
        toRun.run();
    }
  }

}
