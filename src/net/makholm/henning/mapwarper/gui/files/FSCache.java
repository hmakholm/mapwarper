package net.makholm.henning.mapwarper.gui.files;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.util.PokePublisher;

public class FSCache {

  public final PokePublisher modifiedFilesPokes =
      new PokePublisher("modifiedFiles");

  final Set<VectFile> modifiedFiles = new LinkedHashSet<>();

  public VectFile getFile(Path p) {
    synchronized(this) {
      return knownFiles.computeIfAbsent(p, p0 -> new VectFile(this,p0));
    }
  }

  public CachedDirectory getDirectory(Path p) {
    synchronized(this) {
      return knownDirs.computeIfAbsent(p,  CachedDirectory::new);
    }
  }

  public CachedDirectory refreshDirectory(Path p) {
    synchronized(this) {
      CachedDirectory result = new CachedDirectory(p);
      knownDirs.put(p, result);
      return result;
    }
  }

  public Set<VectFile> getModifiedFiles() {
    synchronized(modifiedFiles) {
      return new LinkedHashSet<>(modifiedFiles);
    }
  }

  public void cleanCache(Path focusDir) {
    synchronized(this) {
      knownDirs.clear();
      int beforeCount = knownFiles.size();
      for( var it = knownFiles.values().iterator(); it.hasNext(); ) {
        VectFile vf = it.next();
        if( !vf.okToForget() )
          continue;
        if( focusDir != null &&
            vf.path.startsWith(focusDir) &&
            focusDir.startsWith(vf.path.getParent()) )
          continue;
        it.remove();
      }
      int afterCount = knownFiles.size();
      if( beforeCount != afterCount )
        System.err.printf(Locale.ROOT,
            "Cleared %d of %d cached files\n",
            beforeCount-afterCount, beforeCount);
    }
  }

  public Map<Path, FileContent> revertContent(boolean alsoChanged) {
    Map<Path, FileContent> undoMap = new LinkedHashMap<>();
    synchronized(this) {
      for( var vf : knownFiles.values() )
        vf.forgetContent(alsoChanged ? undoMap : null);
    }
    return undoMap;
  }

  private final Map<Path, VectFile> knownFiles = new LinkedHashMap<>();

  private final Map<Path, CachedDirectory> knownDirs = new LinkedHashMap<>();

}
