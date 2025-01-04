package net.makholm.henning.mapwarper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;

import net.makholm.henning.mapwarper.georaster.Tile;
import net.makholm.henning.mapwarper.tiles.DiskCachedTileset;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.util.NiceError;

public class DownloadtileCommand extends Mapwarper.Command {

  DownloadtileCommand(Mapwarper common) {
    super(common);
  }

  @Override
  protected void run(Deque<String> words) {
    Tileset tileset0 = common.tilesWithDefault(null);
    if( tileset0 instanceof DiskCachedTileset tileset ) {
      long pos = common.parsePoint(words);
      int zoom = common.wantedZoom.orElse(12);
      Tile tile = Tile.containing(pos, zoom);

      Path outname = Paths.get("downloaded"+tileset.extension);
      System.err.println(tileset.tilename(tile)+" --> "+outname);
      try {
        tileset.produceTileInFile(tile, outname);
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      throw NiceError.of("'%s' cannot produce disk files ...", tileset0.name);
    }
  }

}
