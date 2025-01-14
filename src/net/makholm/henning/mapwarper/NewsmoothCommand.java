package net.makholm.henning.mapwarper;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Deque;

import net.makholm.henning.mapwarper.track.ChainClass;
import net.makholm.henning.mapwarper.track.FileContent;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.track.VectParser;

public class NewsmoothCommand extends Mapwarper.Command {

  NewsmoothCommand(Mapwarper common) {
    super(common);
  }

  double worstYet = 0;
  TrackNode worstWhere = null;

  @Override
  protected void run(Deque<String> words) {
    try {
      Files.walkFileTree(Path.of("."), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
            throws IOException {
          if( file.getFileName().toString().endsWith(".vect") ) {
            FileContent fc = new VectParser(file).readFromDisk();
            SegmentChain chain = fc.uniqueChain(ChainClass.TRACK);
            if( chain != null ) {
              var smoothed = chain.smoothed();
              if( smoothed.worstDiff > worstYet ) {
                System.out.println(smoothed.worstDiff+" for "+file+" at "+smoothed.worstWhere);
                worstYet = smoothed.worstDiff;
              }
            }
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
