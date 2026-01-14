package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.overlays.BoxOverlay;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.swing.SwingUtils;
import net.makholm.henning.mapwarper.gui.swing.Tool;
import net.makholm.henning.mapwarper.track.SegmentChain;

class DragSubchainSelector implements MouseAction {

  interface Callback {
    ProposedAction draggedSubchain(SegmentChain chain, int a, int b);
  }

  final MapView mapView;
  final Point p1;
  final int rgb;
  final Callback callback;

  DragSubchainSelector(MapView mapView, Point p1, int rgb, Callback callback) {
    this.mapView = mapView;
    this.p1 = p1;
    this.rgb = rgb;
    this.callback = callback;
  }

  @Override
  public ToolResponse mouseResponse(Point p2, int mod2) {
    var rect = new AxisRect(p1, p2);
    var chain = mapView.editingChain;
    int a=0, b;
    if( chain == null ) {
      b = -1;
    } else {
      var trans = mapView.translator();
      b = chain.numSegments;
      while( b >= 0 && !rect.contains(trans.global2local(chain.nodes.get(b))) )
        b--;
      while( a < b && !rect.contains(trans.global2local(chain.nodes.get(a))) )
        a++;
    }

    var box = new BoxOverlay(rect, rgb);
    ToolResponse toWrap = null;
    if( a <= b ) {
      var action = callback.draggedSubchain(chain, a, b);
      if( action != null )
        toWrap = action.withPreview().freeze();
    }
    if( toWrap == null ) toWrap = Tool.NO_RESPONSE;
    return new WrappedToolResponse(toWrap) {
      @Override
      public VectorOverlay previewOverlay() {
        return box;
      }
      @Override
      public void execute(ExecuteWhy why) {
        if( !new AxisRect(mapView.visibleArea).contains(p2) )
          SwingUtils.beep();
        else
          super.execute(why);
      }
    };
  }

}
