package net.makholm.henning.mapwarper.gui;

import java.awt.geom.NoninvertibleTransformException;
import java.util.ArrayList;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.TransformHelper;
import net.makholm.henning.mapwarper.gui.overlays.ArrowOverlay;
import net.makholm.henning.mapwarper.track.ChainClass;
import net.makholm.henning.mapwarper.track.ChainRef;
import net.makholm.henning.mapwarper.track.SegmentChain;
import net.makholm.henning.mapwarper.track.TrackHighlight;
import net.makholm.henning.mapwarper.track.TrackNode;

final class LockingTool extends GenericEditTool {

  static final int RGB = 0xAA00FF;

  LockingTool(Commands owner) {
    super(owner, "lock", "Lock node headings");
  }

  @Override
  public ToolResponse mouseResponse(Point local, int modifiers) {
    var found = pickNodeInActive(local);
    var action = lockOrUnlock(found);
    if( action == null )
      return NO_RESPONSE;
    if( shiftHeld(modifiers) || mouseHeld(modifiers) )
      action = action.withPreview();
    return action.freeze();
  }

  ProposedAction lockOrUnlock(ChainRef<Point> found) {
    if( found == null )
      return null;
    var chain = found.chain();
    if( chain.chainClass != ChainClass.TRACK )
      return null;
    int i = found.index();
    var oldNode = chain.nodes.get(i);
    TrackNode newNode;
    String undoDesc;
    if( oldNode.locksDirection() ) {
      newNode = oldNode.withDirection(null);
      undoDesc = "Unlock node heading";
    } else {
      newNode = oldNode.withDirection(chain.smoothed().direction(i));
      undoDesc = "Lock node heading";
    }
    var newNodes = new ArrayList<>(chain.nodes);
    newNodes.set(i, newNode);
    var newChain = new SegmentChain(newNodes, chain.kinds, ChainClass.TRACK);
    return rewriteTo(undoDesc, newChain)
        .with(new TrackHighlight(chain, i, RGB));
  }

  @Override
  public MouseAction drag(Point p1, int mod1) {
    var found = pickNodeInActive(p1);
    var clickAction = lockOrUnlock(found);
    if( clickAction == null ) {
      return NO_RESPONSE.constantDrag();
    }
    var clickResponse = clickAction.withPreview().freeze();

    var chain = found.chain();
    var i = found.index();
    var p0 = translator().global2local(chain.nodes.get(i));
    var origDir = chain.smoothed().direction(i);
    var differential = translator().createDifferential(p0);
    try {
      differential.invert();
    } catch( NoninvertibleTransformException e ) {
      e.printStackTrace();
      return NO_RESPONSE.constantDrag();
    }
    var th = new TransformHelper();
    return (p2, mod2) -> {
      var dragged = p0.to(p2);
      if( dragged.length() < 10 )
        return clickResponse;
      var dir = th.applyDelta(differential, dragged).normalize();
      if( dir.dot(origDir) < 0 )
        dir = dir.reverse();
      var newNodes = new ArrayList<>(chain.nodes);
      newNodes.set(i, chain.nodes.get(i).withDirection(dir));
      var newChain = new SegmentChain(newNodes, chain.kinds, ChainClass.TRACK);
      return rewriteTo("Set locked heading", newChain)
          .with(new ArrowOverlay(dragged, RGB))
          .withPreview()
          .freeze();
    };
  }

}
