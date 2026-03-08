package net.makholm.henning.mapwarper.gui.swing;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;

import net.makholm.henning.mapwarper.gui.Commands;
import net.makholm.henning.mapwarper.gui.MapView;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.util.BadError;

public abstract class Command {

  private final SwingMapView swing;

  public final Commands owner;
  public final MapView mapView;
  public final String codename;
  public final String niceName;

  KeyStroke keybinding;
  Action action;

  public Command(Commands owner, String codename, String niceName) {
    this.owner = owner;
    this.mapView = owner.mapView;
    this.swing = (SwingMapView)mapView.hairy;
    this.codename = codename;
    this.niceName = niceName != null ? niceName : "("+codename+")";

    if( owner.commandRegistry.containsKey(codename) )
      throw BadError.of("Command '%s' seems to be registered twice.", codename);
    owner.commandRegistry.put(codename, this);
  }

  protected final void beep() {
    owner.window.beep();
  }

  public final MapView mapView() {
    return owner.mapView;
  }

  protected final ProjectionWorker translator() {
    return mapView.translator();
  }

  protected final SegmentChain editingChain() {
    return mapView.editingChain;
  }

  protected final FileContent activeFileContent() {
    return owner.files.activeFile().content();
  }

  public boolean makesSenseNow() {
    return true;
  }

  protected void invokeByKey(char key) {
    swing.tempTool.disable();
    invoke();
    if( key != KeyEvent.CHAR_UNDEFINED )
      swing.tempTool = new TempToolReleaser(key, this);
  }

  public abstract void invoke();

  /**
   * @return true if we need to refresh the displayed situation
   */
  public boolean invocationKeyReleased(boolean anythingDone, int modifiers) {
    return false;
  }

  final Action makeAction() {
    return new AbstractAction(niceName) {
      @Override
      public void actionPerformed(ActionEvent e) {
        var mainFrame = (MainFrame)owner.window;
        mainFrame.anyUserInputYet = true;
        String swingstring = e.getActionCommand();
        if( swingstring == null ) {
          // This seems to happen for non-character keys such as F-keys
          swingstring = "("+keybinding+")";
        }
        if( niceName.equals(swingstring) ) {
          // This happens when the invocation comes via a menu
          swing.whenInvokingCommand(true);
          debugTraceInvoke();
          invoke();
        } else if( (e.getModifiers() & ActionEvent.ALT_MASK) != 0 &&
            keybinding != null &&
            (keybinding.getModifiers() & InputEvent.ALT_DOWN_MASK) == 0 ) {
          System.err.println("[ignoing spurious "+codename+"] <"+keybinding+">");
          return;
        } else if( swing.possiblyRepeatingKey.equals(swingstring) &&
            !codename.endsWith("...")) {
          // ignore auto-repeating keys where we haven't seen a key
          // release event first
          return;
        } else {
          swing.possiblyRepeatingKey = swingstring;
          swing.whenInvokingCommand(false);
          debugTraceInvoke();
          if( swingstring != null && swingstring.length() == 1 ) {
            invokeByKey(swingstring.charAt(0));
          } else {
            invoke();
          }
        }
        swing.refreshScene();
      }
    };
  }

  void debugTraceInvoke() {
    System.err.println("["+codename+"]");
  }

  protected Boolean getMenuSelected() {
    // Returning null means this never shows a checkmark
    return null;
  }

  public JMenuItem makeMenuItem() {
    JMenuItem result;
    Boolean selectedState = getMenuSelected();
    if( selectedState != null ) {
      result = new JRadioButtonMenuItem(getAction());
      result.setSelected(selectedState);
    } else {
      result = new JMenuItem(getAction());
    }
    if( !makesSenseNow() )
      result.setEnabled(false);
    return result;
  }

  public Action getAction() {
    if( action == null )
      action = makeAction();
    return action;
  }

  final void defineInActionMap(JComponent c) {
    ActionMap actionMap = c.getActionMap();
    if( actionMap.get(codename) == null )
      actionMap.put(codename, getAction());
  }

}
