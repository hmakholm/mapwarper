package net.makholm.henning.mapwarper.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import net.makholm.henning.mapwarper.gui.files.VectFile;
import net.makholm.henning.mapwarper.gui.swing.Command;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.gui.track.FileContent;

public class UndoList {

  public static final int MAX_DEPTH = 100;

  public final MapView owner;

  public UndoList(MapView owner) {
    this.owner = owner;
  }

  public void pushItem(UndoItem item) {
    redo.clear();
    undo.push(item);
  }

  public void pushFileChange(String desc, VectFile file,
      FileContent oldContent, FileContent newContent) {
    pushItem(new FileChange(desc, file,
        oldContent, newContent, new Teleporter(owner)));
  }

  public final Stack undo = new Stack();
  public final Stack redo = new Stack();

  public interface UndoItem {
    String undoDesc();

    /**
     * Returns a item that will <em>redo</em> the change, null if something
     * went wrong and an error has already been shown.
     */
    UndoItem apply(MapView mapView);
  }

  public final class Stack {
    private final ArrayList<Command> commands = new ArrayList<>();
    private final ArrayDeque<UndoItem> deque = new ArrayDeque<>();
    private UndoItem[] array;

    public Command getCommand(Commands owner, int howmany) {
      while( commands.size() < howmany )
        commands.add(new UndoCommand(owner, commands.size()+1, this == undo));
      return commands.get(howmany-1);
    }

    private UndoItem get(int i) {
      if( array == null ) array = deque.toArray(new UndoItem[0]);
      return array[i];
    }

    private void push(UndoItem item) {
      array = null;
      if( deque.size() >= MAX_DEPTH ) deque.removeLast();
      deque.addFirst(item);
    }

    private UndoItem pop() {
      array = null;
      return deque.isEmpty() ? null : deque.removeFirst();
    }

    private void clear() {
      array = null;
      deque.clear();
    }

    private int size() {
      return deque.size();
    }
  }

  public void addToEditMenu(Commands owner, IMenu menu) {
    menu.add(undo.getCommand(owner, 1));
    menu.add(redo.getCommand(owner, 1));

    if( undo.size() <= 1 && redo.size() <= 1 )
      return;
    IMenu history = menu.addSubmenu("Undo history");

    int showRedos, showUndos;
    int maxEachSide = 25;
    do {
      maxEachSide--;
      showRedos = Math.min(maxEachSide, redo.size());
      showUndos = Math.min(maxEachSide, undo.size());
    } while( showRedos + showUndos > 30 );

    for( int i = showRedos; i>=1; i-- )
      history.add(redo.getCommand(owner, i));
    if( showRedos != 0 && showUndos != 0 )
      history.addSeparator();
    for( int i = 1; i<=showUndos; i++ )
      history.add(undo.getCommand(owner, i));
  }

  public record FileChange(String undoDesc, VectFile file,
      FileContent before, FileContent after, Teleporter teleport)
  implements UndoItem {
    @Override
    public UndoItem apply(MapView mapView) {
      VectFile vf = file;
      if( vf.path != null ) {
        vf = mapView.files.cache.getFile(vf.path);
      }
      FileContent after = after();
      while( !vf.changeContentNoUndo(after, before) ) {
        String msg = String.format(Locale.ROOT,
            "Cannot undo/redo '%s' cleanly because %s seems to have changed "
                + "without leaving an undo record. "
                + "Restore the saved content anyway?",
                undoDesc, vf);
        int result = JOptionPane.showConfirmDialog(mapView.window, msg,
            "Inconsistency warning", JOptionPane.OK_CANCEL_OPTION);
        if( result != JOptionPane.OK_OPTION )
          return null;
        after = vf.content();
      }
      mapView.files.setActiveFile(vf);

      // If there's just one chain changed, make that active
      var newChains = before.chainsCopy();
      for( var c : after.chains() ) newChains.remove(c);
      if( newChains.size() == 1 )
        mapView.setEditingChain(newChains.iterator().next());

      teleport.apply();
      return new FileChange(undoDesc, vf, after, before, teleport);
    }
  }

  public static class SkippedUndoItem implements UndoItem {
    @Override
    public String undoDesc() {
      return "(skipped item)";
    }

    @Override
    public UndoItem apply(MapView mapView) {
      return this;
    }
  }

  private class UndoCommand extends Command {
    final boolean isUndo;
    final int howmany;
    final String verb;

    UndoCommand(Commands owner, int howmany, boolean isUndo) {
      super(owner,
          (isUndo ? "undo." : "redo.") + howmany,
          "(Dynamically named undo)");
      this.verb = isUndo ? "Undo" : "Redo";
      this.howmany = howmany;
      this.isUndo = isUndo;
    }

    @Override
    public void invoke() {
      for(int i=0; i<howmany; i++ ) {
        UndoItem toUndo = (isUndo ? undo : redo).pop();
        if( toUndo == null ) {
          SwingUtils.beep();
          return;
        }
        System.err.printf("[%s %d/%d: %s]\n",
            verb, i+1, howmany, toUndo.undoDesc());
        UndoList.this.owner.setEditingChain(null);
        UndoItem reversed = toUndo.apply(UndoList.this.owner);
        if( reversed == null ) return;
        (isUndo ? redo : undo).push(reversed);
      }
    }

    @Override
    public JMenuItem makeMenuItem() {
      var fromStack = isUndo ? undo : redo;
      JMenuItem result;
      if( fromStack.size() < howmany ) {
        result = new JMenuItem(verb);
        result.setEnabled(false);
      } else {
        result = new JMenuItem(verb+" "+fromStack.get(howmany-1).undoDesc());
        result.addActionListener(e -> {
          invoke();
          owner.swing.refreshScene();
        });
      }
      if( getAction().getValue(Action.ACCELERATOR_KEY)
          instanceof KeyStroke ks )
        result.setAccelerator(ks);
      return result;
    }
  }
}
