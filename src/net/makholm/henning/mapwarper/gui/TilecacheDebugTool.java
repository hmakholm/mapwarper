package net.makholm.henning.mapwarper.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.georaster.Coords;
import net.makholm.henning.mapwarper.georaster.Tile;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.projection.ProjectionWorker;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.tiles.TileCache;
import net.makholm.henning.mapwarper.tiles.TileSpec;
import net.makholm.henning.mapwarper.tiles.Tileset;
import net.makholm.henning.mapwarper.util.SingleMemo;

public class TilecacheDebugTool extends Tool {

  protected TilecacheDebugTool(Commands owner) {
    super(owner, "tilecacheDebug", "Tilecache debugger");
  }

  @Override
  public ToolResponse mouseResponse(Point pos, int modifiers) {
    if( mapView().projection.createAffine() == null ||
        mapView().lensZoom < 7 ) return NO_RESPONSE;
    var pointTo = Tile.containing(
        Coords.point2pixcoord(mapView().mouseGlobal),
        mapView().lensZoom);
    var tilex00 = pointTo.tilex - pointTo.tilex%100;
    var tiley00 = pointTo.tiley - pointTo.tiley%100;
    var overlay = new TilecacheOverlay(new TileSpec(mapView().lensTiles,
        Tile.at(pointTo.zoom, tilex00, tiley00).shortcode),
        translator());
    return new ToolResponse() {
      @Override public VectorOverlay previewOverlay() { return overlay; }
      @Override public void execute(ExecuteWhy why) { }
    };
  }

  private class TilecacheOverlay implements VectorOverlay {
    final TileSpec tileSpec00;
    final ProjectionWorker translator;
    final AxisRect bbox;

    TilecacheOverlay(TileSpec tileSpec00, ProjectionWorker translator) {
      this.tileSpec00 = tileSpec00;
      this.translator = translator;
      var tile00 = tileSpec00.tile();
      Point nwcorner = tile00.nwcorner();
      Vector diag = Vector.of(tile00.width(), tile00.height());
      AxisRect globalBox = new AxisRect(nwcorner, nwcorner.plus(100, diag));
      bbox = globalBox.transform(translator::global2local).grow(3);
    }

    @Override
    public AxisRect boundingBox() {
      return bbox;
    }

    @Override
    public void paint(Graphics2D g) {
      g.setFont(SwingUtils.getANiceDefaultFont().deriveFont(
          Font.BOLD));
      knownTiles.apply(tileSpec00).forEach((tile, color) -> {
        Graphics2D gg = (Graphics2D)g.create();
        gg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
            RenderingHints.VALUE_STROKE_PURE);
        gg.setColor(color);
        Point[] ps = localCorners(tile, 0);
        var box = rectpath(ps);
        gg.setClip(box);
        gg.setStroke(new BasicStroke(2.0f));
        gg.draw(box);
        if( Math.abs(ps[0].x-ps[2].x) > 10 ) {
          Path2D.Double cross = new Path2D.Double();
          cross.moveTo(ps[0].x, ps[0].y); cross.lineTo(ps[2].x, ps[2].y);
          cross.moveTo(ps[1].x, ps[1].y); cross.lineTo(ps[3].x, ps[3].y);
          gg.setStroke(new BasicStroke(1.0f));
          gg.draw(cross);
          if( Math.abs(ps[0].x-ps[2].x) > 100 ) {
            Point mid = translator.global2local(Point.at(tile.shortcode));
            gg.drawString(tile.tilex+"/"+tile.tiley,
                (float)mid.x+10, (float)mid.y+4);
          }
        }
      });
      g.setColor(new Color(0x00FF55));
      g.setStroke(new BasicStroke(3.0f));
      Tile tile00 = tileSpec00.tile();
      Point[] outer = localCorners(tile00, 99);
      g.draw(rectpath(outer));
      g.drawString(String.format(Locale.ROOT, "%d/%dxx/%dxx",
          tile00.zoom, tile00.tilex/100, tile00.tiley/100),
          (float)outer[3].x + 5, (float)outer[3].y - 5);
    }

    Point[] localCorners(Tile t, int extra) {
      int west = t.west, east = t.east + extra*t.width();
      int north = t.north, south = t.south + extra*t.height();
      Point[] result = new Point[4];
      result[0] = translator.global2local(Point.at(west, north));
      result[1] = translator.global2local(Point.at(east, north));
      result[2] = translator.global2local(Point.at(east, south));
      result[3] = translator.global2local(Point.at(west, south));
      return result;
    }

    static Path2D.Double rectpath(Point[] ps) {
      Path2D.Double box = new Path2D.Double();
      box.moveTo(ps[0].x, ps[0].y);
      for( int i=1; i<4; i++ ) box.lineTo(ps[i].x, ps[i].y);
      box.closePath();
      return box;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof TilecacheOverlay o &&
          o.tileSpec00.equals(tileSpec00) &&
          o.translator.projection().equals(translator.projection());
    }

  }

  private SingleMemo<TileSpec, Map<Tile, Color>> knownTiles =
      SingleMemo.of(ts -> ts, (TileSpec ts) -> {
        TileCache cache = mapView().tiles.ramCache;
        Tileset tiles = ts.tileset;
        var tile00 = ts.tile();
        int x00 = tile00.tilex;
        int y00 = tile00.tiley;
        int zoom = tile00.zoom;
        Color ramCached = new Color(0x00FFFF);
        Color diskCached = new Color(0xFFFF00);
        Map<Tile, Color> result = new LinkedHashMap<>();
        for( int x = x00; x<x00+100; x++ )
          for( int y = y00; y<y00+100; y++ ) {
            Tile t = Tile.at(zoom, x, y);
            if( cache.getTile(new TileSpec(tiles, t.shortcode), TileCache.RAM)
                != null ) {
              result.put(t, ramCached);
            } else if( tiles.isDiskCached(t) ) {
              result.put(t, diskCached);
            }
          }
        return result;
      });

}
