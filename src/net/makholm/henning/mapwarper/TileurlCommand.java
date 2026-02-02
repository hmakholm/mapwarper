package net.makholm.henning.mapwarper;

import java.util.Deque;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.tiles.HttpTileset;
import net.makholm.henning.mapwarper.tiles.Tileset;

final class TileurlCommand extends Mapwarper.Command {

  TileurlCommand(Mapwarper common) {
    super(common);
  }

  @Override
  protected void run(Deque<String> words) {
    var pos = Point.at(common.parsePoint(words));
    int zoom = common.wantedZoom.orElse(12);
    long tile = common.wantedTiles.makeAddresser(zoom, pos).locate(pos);

    if( common.wantedTiles instanceof HttpTileset hts ) {
      System.out.println(hts.tileUrl(tile));
    } else {
      for( Tileset ts : common.tileContext.tilesets.values() ) {
        if( ts instanceof HttpTileset hts ) {
          String url = hts.tileUrl(tile);
          System.out.println(ts.name+": "+url);
        }
      }
    }
  }

}
