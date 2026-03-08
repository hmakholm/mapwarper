package net.makholm.henning.mapwarper.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.track.VisibleTrackData;
import net.makholm.henning.mapwarper.util.BadError;

public abstract class Tool extends Command implements MouseAction {

  /**
   * This is non-final such that subclasses can install their own
   * cursors, but that shouldn't change routinely. For temporary
   * changes, a cursor can instead be returned from
   * {@link ToolResponse#cursor()}.
   *
   * The value should either be the forename (without extension)
   * of a PNG file in the ...mapwarper.gui package source directory,
   * or a name recognized by the {@link SwingCursor} class.
   */
  public String toolCursor;

  public void escapeAction() {
    switchToPreviousTool();
  }

  public void enterAction() {
    // nothing by default
  }

  protected Tool(Commands owner, String codename, String niceName) {
    super(owner, codename, niceName);
    toolCursor = "DEFAULT";
  }

  public int retouchDisplayFlags(int orig) {
    return orig;
  }

  public void retouchTrackData(VisibleTrackData vdt) { }

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

  public ToolResponse outsideWindowResponse() {
    return NO_RESPONSE;
  }

  protected final boolean mouseHeld(int modifiers) {
    return hairy.mouseHeld(modifiers);
  }

  protected final boolean shiftHeld(int modifiers) {
    return hairy.shiftHeld(modifiers);
  }

  protected final boolean ctrlHeld(int modifiers) {
    return hairy.ctrlHeld(modifiers);
  }

  protected final boolean altHeld(int modifiers) {
    return hairy.altHeld(modifiers);
  }

  protected final boolean altHeld(int mod1, int mod2) {
    return hairy.altHeld(mod1 | mod2);
  }

  protected final boolean isQuickCommand(int modifiers) {
    return hairy.isQuickBitSet(modifiers);
  }

  public static final MouseAction DRAG_THE_MAP = (_,_) -> _ -> {
    throw BadError.of("DRAG_THE_MAP is recognized by ==; this is never called.");
  };

  public static final ToolResponse NO_RESPONSE = _ -> {};

  // -------------------------------------------------------------------------

  public void whenSelected() {
    // Nothing by default
  }

  public void whenDeselected() {
    // Nothing by default
  }

  private Point mouseLocalAtKeypress;
  private Tool toolAtKeypress;

  @Override
  public final void invokeByKey() {
    mouseLocalAtKeypress = mapView.mouseLocal;
    var prev = mapView().currentTool;
    super.invokeByKey();
    if( prev != this && mapView().currentTool == this )
      toolAtKeypress = prev;
    else
      toolAtKeypress = null;
  }

  protected final boolean isTempTool() {
    return mapView.hairy.isCommandKeyDown(this);
  }

  protected ToolResponse simpleKeyAction(Point pos, int modifiers) {
    return null;
  }
  protected Tool previousTool;

  @Override
  public void invoke() {
    if( mapView().currentTool != this ) {
      previousTool = mapView().currentTool;
      mapView().selectTool(this);
    } else {
      whenSelected();
    }
  }

  @Override
  public boolean invocationKeyReleased(boolean anythingDone, int modifiers) {
    Tool prev = toolAtKeypress;
    toolAtKeypress = null;
    if( mapView.currentTool != this || prev == null ) {
      return false;
    } else if( anythingDone ) {
      System.err.println("[resume "+prev.codename+"]");
      mapView.selectTool(prev);
      return true;
    } else {
      modifiers = hairy.setQuickBit(modifiers);
      var a = simpleKeyAction(mouseLocalAtKeypress, modifiers);
      if( a == null || a == NO_RESPONSE ) {
        return false;
      } else {
        System.err.println("[quickcmd "+codename+"; resuming "+prev.codename+"]");
        a.execute(ExecuteWhy.QUICKTOOL);
        mapView.selectTool(prev);
        return true;
      }
    }
  }

  protected final boolean switchToPreviousTool() {
    if( previousTool != null && canEscapeBackTo(previousTool) ) {
      mapView().selectTool(previousTool);
      previousTool = null;
      return true;
    } else {
      return false;
    }
  }

  protected boolean canEscapeBackTo(Tool other) {
    return true;
  }

  // -------------------------------------------------------------------------

  public void activeFileChanged() { }

  @Override
  public Boolean getMenuSelected() {
    return this == mapView().currentTool;
  }

  // -------------------------------------------------------------------------


  private Command selectionlessAlias;
  public Command selectionlessAlias() {
    if( selectionlessAlias == null ) {
      selectionlessAlias = new Command(owner,codename+"%menu", niceName) {
        @Override
        public void invoke() {
          Tool.this.invoke();
        }
      };
    }
    return selectionlessAlias;
  }

  // -------------------------------------------------------------------------

  private final Map<Integer, Command> quickCommands = new LinkedHashMap<>();

  private Command makeQuickCommand(int modifier,
      String quickCodename, String niceName, BooleanSupplier menuCheckmark) {
    return quickCommands.computeIfAbsent(modifier, _ ->
    new Command(owner, quickCodename, niceName) {
      private ToolResponse tr() {
        return mouseResponse(mapView.mouseLocal, hairy.setQuickBit(modifier));
      }
      @Override
      public Boolean getMenuSelected() {
        return menuCheckmark == null ? null : menuCheckmark.getAsBoolean();
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

  protected final Command bareQuickCommand(String codename, String niceName,
      BooleanSupplier menuCheckmark) {
    return makeQuickCommand(0, codename, niceName, menuCheckmark);
  }

  protected final Command altQuickCommand(String codename, String niceName,
      BooleanSupplier menuCheckmark) {
    return makeQuickCommand(hairy.setAltBit(0), codename, niceName,
        menuCheckmark);
  }

}
