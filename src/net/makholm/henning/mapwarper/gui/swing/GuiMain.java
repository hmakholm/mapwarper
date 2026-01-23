package net.makholm.henning.mapwarper.gui.swing;

import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

import net.makholm.henning.mapwarper.gui.Commands;
import net.makholm.henning.mapwarper.gui.MapView;
import net.makholm.henning.mapwarper.gui.files.FSCache;
import net.makholm.henning.mapwarper.gui.files.FilePane;
import net.makholm.henning.mapwarper.tiles.TileContext;
import net.makholm.henning.mapwarper.util.BackgroundThread;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.Regexer;
import net.makholm.henning.mapwarper.util.XyTree;

@SuppressWarnings("serial")
public class GuiMain extends JFrame {

  public final FilePane filePane;
  public final JPanel tilesetPane;
  public final MapView mainLogic;
  public final Commands commands;
  public final JComponent topLevelComponent;

  public final WindowTitle windowTitle;

  private final JSplitPane leftSplitter;
  private final JSplitPane rightSplitter;

  boolean anyUserInputYet;

  /**
   * Used for short administrative actions that must be decoupled from the
   * calling context for reasons of deadlock avoidance.
   */
  public final Executor miscAsyncWork =
      Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "MiscAsyncWork");
        thread.setDaemon(true);
        return thread;
      });

  public static void main(TileContext tiles, List<String> args) {
    System.out.print("Starting GUI ...");
    var frame = new GuiMain(tiles, args.isEmpty() ? null : args.get(0));
    System.out.println();
    frame.setVisible(true);
    frame.setTilesetPaneVisible(true);
  }

  public void showErrorBox(String fmt, Object... params) {
    String msg = String.format(Locale.ROOT, fmt, params);
    JOptionPane.showMessageDialog(this, msg,
        "Error", JOptionPane.ERROR_MESSAGE);
  }

  public boolean showYesCancelBox(String title, String fmt, Object... params) {
    String msg = String.format(Locale.ROOT, fmt, params);
    int result = JOptionPane.showConfirmDialog(this, msg, title,
        JOptionPane.OK_CANCEL_OPTION);
    return result == JOptionPane.OK_OPTION;
  }

  private GuiMain(TileContext tiles, String filearg) {
    setMainIcon();
    setTitle("Mapwarper v3");
    selectInitialSize();

    FSCache fileCache = new FSCache();
    mainLogic = new MapView(this, fileCache, filearg, tiles);
    filePane = mainLogic.files;

    tilesetPane = new TilesetPane(this, tiles);

    windowTitle = new WindowTitle(this, mainLogic);

    leftSplitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
        true, filePane.swing, mainLogic.swing.scrollPane);
    leftSplitter.setBorder(null);
    leftSplitter.setResizeWeight(0);
    if( XyTree.isEmpty(filePane.activeFile().content().nodeTree.get()) )
      leftSplitter.setDividerLocation(SwingFilePane.PREFERRED_WIDTH);
    leftSplitter.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
        this::leftDividerMoved);

    rightSplitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
        true, leftSplitter, tilesetPane);
    rightSplitter.setBorder(null);
    rightSplitter.setResizeWeight(1);

    topLevelComponent = rightSplitter;
    add(topLevelComponent);

    commands = new Commands(mainLogic);
    killInputMaps();
    commands.defineKeyBindings(this::defineKeyBinding);

    mainLogic.swing.repaintFromScratch();

    addComponentListener(new ResizeListener());

    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        quitCommand();
      }
    });

    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
      e.printStackTrace();
      BackgroundThread.scheduleAbort(t.getName(), e, null);
    });
    BackgroundThread.ERRORPOKE.subscribe(() -> {
      EventQueue.invokeLater(filePane::perhapsDeferredShutdown);
    });
  }

  private void selectInitialSize() {
    String s = System.getProperty("mapwarper.geometry");
    if( s != null ) {
      var r = new Regexer(s);
      if( r.match("([0-9]+)x([0-9]+)") ) {
        setSize(r.igroup(1), r.igroup(2));
        return;
      }
    }

    // We want to start maximized, but the right splitter somehow won't
    // initialize properly unless we start by setting at least a wild guess.
    setSize(1000, 700);
    setExtendedState(MAXIMIZED_BOTH);
  }

  /**
   * Dismayingly, Swing tends to ask the map view to repaint itself
   * <em>before</em> the initial size calculations have settled down.
   * In order to get the initial content correctly centered and scaled,
   * we have to repeat {@link MapView#setInitialPosition()} when we see
   * the first resize event of the main frame.
   *
   * Just for belt-and-suspenders we'll suppress that if we don't get
   * any initial resize event before either the user has started
   * interacting or a few seconds have passed. Otherwise a random
   * later resize could lead to an unexpected unzoom.
   */
  private class ResizeListener extends ComponentAdapter {
    final long startedAt = System.nanoTime();
    @Override
    public void componentResized(ComponentEvent comp) {
      removeComponentListener(this);
      if( !anyUserInputYet &&
          System.nanoTime() < startedAt + 5_000_000_000L ) {
        mainLogic.setInitialPosition();
      }
    }
  }

  private void leftDividerMoved(PropertyChangeEvent e) {
    int oldPosition = (Integer)e.getOldValue();
    int newPosition = (Integer)e.getNewValue();
    int delta = newPosition - oldPosition;
    mainLogic.positionX += delta;
    mainLogic.swing.refreshScene();
  }

  private void setMainIcon() {
    SwingUtils.loadBundledImage("mainIcon.png").ifPresent(this::setIconImage);
  }

  private int savedLeftPanePosition = SwingFilePane.PREFERRED_WIDTH;

  public boolean filePaneVisible() {
    return leftSplitter.getDividerLocation() > 10;
  }

  public void setFilePaneVisible(boolean wantVisible) {
    int curpos = leftSplitter.getDividerLocation();
    if( curpos > 10 ) {
      if( !wantVisible ) {
        savedLeftPanePosition = curpos;
        leftSplitter.setDividerLocation(0);
      }
    } else {
      if( wantVisible ) {
        int pos = Math.min(savedLeftPanePosition, getWidth()/3);
        leftSplitter.setDividerLocation(pos);
      }
    }
  }

  public boolean tilesetPaneVisible() {
    return rightSplitter.getDividerLocation() <
        rightSplitter.getMaximumDividerLocation() - 20;
  }

  public void setTilesetPaneVisible(boolean wantVisible) {
    if( wantVisible )
      rightSplitter.setDividerLocation(
          rightSplitter.getMaximumDividerLocation() -
          tilesetPane.getPreferredSize().width - 10);
    else
      rightSplitter.setDividerLocation(1.0);
  }

  public void quitCommand() {
    if( filePane.cache.anyUnsavedChanges() ) {
      int result = JOptionPane.showConfirmDialog(this,
          "There are unsaved files. Save before closing?",
          "Mapwarper", JOptionPane.YES_NO_CANCEL_OPTION);
      switch( result ) {
      case JOptionPane.CANCEL_OPTION:
      case JOptionPane.CLOSED_OPTION:
        return;
      case JOptionPane.YES_OPTION:
        if( !filePane.saveAllCommand() )
          return;
        else
          break;
      case JOptionPane.NO_OPTION:
        break;
      default:
        throw BadError.of("showConfirmDialog returned %d", result);
      }
    }
    dispose();
  }

  private void killInputMaps() {
    var cond = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
    leftSplitter.setInputMap(cond, null);
    rightSplitter.setInputMap(cond, null);
    mainLogic.swing.scrollPane.setInputMap(cond, null);
    topLevelComponent.setInputMap(cond, new InputMap());
  }

  private void defineKeyBinding(String key, Command command) {
    command.defineInActionMap(topLevelComponent);

    final KeyStroke keystroke;
    var re = new Regexer(key);
    if( re.match(".") ) {
      char c = key.toLowerCase(Locale.ROOT).charAt(0);
      keystroke = KeyStroke.getKeyStroke(c);
    } else if( re.match("C-([A-Z])") ) {
      key = "control "+re.group(1);
      keystroke = KeyStroke.getKeyStroke(key);
    } else if( re.match("S-([A-Z]|F[0-9]+)") ) {
      key = "shift "+re.group(1);
      keystroke = KeyStroke.getKeyStroke(key);
    } else if( re.match("M-([A-Z]|F[0-9]+)") ) {
      key = "alt "+re.group(1);
      keystroke = KeyStroke.getKeyStroke(key);
    } else if( re.match("[A-Z][a-z]+|F[0-9]+") ) {
      keystroke = KeyStroke.getKeyStroke(re.full.toUpperCase(Locale.ROOT));
    } else {
      throw BadError.of("Unrecognized key binding syntax '%s'", key);
    }
    if( keystroke == null )
      throw BadError.of("Swing produced null KeyStroke for '%s'", key);

    command.keybinding = keystroke;
    command.getAction().putValue(Action.ACCELERATOR_KEY, keystroke);

    InputMap inputMap = topLevelComponent.getInputMap(
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    // System.err.println("Define "+command.codename+" for keystroke "+keystroke);
    inputMap.put(keystroke, command.codename);
  }

}
