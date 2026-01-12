package net.makholm.henning.mapwarper.gui.swing;

import java.awt.Cursor;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.Commands;
import net.makholm.henning.mapwarper.gui.MouseAction;
import net.makholm.henning.mapwarper.util.BadError;

public abstract class Tool extends Command implements MouseAction {

  /**
   * This is non-final such that subclasses can install their own
   * cursors, but that shouldn't change routinely. For temporary
   * changes, a cursor can instead be returned from
   * {@link ToolResponse#cursor()}.
   */
  public Cursor toolCursor;

  public void escapeAction() {
    switchToPreviousTool();
  }

  protected Tool(Commands owner, String codename, String niceName) {
    super(owner, codename, niceName);
    toolCursor = Cursor.getDefaultCursor();
  }

  /**
   * The main point to implement for at tool.
   *
   * @see MouseAction#mouseResponse(Point, int)
   */
  @Override
  public abstract ToolResponse mouseResponse(Point pos, int modifiers);

  /**
   * Overriding this gives the tool an opportunity to respond differently
   * to drags. The returned object's {@link MouseAction#mouseResponse} will
   * be called <em>instead of</em> the tools's own during a drag, to provide
   * both feedback during the drag and an action for when the drag ends.
   */
  public MouseAction drag(Point startPoint, int modifiers) {
    return this;
  }

  public static boolean mouseHeld(int flags) {
    return (flags & InputEvent.BUTTON1_DOWN_MASK) != 0;
  }

  public static boolean shiftHeld(int flags) {
    return (flags & InputEvent.SHIFT_DOWN_MASK) != 0;
  }

  public static boolean ctrlHeld(int flags) {
    return (flags & InputEvent.CTRL_DOWN_MASK) != 0;
  }

  public static boolean altHeld(int flags) {
    return (flags & InputEvent.ALT_DOWN_MASK) != 0;
  }

  public static boolean isQuickCommand(int flags) {
    return (flags & QUICK_COM_MASK) != 0;
  }

  public static final MouseAction DRAG_THE_MAP = (p,m) -> why -> {
    throw BadError.of("DRAG_THE_MAP is recognized by ==; this is never called.");
  };

  public static final ToolResponse NO_RESPONSE = why -> {};

  // -------------------------------------------------------------------------

  private static final Map<String, Cursor> cursors = new LinkedHashMap<>();

  public static Cursor loadCursor(String name) {
    synchronized( cursors ) {
      return cursors.computeIfAbsent(name, Tool::loadCursorInternal);
    }
  }

  private static Cursor loadCursorInternal(String name) {
    BufferedImage img = SwingUtils.loadBundledImage(name).orElse(null);
    if( img == null ) return Cursor.getDefaultCursor();
    return Toolkit.getDefaultToolkit().createCustomCursor(img,
        new java.awt.Point(img.getWidth()/2, img.getHeight()/2),
        name);
  }

  public void sanitizeEditingStateWhenSelected() {
    // Nothing by default
  }

  private Tool previousTool;
  private boolean arrivedBySwitchingBack;
  private boolean mayCancelWhenReselecting;

  @Override
  public void invoke() {
    if( mapView().currentTool != this ) {
      previousTool = mapView().currentTool;
      arrivedBySwitchingBack = false;
      mayCancelWhenReselecting = false;
      mapView().selectTool(this);
    } else {
      sanitizeEditingStateWhenSelected();
    }
  }

  @Override
  public boolean specialKeypressAction() {
    if( mapView().currentTool == this &&
        !arrivedBySwitchingBack &&
        mayCancelWhenReselecting ) {
      switchToPreviousTool();
      return true;
    } else {
      return false;
    }
  }

  public void enableSameKeyCancel() {
    mayCancelWhenReselecting = true;
  }

  protected final void switchToPreviousTool() {
    if( previousTool != null && canEscapeBackTo(previousTool) ) {
      previousTool.arrivedBySwitchingBack = true;
      mapView().selectTool(previousTool);
      previousTool = null;
    }
  }

  protected boolean canEscapeBackTo(Tool other) {
    return true;
  }

  public void activeFileChanged() { }

  @Override
  public JMenuItem makeMenuItem() {
    var result = new JRadioButtonMenuItem(getAction());
    result.setSelected(this == mapView().currentTool);
    return result;
  }

  // -------------------------------------------------------------------------

  private static final int QUICK_COM_MASK = 1 << 31;

  private final Map<Integer, Command> quickCommands = new LinkedHashMap<>();

  private Command makeQuickCommand(int modifier, String niceName) {
    return quickCommands.computeIfAbsent(modifier, m0 ->
    new Command(owner, codename + "%" + modifier, niceName) {
      private ToolResponse tr() {
        return owner.swing.quickToolResponse(Tool.this,
            modifier | QUICK_COM_MASK);
      }
      @Override
      public boolean makesSenseNow() {
        return tr() != NO_RESPONSE;
      }
      @Override
      public void invoke() {
        tr().execute(ExecuteWhy.QUICKTOOL);
      }
    });
  }

  protected final Command bareQuickCommand(String niceName) {
    return makeQuickCommand(0, niceName);
  }

  protected final Command altQuickCommand(String niceName) {
    return makeQuickCommand(InputEvent.ALT_DOWN_MASK, niceName);
  }

}
