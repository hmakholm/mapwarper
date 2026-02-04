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
    if( common.wantedTiles instanceof HttpTileset hts ) {
      attempt(hts, pos);
    } else {
      for( Tileset ts : common.tileContext.tilesets.values() ) {
        if( ts instanceof HttpTileset hts ) {
          attempt(hts, pos);
        }
      }
    }
  }

  private void attempt(HttpTileset tiles, Point pos) {
    int zoom = common.wantedZoom.orElse(12);
    long tile = tiles.makeAddresser(zoom, pos).locate(pos);
    if( tile == 0 ) {
      System.out.println("No "+tiles.name+" tile at "+pos);
    } else {
      String url = tiles.tileUrl(tile);
      System.out.println(tiles.name+": "+url);
    }
  }

}
