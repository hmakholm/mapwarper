package net.makholm.henning.mapwarper.gui.maprender;

import java.util.function.DoubleSupplier;

import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.util.LongHashed;

public final class FrozenLayerSpec extends LongHashed implements LayerSpec {

  public final Projection projection;
  @Override public Projection projection() { return projection; }

  public final int flags;
  @Override public int flags() { return flags; }

  public final Tileset mainTiles;
  @Override public Tileset mainTiles() { return mainTiles; }

  public final int targetZoom;
  @Override public int targetZoom() { return targetZoom; }

  public final Tileset fallbackTiles;
  @Override public Tileset fallbackTiles() { return fallbackTiles; }

  public final DoubleSupplier windowDiagonal;
  @Override public DoubleSupplier windowDiagonal() { return windowDiagonal; }

  public FrozenLayerSpec(LayerSpec orig) {
    projection = orig.projection();
    flags = orig.flags();
    mainTiles = orig.mainTiles();
    targetZoom = orig.targetZoom();
    fallbackTiles = orig.fallbackTiles();
    windowDiagonal = orig.windowDiagonal();
  }

  @Override
  protected long longHashImpl() {
    long hash = projection.longHash();
    hash ^= mainTiles.hashCode();
    hash ^= (long)fallbackTiles.hashCode() << 32;
    hash = hashStep(hash);
    hash ^= flags;
    hash ^= targetZoom << 25;
    hash ^= (long)windowDiagonal.hashCode() << 32;
    return hash;
  }

  @Override
  public boolean equals(Object o) {
    if( o == this ) return true;
    LayerSpec ls;
    if( o instanceof FrozenLayerSpec fls ) {
      if( longHash() == fls.longHash() )
        ls = fls;
      else
        return false;
    } else if( o instanceof LayerSpec ls0 ) {
      ls = ls0;
    } else {
      return false;
    }

    return targetZoom == ls.targetZoom() &&
        flags == ls.flags() &&
        mainTiles.equals(ls.mainTiles()) &&
        fallbackTiles.equals(ls.fallbackTiles()) &&
        projection.equals(ls.projection()) &&
        windowDiagonal.equals(ls.windowDiagonal());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(mainTiles.name);
    sb.append("@z").append(targetZoom);
    if( fallbackTiles != mainTiles )
      sb.append('/').append(fallbackTiles.name);
    for( Toggles t : Toggles.values() )
      if( t.setIn(flags) )
        sb.append('/').append(t);
    return sb.toString();
  }

}
