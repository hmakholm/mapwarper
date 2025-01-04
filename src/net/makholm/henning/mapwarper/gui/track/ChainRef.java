package net.makholm.henning.mapwarper.gui.track;

public record ChainRef<T>(T data, SegmentChain chain, int index) {

  public static <T> ChainRef<T> of(T data, SegmentChain chain, int index) {
    return new ChainRef<>(data, chain, index);
  }

}
