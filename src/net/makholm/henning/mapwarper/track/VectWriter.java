package net.makholm.henning.mapwarper.track;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.makholm.henning.mapwarper.georaster.WebMercator;

public class VectWriter {

  private final Path directory;
  private final List<Path> oldUsebounds = new ArrayList<>();
  private final FileContent newContent;

  record Segpoints(long a, long b) {}

  private final Map<Segpoints, Integer> whichChain = new LinkedHashMap<>();

  public VectWriter(Path directory,
      FileContent oldContent, FileContent newContent) {
    this.directory = directory;
    if( oldContent != null ) {
      Integer segcounter = 0;
      for( var chain : oldContent.chains() ) {
        for( int i = 0; i < chain.numSegments; i++ )
          whichChain.put(segpoints(chain, i), segcounter++);
      }
      for( var usebound : oldContent.usebounds() ) oldUsebounds.add(usebound);
    }
    this.newContent = newContent;
  }

  public void write(PrintStream ps) {
    if( newContent.fileComment != null )
      ps.println("comment " + newContent.fileComment);
    ps.println("coords " + WebMercator.TAG);

    Set<Path> usebounds = new LinkedHashSet<Path>();
    for( var usebound : newContent.usebounds() ) usebounds.add(usebound);
    for( var path : oldUsebounds ) {
      if( usebounds.remove(path) )
        ps.println("usebounds "+directory.relativize(path));
    }
    for( var path : usebounds ) {
      ps.println("usebounds "+directory.relativize(path));
    }

    TreeMap<Long, SegmentChain> chains = new TreeMap<>();
    int chaincounter = 0;
    for( var chain : newContent.chains() ) {
      int orderBy = orderingForChain(chain);
      chains.put(((long)orderBy << 32) + chaincounter, chain);
      chaincounter++;
    }
    boolean needsBreak = false;
    for( var chain : chains.values() ) {
      SegKind defaultKind = chain.chainClass.defaultKind();
      for( int i=0; i<chain.numNodes; i++ ) {
        if( i > 0 ) {
          SegKind kind = chain.kinds.get(i-1);
          if( kind != defaultKind )
            ps.println("  "+kind.keyword);
        } else {
          if( needsBreak )
            ps.println("  break");
          if( defaultKind != SegKind.TRACK )
            ps.println(defaultKind.keyword);
        }
        chain.nodes.get(i).print(ps);
      }
      needsBreak = true;
    }
  }

  private int orderingForChain(SegmentChain chain) {
    int usePosition = Integer.MAX_VALUE;
    if( chain.isTrack() )
      usePosition -= 1000;
    usePosition -= chain.numSegments;

    int longestRun = 0;
    int currentRun = 0;
    int prev = -1;
    for( int i=0; i<chain.numSegments; i++ ) {
      Integer gotpos = whichChain.get(segpoints(chain, i));
      if( gotpos == null ) {
        currentRun = 0;
        prev = -1;
      } else {
        if( gotpos == prev+1 ) {
          currentRun++;
        } else {
          currentRun = 1;
        }
        if( currentRun > longestRun ) {
          longestRun = currentRun;
          usePosition = gotpos;
        }
        prev = gotpos;
      }
    }
    return usePosition;
  }

  private static Segpoints segpoints(SegmentChain chain, int i) {
    return new Segpoints(chain.nodes.get(i).pos, chain.nodes.get(i+1).pos);
  }

}
