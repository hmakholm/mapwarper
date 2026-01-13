package net.makholm.henning.mapwarper.gui.swing;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JComponent;
import javax.swing.JMenuItem;

import net.makholm.henning.mapwarper.gui.Commands;
import net.makholm.henning.mapwarper.gui.MapView;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.util.BadError;

@SuppressWarnings("serial")
public abstract class Command {

  public final Commands owner;
  public final String codename;
  public final String niceName;

  boolean hasAltBinding;
  Action action;

  public Command(Commands owner, String codename, String niceName) {
    this.owner = owner;
    this.codename = codename;
    this.niceName = niceName;

    if( owner.commandRegistry.containsKey(codename) )
      throw BadError.of("Command '%s' seems to be registered twice.", codename);
    owner.commandRegistry.put(codename, this);
  }

  public final MapView mapView() {
    return owner.mapView;
  }

  protected final ProjectionWorker translator() {
    return mapView().translator();
  }

  protected final SegmentChain editingChain() {
    return mapView().editingChain;
  }

  protected final FileContent activeFileContent() {
    return owner.files.activeFile().content();
  }

  public boolean makesSenseNow() {
    return true;
  }

  public abstract void invoke();

  final Action makeAction() {
    return new AbstractAction(niceName) {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean invokedFromMenu = niceName.equals(e.getActionCommand());
        if( !invokedFromMenu &&
            (e.getModifiers() & ActionEvent.ALT_MASK) != 0 &&
            !hasAltBinding ) {
          System.err.println("[ignoing spurious "+codename+"]");
          return;
        }
        owner.swing.whenInvokingCommand(invokedFromMenu);
        debugTraceInvoke();
        invoke();
        owner.swing.refreshScene();
      }
    };
  }

  void debugTraceInvoke() {
    System.err.println("["+codename+"]");
  }

  public JMenuItem makeMenuItem() {
    JMenuItem result = new JMenuItem(getAction());
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
