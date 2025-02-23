package net.makholm.henning.mapwarper.track;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.makholm.henning.mapwarper.georaster.WebMercator;
import net.makholm.henning.mapwarper.gui.files.VectFile;
import net.makholm.henning.mapwarper.util.NiceError;
import net.makholm.henning.mapwarper.util.Regexer;

public class VectParser {

  private final Path readingFromPath;
  private int lnum;

  public VectParser(Path path) {
    this.readingFromPath = path;
  }

  private String fileComment;
  private List<Path> usebounds = new ArrayList<>();
  private List<SegmentChain> chains = new ArrayList<>();

  private List<TrackNode> nodeCollector = new ArrayList<>();
  private List<SegKind> kindCollector = new ArrayList<>();

  ChainClass chainClass;
  SegKind currentKind;

  public FileContent readFromDisk() throws IOException {
    try( var br = new BufferedReader(new FileReader(readingFromPath.toFile())) ) {
      for( String line; (line = br.readLine()) != null; ) {
        giveLine(line);
      }
    }
    return result();
  }

  public void giveLine(String line) {
    lnum++ ;
    Regexer re = new Regexer(line.strip());
    if( re.match("node ([0-9]+)::([0-9]+)(?: ([0-9]+))?") ) {
      // The 'size' parameter is not used for anything, but we preserve it
      // through edits if it's there, in order not to create any Git wobble.
      var size = re.group(3) == null ? 0 : re.igroup(3);
      var node = new TrackNode(re.igroup(1), re.igroup(2), size);
      if( !nodeCollector.isEmpty() ) {
        if( currentKind == null )
          currentKind = SegKind.TRACK;
        kindCollector.add(currentKind);
        chainClass = currentKind.chainClass();
      }
      nodeCollector.add(node);
      currentKind = chainClass == null ? null : chainClass.defaultKind();

    } else if( re.is("break") ) {
      flushChain();

    } else if( kindsByKeyword.containsKey(re.full) ) {
      SegKind kind = kindsByKeyword.get(re.full);

      if( nodeCollector.isEmpty() ) {
        if( kind != kind.klass.defaultKind() ) {
          throw NiceError.of("%d: got '%s' when there's no open chain",
              lnum, kind);
        }
        chainClass = kind.chainClass();
      } else {
        if( chainClass != null && kind.chainClass() != chainClass ) {
          // Consecutive segments of different classes can happen in older
          // files (though it really shouldn't)
          TrackNode lastNode = flushChain();
          if( lastNode != null ) {
            nodeCollector.add(lastNode);
            currentKind = kind;
            chainClass = kind.chainClass();
          }
        } else {
          currentKind = kind;
        }
      }

    } else if( re.match("usebounds "+VectFile.cVectfile) ) {
      if( readingFromPath != null ) {
        Path path = readingFromPath.resolveSibling(re.group(1));
        usebounds.add(path);
      }

    } else if( re.match("|new|sealed|nocross|showtrack .*") ) {
      // These lines are ignored for historical reasons

    } else if( re.match("comment (.*)") ) {
      if( fileComment != null )
        throw NiceError.of("%d: double comment", lnum);
      fileComment = re.group(1);

    } else if( re.match("coords (.*)") ) {
      if( !re.group(1).equals(WebMercator.TAG) )
        throw NiceError.of("%d: unsupported coordinate system '%s'", lnum,
            re.group(1));

    } else {
      throw NiceError.of("%d: '%s' unrecognized", lnum, re.full);
    }
  }

  public FileContent result() {
    flushChain();
    var result = new FileContent(fileComment, chains, usebounds);
    if( !result.countsAsTrackFile() )
      result = result.withoutUsebounds();
    return result;
  }

  private TrackNode flushChain() {
    TrackNode result;
    switch( nodeCollector.size() ) {
    case 0:
      result = null;
      break;
    case 1:
      result = nodeCollector.get(0);
      break;
    default:
      result = nodeCollector.get(nodeCollector.size()-1);
      chains.add(new SegmentChain(nodeCollector, kindCollector));
    }
    nodeCollector.clear();
    kindCollector.clear();
    chainClass = null;
    currentKind = null;
    return result;
  }

  private static final Map<String, SegKind> kindsByKeyword;
  static {
    kindsByKeyword = new LinkedHashMap<>();
    for( var v : SegKind.values() ) {
      kindsByKeyword.put(v.keyword, v);
    }
  }

}
