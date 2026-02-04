package net.makholm.henning.mapwarper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.tiles.DiskCachedTileset;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.tiles.TryDownloadLater;
import net.makholm.henning.mapwarper.util.NiceError;

public class DownloadtileCommand extends Mapwarper.Command {

  DownloadtileCommand(Mapwarper common) {
    super(common);
  }

  @Override
  protected void run(Deque<String> words) {
    Tileset tileset0 = common.tilesWithDefault(null);
    if( tileset0 instanceof DiskCachedTileset tileset ) {
      var pos = Point.at(common.parsePoint(words));
      int zoom = common.wantedZoom.orElse(12);
      var tile = tileset.makeAddresser(zoom, pos).locate(pos);
      if( tile == 0 )
        throw NiceError.of("No tile at that position");

      Path outname = Paths.get("downloaded"+tileset.extension);
      System.err.println(tileset.tilename(tile)+" --> "+outname);
      try {
        tileset.produceTileInFile(tile, outname);
      } catch (IOException | TryDownloadLater e) {
        e.printStackTrace();
      }
    } else {
      throw NiceError.of("'%s' cannot produce disk files ...", tileset0.name);
    }
  }

}
