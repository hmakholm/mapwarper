package net.makholm.henning.mapwarper.gui.files;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class CachedDirectory {

  public final Path path;

  public final Map<String, Path> vectFiles;

  public final Map<String, Path> subdirs;

  CachedDirectory(Path path) {
    this.path = path;
    this.vectFiles = new TreeMap<>();
    this.subdirs = new TreeMap<>();
    try( DirectoryStream<Path> dstream = Files.newDirectoryStream(path) ) {
      for( Path p : dstream ) {
        String name = p.getFileName().toString();
        if( Files.isDirectory(p) ) {
          subdirs.put(name, p);
        } else if( name.endsWith(".vect") ) {
          vectFiles.put(name, p);
        }
      }
    } catch( IOException e ) {
      // What can we do?
      System.err.println("Could not scan directory "+path+":");
      e.printStackTrace();
    }
  }

}
