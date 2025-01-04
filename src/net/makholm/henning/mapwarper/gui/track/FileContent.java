package net.makholm.henning.mapwarper.gui.track;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.track.SegKind;
import net.makholm.henning.mapwarper.track.TrackNode;
import net.makholm.henning.mapwarper.util.Lazy;
import net.makholm.henning.mapwarper.util.LongHashed;
import net.makholm.henning.mapwarper.util.SetFreezer;
import net.makholm.henning.mapwarper.util.SingleMemo;
import net.makholm.henning.mapwarper.util.XyTree;

public final class FileContent extends LongHashed {

  public static final FileContent EMPTY =
      new FileContent(Collections.emptySet(), Collections.emptySet(), null);

  public final String fileComment;

  public final int numTrackChains;
  public final int numBoundChains;

  private final Set<Path> usebounds;
  private final Set<SegmentChain> chains;

  public FileContent(String comment,
      Collection<SegmentChain> chains,
      Collection<Path> usebounds) {
    this(SetFreezer.freeze(chains), SetFreezer.freeze(usebounds), comment);
  }

  /**
   * The private constructor has the comment last, such that it
   * won't conflict with the public constructor that takes entire
   * collections.
   *
   * The caller of this certifies that the sets are known to nobody
   * who might ever mistakenly modify them.
   */
  private FileContent(Set<SegmentChain> chains,
      Set<Path> usebounds,
      String comment) {
    this.fileComment = comment;
    this.usebounds = usebounds;
    this.chains = chains;

    int numTrackChains = 0;
    int numBoundChains = 0;
    for( var chain : chains ) {
      switch( chain.chainClass ) {
      case TRACK:
        numTrackChains++;
        break;
      case BOUND:
        numBoundChains++;
        break;
      default:
        break;
      }
    }
    this.numTrackChains = numTrackChains;
    this.numBoundChains = numBoundChains;
  }

  public FileContent withComment(String newComment) {
    return new FileContent(newComment, chains, usebounds);
  }

  public Iterable<SegmentChain> chains() {
    return chains;
  }

  public Set<SegmentChain> chainsCopy() {
    return new LinkedHashSet<>(chains);
  }

  public boolean contains(SegmentChain chain) {
    return chains.contains(chain);
  }

  public FileContent withChains(Collection<SegmentChain> newChains) {
    return new FileContent(fileComment,
        new LinkedHashSet<>(newChains), usebounds);
  }

  public Iterable<Path> usebounds() {
    return usebounds;
  }

  public boolean usesBounds(Path p) {
    return usebounds.contains(p);
  }

  private FileContent withUsebounds(Set<Path> newUsebounds) {
    return new FileContent(fileComment, chains, newUsebounds);
  }

  public FileContent withoutUsebounds() {
    return withUsebounds(Set.of());
  }

  public FileContent addUsebounds(Path p) {
    if( usebounds.contains(p) )
      return this;
    else if( usebounds.isEmpty() )
      return withUsebounds(Collections.singleton(p));
    else {
      var newbounds = new LinkedHashSet<Path>(usebounds);
      newbounds.add(p);
      return withUsebounds(newbounds);
    }
  }

  public FileContent removeUsebounds(Path p) {
    if( !usebounds.contains(p) ) {
      return this;
    } else if( usebounds.size() == 1 ) {
      return withUsebounds(Collections.emptySet());
    } else {
      var newbounds = new LinkedHashSet<Path>(usebounds);
      newbounds.remove(p);
      return withUsebounds(newbounds);
    }
  }

  public boolean countsAsTrackFile() {
    return numTrackChains > 0 || numBoundChains == 0;
  }

  public SegmentChain uniqueChain(SegKind chainClass) {
    SegmentChain found = null;
    for( var chain : chains )
      if( chain.chainClass == chainClass ) {
        if( found != null )
          return null;
        found = chain;
      }
    return found;
  }

  // -------------------------------------------------------------------------
  //  Hash and check the immutable parts,
  //  ignoring internally cached derivates

  @Override
  protected long longHashImpl() {
    long h = 0;
    if( fileComment != null )
      h += fileComment.hashCode();
    h = hashStep(h);
    for( var bound : usebounds ) {
      h += bound.hashCode();
    }
    h = hashStep(h);
    for( var chain : chains )
      h += chain.longHash();
    return h;
  }

  @Override
  public boolean equals(Object o) {
    return o == this ||
        (o instanceof FileContent other &&
            other.longHash() == longHash() &&
            other.usebounds.equals(usebounds) &&
            other.chains.equals(chains) &&
            Objects.equals(other.fileComment, fileComment));
  }

  // -------------------------------------------------------------------------

  public final Lazy<XyTree<ChainRef<TrackNode>>> nodeTree = Lazy.of(() -> {
    var joiner = XyTree.<ChainRef<TrackNode>>leftWinsJoin();
    var tree = joiner.empty();
    for( var chain : chains() ) {
      tree = joiner.union(tree, chain.nodeTree.get());
    }
    return tree;
  });

  public final SingleMemo<ProjectionWorker, XyTree<List<ChainRef<Bezier>>>>
  segmentTree = SingleMemo.of(ProjectionWorker::projection, proj -> {
    var joiner = XyTree.<ChainRef<Bezier>>concatJoin();
    var tree = joiner.empty();
    for( var chain : chains() ) {
      tree = joiner.union(tree, chain.localize(proj).segmentTree.get());
    }
    return tree;
  });

  public final SingleMemo<ProjectionWorker, XyTree<ChainRef<Point>>>
  localNodeTree = SingleMemo.of(ProjectionWorker::projection, proj -> {
    var joiner = XyTree.<ChainRef<Point>>leftWinsJoin();
    var tree = joiner.empty();
    for( var chain : chains() ) {
      tree = joiner.union(tree, chain.localize(proj).nodeTree.get());
    }
    return tree;
  });

  public XyTree<ChainRef<Point>> nodeTree(ProjectionWorker worker) {
    return localNodeTree.apply(worker);
  }

}
