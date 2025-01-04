package net.makholm.henning.mapwarper.track;

import java.util.AbstractList;
import java.util.List;

import net.makholm.henning.mapwarper.util.BadError;
import net.makholm.henning.mapwarper.util.FrozenArray;

public final class TrackPath {

  public final FrozenArray<TrackSegment> segs;
  public final int size;

  public TrackSegment seg(int index) {
    return segs.get(index);
  }

  public SegKind kind(int index) {
    if( index < 0 || index >= size )
      return null;
    else
      return segs.get(index).kind;
  }

  public TrackPath(List<TrackSegment> seglist) {
    if( seglist.isEmpty() )
      throw BadError.of("Cannot create an empty TrackPath");
    segs = FrozenArray.freeze(seglist);
    size = segs.size();
    for( int i=1; i<size; i++ ) {
      if( segs.get(i-1).b != segs.get(i).a )
        throw BadError.of("Match-up error for segments %d..%d", i-1, i);
    }
  }

  public final List<TrackNode> nodes = new AbstractList<>() {
    public int size() {
      return size+1;
    }

    public TrackNode get(int i) {
      if( i == 0 )
        return segs.get(0).a;
      else
        return segs.get(i-1).b;
    }
  };

}
