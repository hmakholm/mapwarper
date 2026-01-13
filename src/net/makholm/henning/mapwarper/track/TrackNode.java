package net.makholm.henning.mapwarper.track;

import java.io.PrintStream;
import java.util.Locale;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.UnitVector;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.util.Regexer;

public final class TrackNode extends Point {

  public final long pos;
  public final UnitVector direction;

  private final long dirhash;
  private final int size;

  public TrackNode(int x, int y) {
    this(x,y,0);
  }

  public TrackNode withDirection(Vector dir) {
    if( dir != null )
      return new TrackNode(Coords.x(pos), Coords.y(pos), dir.normalize());
    else if( direction == null )
      return this;
    else
      return new TrackNode(Coords.x(pos), Coords.y(pos));
  }

  public boolean locksDirection() {
    return direction != null;
  }

  private TrackNode(int x, int y, int size) {
    super(x,y);
    this.pos = Coords.wrap(x, y);
    this.direction = null;
    this.dirhash = 0;
    this.size = size;
  }

  private TrackNode(int x, int y, UnitVector direction) {
    super(x,y);
    this.pos = Coords.wrap(x, y);
    this.direction = direction;
    var tenths = (int)(Math.round(10*direction.bearing()));
    this.dirhash = 0x20260113201057L * (1+tenths);
    this.size = 0;
  }

  static TrackNode parse(Regexer re) {
    if( re.match("node ([0-9]+)::([0-9]+)(?: ([0-9]+))?") ) {
      // The 'size' parameter is not used for anything, but we preserve it
      // through edits if it's there, in order not to create any Git wobble.
      var size = re.group(3) == null ? 0 : re.igroup(3);
      return new TrackNode(re.igroup(1), re.igroup(2), size);
    } else if( re.match("node ([0-9]+)::([0-9]+) ([0-9]+\\.[0-9]+)") ) {
      var bearing = re.dgroup(3);
      return new TrackNode(re.igroup(1), re.igroup(2),
          UnitVector.withBearing(bearing));
    } else {
      return null;
    }
  }

  public void print(PrintStream ps) {
    if( direction != null )
      ps.println("node "+Coords.wprint(pos)+
          String.format(Locale.ROOT, " %.1f", direction.bearing()));
    else if( size != 0 )
      ps.println("node "+Coords.wprint(pos)+" "+size);
    else
      ps.println("node "+Coords.wprint(pos));
  }

  public long longHash() {
    return pos + dirhash;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TrackNode tn && equals(tn);
  }

  public boolean equals(TrackNode other) {
    return other.pos == pos && other.dirhash == dirhash;
  }

}
