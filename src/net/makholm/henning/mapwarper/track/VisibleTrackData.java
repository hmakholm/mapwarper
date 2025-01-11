package net.makholm.henning.mapwarper.track;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.LongHashed;
import net.makholm.henning.mapwarper.util.SingleMemo;
import net.makholm.henning.mapwarper.util.XyTree;

public final class VisibleTrackData extends LongHashed {

  private TrackHighlight highlight;

  private SegmentChain editingChain;
  private final Set<SegmentChain> currentFileChains = new LinkedHashSet<>();

  private final Set<FileContent> showBoundChainsIn = new LinkedHashSet<>();
  private final Set<FileContent> showTrackChainsIn = new LinkedHashSet<>();

  private int flags;

  private boolean frozen;

  public VisibleTrackData() {}

  @Override
  public VisibleTrackData clone() {
    return new VisibleTrackData(this);
  }

  public VisibleTrackData(VisibleTrackData orig) {
    highlight = orig.highlight;
    editingChain = orig.editingChain;
    currentFileChains.addAll(orig.currentFileChains);
    showBoundChainsIn.addAll(orig.showBoundChainsIn);
    showTrackChainsIn.addAll(orig.showTrackChainsIn);
    flags = orig.flags;
  }

  public TrackHighlight highlight() {
    return highlight;
  }

  public void setHighlight(TrackHighlight hl) {
    checkEditable();
    highlight = hl;
  }

  public SegmentChain editingChain() {
    return editingChain;
  }

  public void setEditingChain(SegmentChain ch) {
    checkEditable();
    editingChain = ch;
  }

  public Iterable<SegmentChain> currentFileChains() {
    return currentFileChains;
  }

  public void addCurrentChain(SegmentChain chain) {
    checkEditable();
    currentFileChains.add(chain);
  }

  public void removeCurrentChain(SegmentChain chain) {
    checkEditable();
    currentFileChains.remove(chain);
  }

  public void setCurrentChains(Collection<SegmentChain> chains) {
    currentFileChains.clear();
    currentFileChains.addAll(chains);
  }

  public void setCurrentChains(FileContent file) {
    checkEditable();
    currentFileChains.clear();
    file.chains().forEach(currentFileChains::add);
  }

  public Iterable<FileContent> showBoundChainsIn() {
    return showBoundChainsIn;
  }

  public Iterable<FileContent> showTrackChainsIn() {
    return showTrackChainsIn;
  }

  public void showBoundChainsIn(FileContent file) {
    checkEditable();
    if( file.numBoundChains != 0 )
      showBoundChainsIn.add(file);
  }

  public void showTrackChainsIn(FileContent file) {
    checkEditable();
    if( file.numTrackChains != 0 )
      showTrackChainsIn.add(file);
  }

  public void setFlags(int newFlags) {
    checkEditable();
    this.flags = newFlags & Toggles.VECTOR_MASK;
  }

  public boolean hasFlag(Toggles t) {
    return t.setIn(flags);
  }

  public VisibleTrackData freeze() {
    frozen = true;
    return this;
  }

  private void checkEditable() {
    if( frozen )
      throw BadError.of("Attempt to edit frozen VisibleTrackData");
  }

  @Override
  protected long longHashImpl() {
    if( !frozen )
      throw BadError.of("Non-frozen VisibleTrackData cannot be hashed");
    long hash = 202412 + (long)flags << 20;
    if( highlight != null )
      hash += highlight.longHash();
    if( editingChain != null )
      hash += editingChain.longHash();
    hash = hashStep(hash);
    for( var chain : currentFileChains )
      hash += chain.longHash();
    hash = hashStep(hash);
    for( var bounds : showBoundChainsIn )
      hash += bounds.longHash();
    hash = hashStep(hash);
    for( var other : showTrackChainsIn )
      hash += other.longHash();
    return hash;
  }

  @Override
  public boolean equals(Object o) {
    return o == this || (
        o instanceof VisibleTrackData other &&
        other.longHash() == longHash() &&
        other.flags == flags &&
        Objects.equals(other.highlight, highlight) &&
        Objects.equals(other.editingChain, editingChain) &&
        other.currentFileChains.equals(currentFileChains) &&
        other.showBoundChainsIn.equals(showBoundChainsIn)) &&
        other.showTrackChainsIn.equals(showTrackChainsIn);
  }

  public final SingleMemo<ProjectionWorker, XyTree<ChainRef<Point>>>
  otherBoundNodeTree = SingleMemo.of(ProjectionWorker::projection, proj -> {
    var joiner = XyTree.<ChainRef<Point>>leftWinsJoin();
    var tree = joiner.empty();
    for( var content : showBoundChainsIn ) {
      for( var chain : content.chains() ) {
        if( chain.isBound() )
          tree = joiner.union(tree, chain.localize(proj).nodeTree.get());
      }
    }
    return tree;
  });

}
