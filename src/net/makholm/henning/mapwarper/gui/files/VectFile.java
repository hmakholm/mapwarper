package net.makholm.henning.mapwarper.gui.files;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Function;

import net.makholm.henning.mapwarper.gui.UndoList;
import net.makholm.henning.mapwarper.gui.swing.GuiMain;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.track.VectParser;
import net.makholm.henning.mapwarper.track.VectWriter;
import net.makholm.henning.mapwarper.util.NiceError;
import net.makholm.henning.mapwarper.util.PokePublisher;
import net.makholm.henning.mapwarper.util.Regexer;
import net.makholm.henning.mapwarper.util.XyTree;

public class VectFile {

  public static final String EXTENSION = ".vect";

  public static final String cVectfile = "(\\S+\\"+EXTENSION+")";

  private final FSCache owner;
  public final Path path;
  public final PokePublisher changePokes;

  public String error;

  VectFile(FSCache owner, Path path) {
    this.owner = owner;
    this.path = path;
    this.changePokes = new PokePublisher(this.toString());
  }

  @Override
  public String toString() {
    return path != null ? path.toString() : "(unnamed file)";
  }

  public String shortname() {
    return path != null ? path.getFileName().toString() : "(unnamed)";
  }

  private FileContent readAs;
  private FileContent onDisk;
  private FileContent currentContent;
  private boolean isModified;

  public synchronized void setContentHarshly(FileContent content) {
    onDisk = content;
    currentContent = content;
    error = null;
  }

  public synchronized FileContent content() {
    if( currentContent == null ) {
      currentContent = FileContent.EMPTY;
      if( path == null ) {
        onDisk = FileContent.EMPTY;
        // This is an anonymous empty file, so EMPTY is exactly right
      } else if( !Files.exists(path) ) {
        error = "This file does not exist.";
      } else if( !Files.isRegularFile(path) ) {
        error = "This is not a regular file.";
      } else {
        try( var br = new BufferedReader(new FileReader(path.toFile())) ) {
          var parser = new VectParser(path);
          for( String line; (line = br.readLine()) != null; ) {
            parser.giveLine(line);
          }
          onDisk = readAs = parser.result();
          currentContent = readAs;
        } catch( IOException e ) {
          error = "Reading failed: "+e.getMessage();
        } catch( NiceError e ) {
          String msg = e.getMessage();
          if( new Regexer(msg).match("[0-9]+:.*") )
            msg = "Line "+msg;
          error = msg;
        }
      }
      sendChangePoke();
    }
    return currentContent;
  }

  public synchronized void rewriteContent(UndoList undo, String undoDesc,
      Function<FileContent, FileContent> f) {
    synchronized(this) {
      FileContent newContent = f.apply(currentContent);
      if( newContent.equals(currentContent) ) return;
      undo.pushFileChange(undoDesc, this, currentContent,  newContent);
      currentContent = newContent;
      sendChangePoke();
    }
  }

  public synchronized boolean changeContentNoUndo(FileContent oldContent,
      FileContent newContent) {
    synchronized(this) {
      if( oldContent != null && !content().equals(oldContent) )
        return false;
      currentContent = newContent;
      sendChangePoke();
      return true;
    }
  }

  /**
   * Content will only actually be forgotten if the undoMap is non-null.
   */
  public synchronized void forgetContent(Map<Path, FileContent> undoMap) {
    if( path == null ) return;
    if( currentContent == null ) {
      onDisk = null;
      readAs = null;
    } else {
      if( !currentContent.equals(onDisk) ) {
        if( undoMap == null ) return;
        undoMap.put(path, currentContent);
      }
      currentContent = null;
      onDisk = null;
      readAs = null;
      sendChangePoke();
    }
    if( error != null ) {
      error = null;
      sendChangePoke();
    }
  }

  public synchronized void trySaving() throws NiceError {
    if( path == null )
      throw NiceError.of("This file has no name we can save to");
    Path writingTo = path.resolveSibling(
        path.getFileName() + ".new");
    try( PrintStream ps = new PrintStream(writingTo.toFile()) ) {
      new VectWriter(path.getParent(), readAs, currentContent).write(ps);
    } catch( IOException e ) {
      throw NiceError.of("Could not write %s: %s",
          writingTo.getFileName(), e.getMessage());
    }
    try {
      Files.move(writingTo, path, StandardCopyOption.REPLACE_EXISTING);
      onDisk = currentContent;
      sendChangePoke();
    } catch( IOException e ) {
      throw NiceError.of("Could not move %s into place: %s",
          writingTo.getFileName(), e);
    }
  }

  public XyTree<ChainRef<TrackNode>> allShownNodes() {
    var content = content();
    var got = content.nodeTree.get();
    if( XyTree.isEmpty(got) ) {
      var joiner = XyTree.<ChainRef<TrackNode>>leftWinsJoin();
      for( Path bound : content.usebounds() ) {
        VectFile vf = owner.getFile(bound);
        got = joiner.union(got, vf.content().nodeTree.get());
      }
    }
    return got;
  }

  synchronized void ensureErrorIsCurrent() {
    if( error != null && !FileContent.EMPTY.equals(currentContent) ) {
      error = null;
      currentContent = null;
    }
    content();
  }

  boolean showBoxOnError(GuiMain main) {
    ensureErrorIsCurrent();
    String err = error;
    if( err != null ) {
      main.showErrorBox("Cannot open %s: %s",
          path.getFileName(), err);
      return true;
    } else {
      return false;
    }
  }

  boolean okToForget() {
    return !isModified && changePokes.isEmpty();
  }

  synchronized void contentHasBeenSaved(FileContent content) {
    if( content.equals(currentContent) && path == null ) {
      onDisk = currentContent;
      sendChangePoke();
    }
  }

  /**
   * Must be called with the lock held.
   */
  private void sendChangePoke() {
    boolean wasModified = isModified;
    isModified = currentContent != null &&
        !currentContent.equals(onDisk) &&
        !(error != null && onDisk == null);
    if( isModified != wasModified ) {
      synchronized( owner.modifiedFiles ) {
        if( isModified )
          owner.modifiedFiles.add(this);
        else
          owner.modifiedFiles.remove(this);
      }
      owner.modifiedFilesPokes.poke();
    }
    changePokes.poke();
  }

  public boolean isModified() {
    return isModified;
  }

}
