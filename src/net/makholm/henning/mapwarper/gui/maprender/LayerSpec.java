package net.makholm.henning.mapwarper.gui.maprender;

import java.util.function.DoubleSupplier;

import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.tiles.Tileset;

public interface LayerSpec {

  public Projection projection();

  public int flags();

  public Tileset mainTiles();
  public int targetZoom();

  public Tileset fallbackTiles();

  public DoubleSupplier windowDiagonal();

}
