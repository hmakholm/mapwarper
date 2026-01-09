package net.makholm.henning.mapwarper.gui.swing;

import java.util.Locale;
import java.util.Objects;

import javax.swing.JFrame;

import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.gui.MapView;
import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.files.VectFile;
import net.makholm.henning.mapwarper.gui.maprender.FallbackChain;
import net.makholm.henning.mapwarper.gui.projection.BaseProjection;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.gui.projection.WarpedProjection;
import net.makholm.henning.mapwarper.tiles.Tileset;

public class WindowTitle {

  private final JFrame frame;
  private final MapView logic;

  WindowTitle(JFrame frame, MapView logic) {
    this.frame = frame;
    this.logic = logic;

    frame.setTitle("Mapwarper v3");
  }

  private static final int RELEVANT_FLAGS = Toggles.DOWNLOAD.bit();

  private Tool tool;
  private VectFile file;
  private Projection projection;
  private Tileset tiles;
  private int flagBits;
  private Tileset lensTiles;
  private int lensZoom;

  void refresh() {
    int flags0 = logic.toggleState & RELEVANT_FLAGS;
    Tileset lensTiles0 = null;
    int lensZoom0 = 0;
    if( logic.lensRect != null ) {
      lensTiles0 = logic.lensTiles;
      lensZoom0 = logic.lensZoom;
    }
    if( tool != logic.currentTool ||
        file != logic.files.activeFile() ||
        projection != logic.projection ||
        tiles != logic.mainTiles ||
        flagBits != flags0 ||
        lensTiles != lensTiles0 ||
        lensZoom != lensZoom0 ) {

      tool = logic.currentTool;
      file = logic.files.activeFile();
      projection = logic.projection;
      tiles = logic.mainTiles;
      flagBits = flags0;
      lensTiles = lensTiles0;
      lensZoom = lensZoom0;

      StringBuffer sb = new StringBuffer();
      sb.append(tool.codename);
      sb.append(" \u2013 ");
      sb.append(file);
      sb.append(" \u2013 ");

      BaseProjection baseproj = projection.base();
      if( baseproj.isOrtho() ) {
        // This is the default, nothing to show
      } else if( baseproj.isQuickwarp() ) {
        sb.append("quickwarp ");
      } else if( baseproj instanceof WarpedProjection wp ) {
        sb.append("warped");
        if( !Objects.equals(wp.sourcename0, file.path) ) {
          sb.append('(');
          sb.append(wp.sourcename0 == null ? "anonymous" :
            wp.sourcename0.getFileName().toString());
          sb.append(')');
        }
        sb.append(' ');
      } else {
        sb.append(baseproj.getClass().getSimpleName()).append(' ');
      }

      double squeeze = projection.getSqueeze();
      if( squeeze != 1 || !projection.base().isOrtho() ) {
        appendNumber(sb, "\u00d7", squeeze, " ");
      }

      double pixsize = projection.scaleAcross();
      int zoom = tiles.guiTargetZoom();
      if( baseproj.isOrtho() ) {
        int natzoom = FallbackChain.naturalZoom(pixsize, tiles);
        int natlogsize = Coords.BITS - natzoom - tiles.logTilesize();
        zoom = Math.min(zoom, Coords.logPixsize2zoom(natlogsize));
      }
      sb.append(tiles.name);
      if( baseproj.usesDownloadFlag() && !Toggles.DOWNLOAD.setIn(flagBits) )
        sb.append('?');
      sb.append(zoom);
      double factor = pixsize / Coords.zoom2pixsize(zoom);
      if( factor == 1 ) {
        // nothing
      } else if( factor > 1 )
        appendNumber(sb, "/", factor, "");
      else
        appendNumber(sb, "\u00D7", 1/factor, "");

      if( lensTiles != null ) {
        sb.append(" [");
        sb.append(lensTiles.name);
        sb.append(lensZoom);
        sb.append("]");
      }

      frame.setTitle(sb.toString());
    }

  }

  public void appendNumber(StringBuffer sb, String pre, double v, String post) {
    sb.append(pre);
    if( v == (int)v )
      sb.append((int)v);
    else
      sb.append(String.format(Locale.ROOT, "%.1f", v));
    sb.append(post);
  }

}
