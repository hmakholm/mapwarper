package net.makholm.henning.mapwarper.track;

import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.LongHashed;

public final class TrackHighlight extends LongHashed {

  public final SegmentChain chain;
  public final int fromNode, toNode;
  public final int rgb;

  public TrackHighlight(SegmentChain chain, int rgb) {
    this(chain, 0, chain.numNodes-1, rgb);
  }

  public TrackHighlight(SegmentChain chain, int node, int rgb) {
    this(chain, node, node, rgb);
  }

  public TrackHighlight(SegmentChain chain, int fromNode, int toNode,
      int rgb) {
    if( 0 > fromNode || fromNode > toNode || toNode > chain.numNodes-1 )
      throw BadError.of("Cannot highlight node %d..%d of %d-node chain",
          fromNode, toNode, chain.numNodes);
    this.chain = chain;
    this.fromNode = fromNode;
    this.toNode = toNode;
    if( RGB.isOpaque(rgb) || RGB.isTransparent(rgb) )
      rgb = (rgb & 0xFFFFFF) | (0x20 << 24);
    this.rgb = rgb;
  }

  public static TrackHighlight segment(ChainRef<?> ref, int rgb) {
    return new TrackHighlight(ref.chain(), ref.index(), ref.index()+1, rgb);
  }

  @Override
  protected long longHashImpl() {
    return chain.longHash() +
        fromNode +
        toNode<<16 +
        (long)rgb << 32;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this ||
        obj instanceof TrackHighlight other &&
        other.fromNode == fromNode &&
        other.toNode == toNode &&
        other.rgb == rgb &&
        other.chain.equals(chain);
  }

}
