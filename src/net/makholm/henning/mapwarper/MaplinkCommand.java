package net.makholm.henning.mapwarper;

import java.util.Deque;

import net.makholm.henning.mapwarper.tiles.Tileset;

final class MaplinkCommand extends Mapwarper.Command {

  MaplinkCommand(Mapwarper common) {
    super(common);
  }

  @Override
  protected void run(Deque<String> words) {
    long pos = common.parsePoint(words);
    int zoom = common.wantedZoom.orElse(12);

    if( common.wantedTiles != null ) {
      System.out.println(common.wantedTiles.webUrlFor(zoom, pos));
    } else {
      for( Tileset ts : common.tileContext.tilesets.values() ) {
        String url = ts.webUrlFor(zoom, pos);
        if( url != null )
          System.out.println(ts.name+": "+url);
      }
    }
  }

}
