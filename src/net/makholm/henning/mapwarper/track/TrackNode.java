package net.makholm.henning.mapwarper.track;

import java.io.PrintStream;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.georaster.Coords;

public final class TrackNode extends Point {

  public final long pos;
  public final int size;

  public TrackNode(int x, int y) {
    this(x,y,0);
  }

  TrackNode(int x, int y, int size) {
    super(x,y);
    this.pos = Coords.wrap(x, y);
    this.size = size;
  }

  public void print(PrintStream ps) {
    if( size != 0 )
      ps.println("node "+Coords.wprint(pos)+" "+size);
    else
      ps.println("node "+Coords.wprint(pos));
  }

}
