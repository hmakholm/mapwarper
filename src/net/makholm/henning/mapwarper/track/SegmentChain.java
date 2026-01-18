package net.makholm.henning.mapwarper.track;

import java.util.List;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Bezier;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.FrozenArray;
import net.makholm.henning.mapwarper.util.Lazy;
import net.makholm.henning.mapwarper.util.LongHashed;
import net.makholm.henning.mapwarper.util.SingleMemo;
import net.makholm.henning.mapwarper.util.XyTree;

public final class SegmentChain extends LongHashed {

  public final ChainClass chainClass;

  public int numNodes;
  public int numSegments;
  public FrozenArray<TrackNode> nodes;
  public FrozenArray<SegKind> kinds;

  public SegmentChain(List<TrackNode> nodes0,
      List<SegKind> kinds0, ChainClass chainClass) {
    this.chainClass = chainClass;
    this.nodes = FrozenArray.freeze(nodes0);
    this.kinds = FrozenArray.freeze(kinds0);
    this.numNodes = nodes.size();
    this.numSegments = kinds.size();
    if( numSegments != numNodes-1 )
      throw BadError.of("Mismatch: %d kinds for %d nodes", kinds.size(), numNodes);
    for( int i = 0; i<kinds.size(); i++ )
      if( kinds.get(i).chainClass() != chainClass ) {
        throw BadError.of("kind[%d] = %s in a chain that was supposed to be %s",
            i, kinds.get(i), chainClass);
      }
  }

  public SegmentChain(List<TrackNode> nodes0, List<SegKind> kinds0) {
    this(nodes0, kinds0, kinds0.get(0).chainClass());
  }

  public boolean isTrack() {
    return chainClass == ChainClass.TRACK;
  }

  public boolean isBound() {
    return chainClass == ChainClass.BOUND;
  }

  public SegmentChain subchain(int fromNode, int toNode) {
    return new SegmentChain(
        nodes.subList(fromNode, toNode+1),
        kinds.subList(fromNode, toNode),
        chainClass);
  }

  // -------------------------------------------------------------------------
  //  Hash and check the immutable parts,
  //  ignoring internally cached derivates

  @Override
  protected long longHashImpl() {
    long h = nodes.get(0).longHash();
    for( int i=0; i<numSegments; i++ ) {
      h = hashStep(h);
      h ^= nodes.get(i+1).longHash();
      h += (long)kinds.get(i).ordinal() << 28;
    }
    return h;
  }

  @Override
  public boolean equals(Object o) {
    if( o == this ) return true;
    if( o instanceof SegmentChain other ) {
      if( other.longHash() != longHash() ||
          other.nodes.size() != nodes.size() ||
          !other.nodes.get(0).equals(nodes.get(0)) )
        return false;
      for( int i=0; i<numSegments; i++ ) {
        if( other.kinds.get(i)!= kinds.get(i) ||
            !other.nodes.get(i+1).equals(nodes.get(i+1)) )
          return false;
      }
      return true;
    }
    return false;
  }

  // -------------------------------------------------------------------------

  public final Lazy<XyTree<ChainRef<TrackNode>>> nodeTree = Lazy.of(() -> {
    var joiner = XyTree.<ChainRef<TrackNode>>leftWinsJoin();
    var tree = joiner.empty();
    for( int i=0; i<numNodes; i++ ) {
      var node = nodes.get(i);
      var sourced = ChainRef.of(node, this, i);
      var singleton = XyTree.singleton(node, sourced);
      tree = joiner.union(tree, singleton);
    }
    return tree;
  });

  public final Lazy<XyTree<List<ChainRef<Bezier>>>> curveTree = Lazy.of(() -> {
    var joiner = XyTree.<ChainRef<Bezier>>concatJoin();
    var tree = joiner.empty();
    var curves = smoothed();
    for( int i=0; i<curves.size(); i++ ) {
      var curve = curves.get(i);
      var sourced = ChainRef.of(curve, SegmentChain.this, i);
      var singleton = XyTree.singleton(curve.bbox.get(), List.of(sourced));
      tree = joiner.union(tree, singleton);
    }
    return tree;
  });

  // -------------------------------------------------------------------------

  public static final class Smoothed extends FrozenArray<Bezier> {
    private final double[] nodeSlews;
    private final double[] segmentSlews;

    Smoothed(Bezier[] curves, double[] nodeSlews, double[] segmentSlews) {
      super(curves);
      this.nodeSlews = nodeSlews;
      this.segmentSlews = segmentSlews;
    }

    public double nodeSlew(int i) {
      if( i < 0 ) return 0;
      if( i >= nodeSlews.length ) return nodeSlews[nodeSlews.length-1];
      return nodeSlews[i];
    }

    public double segmentSlew(int i) {
      if( i < 0 ) return 0;
      if( i >= segmentSlews.length ) return nodeSlews[nodeSlews.length-1];
      return segmentSlews[i];
    }

    public UnitVector direction(int node) {
      if( node > 0 )
        return get(node-1).dir4();
      else if( size() > 0 )
        return get(node).dir1();
      else
        return Vector.of(1,0).normalize();
    }
  }

  public final Lazy<Smoothed> smoothed = Lazy.of(() -> Smoother.smoothen(this));

  public Smoothed smoothed() {
    return smoothed.get();
  }

  public final SingleMemo<ProjectionWorker, LocalSegmentChain> localize =
      SingleMemo.of(ProjectionWorker::projection,
          proj -> LocalSegmentChain.make(this, proj));

  public LocalSegmentChain localize(ProjectionWorker worker) {
    return localize.apply(worker);
  }

  /**
   * This is used for <em>displaying</em> non-current file tracks, in
   * particular replacing them with a stylized symbol if they would
   * otherwise show as very small.
   */
  private LocalSegmentChain makeShownAsChain(ProjectionWorker worker) {
    // To handle cases where the chain is an almost closed loop (such as
    // fully traced ring lines!) sample both ends _and_ a node in the middle.
    var p0 = worker.global2local(nodes.get(0));
    var p1 = worker.global2local(nodes.get(numNodes/2));
    var p2 = worker.global2local(nodes.last());
    var box = AxisRect.extend(new AxisRect(p0, p2), p1);
    if( box.height() >= 10 || box.width() >= 10 )
      return localize.apply(worker);
    else
      return LocalSegmentChain.diamond(this, box.center());
  }

  private final SingleMemo<ProjectionWorker, LocalSegmentChain> shownAs =
      SingleMemo.of(ProjectionWorker::projection, this::makeShownAsChain);

  public LocalSegmentChain localizePerhapsTiny(ProjectionWorker worker) {
    return shownAs.apply(worker);
  }

}
