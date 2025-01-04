package net.makholm.henning.mapwarper.gui.files;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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

  private final Map<Path, VectFile> knownFiles = new LinkedHashMap<>();

  private final Map<Path, CachedDirectory> knownDirs = new LinkedHashMap<>();

}
