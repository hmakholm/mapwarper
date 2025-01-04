package net.makholm.henning.mapwarper.georaster;

import java.util.Locale;

import net.makholm.henning.mapwarper.util.BadError;

public class GeoRect {

  public final int north, south, east, west;

  public GeoRect(int north, int south, int east, int west) {
    if( north >= south || west >= east ) {
      north = south;
      east = west;
    }
    this.north = north;
    this.south = south;
    this.east = east;
    this.west = west;
  }

  public int width() {
    return east-west;
  }

  public int height() {
    return south-north;
  }

  public long midpoint() {
    return Coords.wrap((east+west)/2, (north+south)/2);
  }

  @Override
  public int hashCode() {
    throw BadError.of("GeoRect itself should not be used as a hash key");
  }

  @Override
  public String toString() {
    return String.format(Locale.ROOT, "[%d,%d)x[%d,%d)",
        west, east, north, south);
  }

}
