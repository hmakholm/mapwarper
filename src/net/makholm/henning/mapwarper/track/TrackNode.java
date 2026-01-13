package net.makholm.henning.mapwarper.track;

import java.io.PrintStream;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.util.Regexer;

public final class TrackNode extends Point {

  public final long pos;
  private final int size;

  public TrackNode(int x, int y) {
    this(x,y,0);
  }

  private TrackNode(int x, int y, int size) {
    super(x,y);
    this.pos = Coords.wrap(x, y);
    this.size = size;
  }

  static TrackNode parse(Regexer re) {
    if( re.match("node ([0-9]+)::([0-9]+)(?: ([0-9]+))?") ) {
      // The 'size' parameter is not used for anything, but we preserve it
      // through edits if it's there, in order not to create any Git wobble.
      var size = re.group(3) == null ? 0 : re.igroup(3);
      return new TrackNode(re.igroup(1), re.igroup(2), size);
    } else {
      return null;
    }
  }

  public void print(PrintStream ps) {
    if( size != 0 )
      ps.println("node "+Coords.wprint(pos)+" "+size);
    else
      ps.println("node "+Coords.wprint(pos));
  }

  public long longHash() {
    return pos;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TrackNode tn && equals(tn);
  }

  public boolean equals(TrackNode other) {
    return other.pos == pos;
  }

}
