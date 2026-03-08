package net.makholm.henning.mapwarper.gui.swing;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;

import net.makholm.henning.mapwarper.gui.Command;
import net.makholm.henning.mapwarper.gui.ToggleCommand;
import net.makholm.henning.mapwarper.gui.hairy.CommandCompanion;

class SwingCommand implements CommandCompanion {

  final Command logic;
  final MainFrame window;
  final SwingMapView swing;

  KeyStroke keybinding;
  Action action;

  SwingCommand(MainFrame window, Command logic) {
    this.window = window;
    this.logic = logic;
    this.swing = window.swingMapView;
  }


  @Override
  public boolean mouseHeld(int flags) {
    return (flags & InputEvent.BUTTON1_DOWN_MASK) != 0;
  }

  @Override
  public boolean shiftHeld(int flags) {
    return (flags & InputEvent.SHIFT_DOWN_MASK) != 0;
  }

  @Override
  public boolean ctrlHeld(int flags) {
    return (flags & InputEvent.CTRL_DOWN_MASK) != 0;
  }

  @Override
  public boolean altHeld(int flags) {
    return (flags & InputEvent.ALT_DOWN_MASK) != 0;
  }

  @Override
  public boolean isQuickBitSet(int flags) {
    return (flags & QUICK_COM_MASK) != 0;
  }

  @Override
  public int setQuickBit(int flags) {
    return flags | QUICK_COM_MASK;
  }

  @Override
  public int setAltBit(int flags) {
    return flags | InputEvent.ALT_DOWN_MASK;
  }

  private static final int QUICK_COM_MASK = 1<<31;

  // -------------------------------------------------------------------------

  private void invokeByKey(char key) {
    window.swingMapView.tempTool.disable();
    logic.invokeByKey();
    if( key != KeyEvent.CHAR_UNDEFINED )
      window.swingMapView.tempTool = new TempToolReleaser(key, logic);
  }

  final Action makeAction() {
    return new AbstractAction(logic.niceName) {
      @Override
      public void actionPerformed(ActionEvent e) {
        var mainFrame = window;
        mainFrame.anyUserInputYet = true;
        String swingstring = e.getActionCommand();
        if( swingstring == null ) {
          // This seems to happen for non-character keys such as F-keys
          swingstring = "("+keybinding+")";
        }
        if( swingstring.equals(logic.niceName) ||
            swingstring.equals(logic.overrideMenuItemText()) ) {
          // This happens when the invocation comes via a menu
          swing.whenInvokingCommand(true);
          logic.debugTraceInvoke();
          logic.invoke();
        } else if( (e.getModifiers() & ActionEvent.ALT_MASK) != 0 &&
            keybinding != null &&
            (keybinding.getModifiers() & InputEvent.ALT_DOWN_MASK) == 0 ) {
          System.err.println("[ignoing spurious "+logic.codename+"] <"+
              keybinding+">");
          return;
        } else if( swing.possiblyRepeatingKey.equals(swingstring) &&
            !logic.codename.endsWith("...")) {
          // ignore auto-repeating keys where we haven't seen a key
          // release event first
          return;
        } else {
          swing.possiblyRepeatingKey = swingstring;
          swing.whenInvokingCommand(false);
          logic.debugTraceInvoke();
          if( swingstring != null && swingstring.length() == 1 ) {
            invokeByKey(swingstring.charAt(0));
          } else {
            logic.invoke();
          }
        }
        swing.refreshScene();
      }
    };
  }

  public final JMenuItem makeMenuItem() {
    var action = getAction();
    String overrideText = logic.overrideMenuItemText();
    if( overrideText != null ) {
      var original = action;
      action = new AbstractAction(overrideText) {
        @Override
        public void actionPerformed(ActionEvent arg0) {
          original.actionPerformed(arg0);
        }
      };
      if( original.getValue(Action.ACCELERATOR_KEY) instanceof KeyStroke ks )
        action.putValue(Action.ACCELERATOR_KEY, ks);
    }
    JMenuItem result;
    Boolean selectedState;
    if( logic instanceof ToggleCommand toggle ) {
      result = new JCheckBoxMenuItem(action);
      result.setSelected(toggle.getCurrentState());
      if( !toggle.dismissPopupMenuImmediately() )
        result.putClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick",
            Boolean.TRUE);
    } else if( (selectedState = logic.getMenuSelected()) != null ) {
      result = new JRadioButtonMenuItem(action);
      result.setSelected(selectedState);
    } else {
      result = new JMenuItem(action);
    }
    result.setEnabled(logic.makesSenseNow());
    return result;
  }

  public Action getAction() {
    if( action == null )
      action = makeAction();
    return action;
  }

  final void defineInActionMap(JComponent c) {
    ActionMap actionMap = c.getActionMap();
    if( actionMap.get(logic.codename) == null )
      actionMap.put(logic.codename, getAction());
  }

}
