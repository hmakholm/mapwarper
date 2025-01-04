package net.makholm.henning.mapwarper.gui.swing;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Objects;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;

import net.makholm.henning.mapwarper.geometry.AxisRect;
import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.geometry.Vector;
import net.makholm.henning.mapwarper.gui.MapView;
import net.makholm.henning.mapwarper.gui.MouseAction;
import net.makholm.henning.mapwarper.gui.MouseAction.ToolResponse;
import net.makholm.henning.mapwarper.gui.Teleporter;
import net.makholm.henning.mapwarper.gui.overlays.VectorOverlay;
import net.makholm.henning.mapwarper.gui.track.VisibleTrackData;

/**
 * The most framework-dependent UI code goes here.
 *
 * This covers implementation of painting and scrolling, and (as necessary)
 * translation of mouse positions to the projection's local coordinates.
 *
 * There's a 1:1 correspondence between this and {@link MapView}.
 */
@SuppressWarnings("serial")
public class SwingMapView extends JComponent
implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

  public final MapView logic;
  public final JScrollPane scrollPane;
  public final JViewport viewport;

  Rectangle viewportRect;
  long positionOffsetX, positionOffsetY;

  private MapPainter previousMapPainter;
  private MapPainter currentMapPainter;
  private MapPainter currentLensPainter;
  boolean someBuffersMayBeInvisible;

  private VisibleTrackData baseTrackData;
  private TrackPainter currentTrackPainter;

  private int modifierState;

  private Point toolResponsePoint = Point.ORIGIN;
  private int toolResponseModifiers;
  private MouseAction toolResponseTool = null;
  private ToolResponse toolResponse = () -> {};
  private VectorOverlay previousOverlay;

  private boolean everPaintedYet;

  public SwingMapView(MapView logic) {
    this.logic = logic;
    Dimension wantedSize = new Dimension(1<<20, 1<<20);
    setMinimumSize(wantedSize);
    setMaximumSize(wantedSize);
    setPreferredSize(wantedSize);
    setSize(wantedSize);
    this.scrollPane = new JScrollPane(this,
        JScrollPane.VERTICAL_SCROLLBAR_NEVER,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(null);
    scrollPane.setWheelScrollingEnabled(false);

    viewport = scrollPane.getViewport();
    viewportRect = viewport.getBounds();

    setFocusable(true);
    setFocusTraversalKeysEnabled(false);
    addMouseListener(this);
    addMouseMotionListener(this);
    addMouseWheelListener(this);
    addKeyListener(this);

    renderQueue.startRenderThreads();
  }

  public void readViewportRect() {
    viewportRect = viewport.getViewRect();
    logic.visibleArea.left = positionOffsetX + viewportRect.x;
    logic.visibleArea.right = logic.visibleArea.left + viewportRect.width;
    logic.visibleArea.top = positionOffsetY + viewportRect.y;
    logic.visibleArea.bottom = logic.visibleArea.top + viewportRect.height;
  }

  private Vector offsetAsVector() {
    return Vector.of(positionOffsetX, positionOffsetY);
  }

  /**
   * Call this from UI logic to ensure that the screen will eventually be
   * updated to match the desired fields in {@link MapView}.
   *
   * In most cases this will be handled lazily by scheduling a repaint
   * -- except that we react to projection and position changes immediately
   * by reconfiguring the JScrollPane.
   */
  public void refreshScene() {
    if( !everPaintedYet ) return;
    readViewportRect();

    logic.window.windowTitle.refresh();

    var newTrackData = logic.collectVisibleTrackData();
    if( !newTrackData.equals(baseTrackData) ) {
      baseTrackData = newTrackData;
      invalidateToolResponse();
    }

    refreshToolResponse(true);
    refreshPositionAndProjection();
    refreshMapAndLensLayers();
    refreshVectorLayer();
  }

  public void invalidateToolResponse() {
    toolResponseTool = null;
  }

  private void refreshToolResponse(boolean callerWillRefreshSceneLater) {
    MouseAction action = ongoingToolDrag;
    if( action == null ) action = logic.currentTool;
    if( toolResponseTool != action ||
        toolResponseModifiers != modifierState ||
        !toolResponsePoint.is(mousePosForTool) ) {
      toolResponseTool = action;
      toolResponseModifiers = modifierState;
      toolResponsePoint = mousePosForTool;
      if( mousePosForTool == OUTSIDE_WINDOW )
        toolResponse = Tool.NO_RESPONSE;
      else
        toolResponse = action.mouseResponse(mousePosForTool, modifierState);
      var newOverlay = toolResponse.previewOverlay();

      if( !Objects.equals(newOverlay, previousOverlay) ) {
        repaintFor(previousOverlay);
        repaintFor(newOverlay);
        previousOverlay = newOverlay;
      }

      var cursor = toolResponse.cursor();
      if( cursor == null ) cursor = logic.currentTool.toolCursor;
      if( cursor != getCursor() )
        setCursor(cursor);

      if( !callerWillRefreshSceneLater )
        refreshVectorLayer();
    }
  }

  private void refreshPositionAndProjection() {
    boolean shouldRepaintAll = true;
    if( currentMapPainter != null &&
        !currentMapPainter.projection.equals(logic.projection) ) {
      resetProjectionAtShiftUp = null;
      moveWithoutMovingSwing();
    } else if( logic.positionX == positionOffsetX + viewportRect.x &&
        logic.positionY == positionOffsetY + viewportRect.y ) {
      shouldRepaintAll = false;
    } else {
      long relX = logic.positionX - positionOffsetX;
      long relY = logic.positionY - positionOffsetY;
      if( Math.abs(relX - viewportRect.x) >= viewportRect.width ||
          Math.abs(relY - viewportRect.y) >= viewportRect.height ) {
        // Since there's no overlap between the old and new position,
        // do the move in place
        moveWithoutMovingSwing();
      } else if( relX < 0 && relX + viewportRect.width >= getWidth() &&
          relY < 0 && relY + viewportRect.height >= getHeight() ) {
        // It's a short move, but, alas, it would take us off
        // what Swing thinks is the edge of the world.
        recenterPosition();
      } else {
        // It's a nice short move where we can let JScrolPane handle
        // reuse of the overlap.
        setViewportPosition((int)relX, (int)relY);
        shouldRepaintAll = false;
      }

      if( logic.lensRect != null &&
          clipbox(logic.lensRect, viewportRect) == null ) {
        logic.lensRect = null;
      }
    }

    if( shouldRepaintAll )
      repaint();
  }

  private void recenterPosition() {
    supersedeMapPainter();
    setPreviousMapPainter(null);

    int x = (getWidth() - viewportRect.width)/2;
    int y = (getHeight() - viewportRect.height)/2;
    positionOffsetX = logic.positionX - x;
    positionOffsetY = logic.positionY - y;
    setViewportPosition(x, y);
  }

  private void moveWithoutMovingSwing() {
    supersedeMapPainter();
    positionOffsetX = logic.positionX - viewportRect.x;
    positionOffsetY = logic.positionY - viewportRect.y;
    refreshLogicalMousePosition();
  }

  public void refreshMapAndLensLayers() {
    if( currentMapPainter == null ||
        currentMapPainter.perhapsChangeSpec(logic.dynamicMapLayerSpec) )
      repaint();
    if( currentLensPainter != null &&
        currentLensPainter.perhapsChangeSpec(logic.dynamicLensSpec) )
      repaintFor(logic.lensRect);
  }

  private void refreshVectorLayer() {
    var visibleTrackData = toolResponse.previewTrackData();
    var newTrackPainter = new TrackPainter(this,
        visibleTrackData != null ? visibleTrackData : baseTrackData);
    if( !newTrackPainter.equals(currentTrackPainter) ) {
      currentTrackPainter = newTrackPainter;
      repaint();
    }
  }

  public void repaintFor(VectorOverlay vo) {
    Rectangle clip = clipbox(vo, viewportRect);
    if( clip != null )
      repaint(clip);
  }

  private void supersedeMapPainter() {
    if( currentMapPainter != null ) {
      currentMapPainter.supersede();
      setPreviousMapPainter(currentMapPainter);
      currentMapPainter = null;
      currentLensPainter = null;
    }
  }

  private void setPreviousMapPainter(MapPainter newPrevious) {
    if( previousMapPainter != null )
      previousMapPainter.disposeCompletely();
    previousMapPainter = newPrevious;
  }

  private void setViewportPosition(int x, int y) {
    viewport.setViewPosition(new java.awt.Point(x,y));
    windowMousePosition = Point.at(
        windowMousePosition.x + x - viewportRect.x,
        windowMousePosition.y + y - viewportRect.y);
    viewportRect.x = x;
    viewportRect.y = y;
    refreshLogicalMousePosition();
    someBuffersMayBeInvisible = true;
  }

  public void repaintFromScratch() {
    readViewportRect();
    recenterPosition();
    repaint();
  }

  // ----------------------------------------------------------------------

  private Teleporter resetProjectionAtShiftUp;

  private static final Point OUTSIDE_WINDOW = Point.at(
      Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
  private Point mousePosForTool = OUTSIDE_WINDOW;

  private MouseEvent dragStartingEvent;
  private MouseAction ongoingToolDrag;
  private java.awt.Point ongoingMapDrag;

  private void cancelDrag() {
    ongoingMapDrag = null;
    dragStartingEvent = null;
  }

  /**
   * This cached mouse position is relative to the
   * {@link SwingMapView} component itself.
   */
  private Point windowMousePosition = Point.at(0);

  public void mousePositionAdjusted() {
    var newPosition = logic.mouseLocal.minus(offsetAsVector());
    windowMousePosition = newPosition;
    if( mousePosForTool != OUTSIDE_WINDOW )
      mousePosForTool = logic.mouseLocal;
  }

  private void readMousePosition(MouseEvent e) {
    modifierState = e.getModifiersEx();
    windowMousePosition = Point.at(e.getX()+0.5, e.getY()+0.5);
    refreshLogicalMousePosition();
    mousePosForTool = logic.mouseLocal;
  }

  private void refreshLogicalMousePosition() {
    logic.mouseLocal = windowMousePosition.plus(offsetAsVector());
    logic.mouseGlobal = logic.translator().local2global(logic.mouseLocal);
    renderQueue.offerMousePosition(logic.mouseLocal);
    logic.tiles.downloader.offerMousePosition(logic.mouseGlobal);
  }

  /**
   * Return true if there's a map dragging event in progress,
   * such that mouse events should not be passed to the main UI
   * logic as normal. (That's easier than making sure they'll
   * react appropriately to the mouse moving without its map
   * position moving with it).
   *
   * Return false if there's no drag event active.
   */
  private boolean checkOngoingDrag(MouseEvent e) {
    if( ongoingMapDrag == null && ongoingToolDrag == null ) {
      return false;
    } else if( 0 == (e.getModifiersEx() &
        (MouseEvent.BUTTON1_DOWN_MASK | MouseEvent.BUTTON2_DOWN_MASK)) ) {
      // Stop the drag as soon as we see no mouse buttons down.
      // Some other programs have an annoying behavior where they'll
      // sometimes lose the up-event. That's particularly bad when
      // dragging a map, so belt and suspenders here.
      ongoingMapDrag = null;
      ongoingToolDrag = null;
      return false;
    } else {
      return true;
    }
  }

  @Override
  public void mouseEntered(MouseEvent e) {
    readMousePosition(e);
  }

  @Override
  public void mouseExited(MouseEvent arg0) {
    readViewportRect();
    windowMousePosition = Point.at(
        viewportRect.getCenterX(), viewportRect.getCenterY());
    refreshLogicalMousePosition();
    mousePosForTool = OUTSIDE_WINDOW;
    refreshToolResponse(false);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    cancelDrag();
    readMousePosition(e);
    if( e.getButton() == 3 ) {
      PopupMenu popup = new PopupMenu();
      logic.window.commands.defineMenu(popup);
      popup.show(this, e.getX(), e.getY());
    } else {
      dragStartingEvent = e;
      refreshToolResponse(false);
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    resetProjectionAtShiftUp = null;
    if( ongoingMapDrag != null )
      cancelDrag();
    else if( ongoingToolDrag != null && e.getButton() == 1 ) {
      readMousePosition(e);
      refreshToolResponse(true);
      ongoingToolDrag = null;
      invalidateToolResponse();
      toolResponse.execute();
      // artificially kill the modifiers right after a drag so one can
      // see the result.
      modifierState = 0;
      refreshScene();
      return;
    }
    readMousePosition(e);
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    cancelDrag();
    if( e.getButton() == 1 ) {
      readMousePosition(e);
      refreshToolResponse(true);
      invalidateToolResponse();
      toolResponse.execute();
      modifierState = 0;
      refreshScene();
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    if( checkOngoingDrag(e) ) return;
    readMousePosition(e);
    refreshToolResponse(false);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if( checkOngoingDrag(e) ) {
      if( ongoingMapDrag != null ) {
        java.awt.Point now = e.getLocationOnScreen();
        int dx = ongoingMapDrag.x - now.x;
        int dy = ongoingMapDrag.y - now.y;
        if( dx == 0 && dy == 0 ) return ;
        logic.positionX += dx;
        logic.positionY += dy;
        ongoingMapDrag = now;
        refreshPositionAndProjection();
      } else {
        readMousePosition(e);
        refreshToolResponse(false);
      }
    } else if( dragStartingEvent == null ) {
      readMousePosition(e);
    } else {
      readMousePosition(dragStartingEvent);
      modifierState = e.getModifiersEx();
      switch( dragStartingEvent.getButton() ) {
      case 1:
        MouseAction dragAction =
        logic.currentTool.drag(logic.mouseLocal, modifierState);
        if( dragAction == Tool.DRAG_THE_MAP ) {
          // fall through to starting a map drag in the next case
        } else {
          ongoingToolDrag = dragAction;
          refreshToolResponse(false);
          break;
        }
      case 2:
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        ongoingMapDrag = dragStartingEvent.getLocationOnScreen();
        break;
      default:
        break;
      }
      dragStartingEvent = null;
    }
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    ongoingMapDrag = null;
    if( e.getWheelRotation() < 0 )
      logic.window.commands.zoomIn.invoke();
    else if( e.getWheelRotation() > 0 )
      logic.window.commands.zoomOut.invoke();
    else
      return;
    refreshScene();
  }

  @Override
  public void keyPressed(KeyEvent e) {
    modifierState = e.getModifiersEx();
    if( e.getKeyCode() == KeyEvent.VK_SHIFT ) {
      var tempProj = toolResponseTool.shiftDownProjectionSwitcher(
          logic.mouseLocal, modifierState);
      if( tempProj != null ) {
        ongoingToolDrag = null;
        Teleporter restore = new Teleporter(logic);
        tempProj.run();
        refreshScene();
        resetProjectionAtShiftUp = restore;
        return;
      }
    }
    refreshToolResponse(false);
  }

  @Override
  public void keyReleased(KeyEvent e) {
    modifierState = e.getModifiersEx();
    if( e.getKeyCode() == KeyEvent.VK_SHIFT &&
        resetProjectionAtShiftUp != null ) {
      resetProjectionAtShiftUp.apply();
      resetProjectionAtShiftUp = null;
      logic.disableTempProjectionsOnShift = false;
    }
    refreshToolResponse(false);
  }

  @Override
  public void keyTyped(KeyEvent e) {
    // nothing
  }

  public void commitToTempProjection() {
    resetProjectionAtShiftUp = null;
  }

  // ------------------------------------------------------------------
  //     P A I N T I N G
  // ------------------------------------------------------------------

  @Override
  public void paint(Graphics g0) {
    readViewportRect();
    if( !everPaintedYet ) {
      logic.setInitialPosition();
      everPaintedYet = true;
      refreshScene();
    }

    if( currentLensPainter != null && !currentLensPainter.stillCurrent() ) {
      repaintFor(currentLensPainter.clipRender);
      currentLensPainter = null;
    }

    if( someBuffersMayBeInvisible ) {
      if( currentMapPainter != null )
        currentMapPainter.discardInvisibleBuffers();
      if( currentLensPainter != null )
        currentLensPainter.discardInvisibleBuffers();
      someBuffersMayBeInvisible = false;
    }

    if( currentMapPainter == null || !currentMapPainter.stillCurrent() ) {
      supersedeMapPainter();
      currentMapPainter = new MapPainter(this, logic.dynamicMapLayerSpec);
    } else if( previousMapPainter != null && !currentMapPainter.stillYoung() ) {
      setPreviousMapPainter(null);
    }

    if( currentLensPainter == null && logic.lensRect != null ) {
      currentLensPainter = new MapPainter(this, logic.dynamicLensSpec);
    }

    Graphics2D g = SwingUtils.startPaint(g0);
    Rectangle bounds = new Rectangle();
    g.getClipBounds(bounds);
    AxisRect dbounds = new AxisRect(
        Point.at(bounds.getMinX(), bounds.getMinY()),
        Point.at(bounds.getMaxX(), bounds.getMaxY()));

    g.setColor(new Color(0xAAAAAA));
    g.fill(bounds);
    if( previousMapPainter != null )
      previousMapPainter.paint(g, bounds);
    currentMapPainter.paint(g, bounds);

    if( currentLensPainter != null )
      currentLensPainter.paint(g, bounds);

    paintVectorOverlay(logic.lensRect, g, bounds);

    AxisRect lbounds =
        dbounds.translate(Vector.of(positionOffsetX, positionOffsetY));

    var ggt = (Graphics2D)g.create();
    ggt.translate(-positionOffsetX, -positionOffsetY);
    currentTrackPainter.paint(ggt, lbounds);

    paintVectorOverlay(toolResponse.previewOverlay(), g, bounds);
  }

  private void paintVectorOverlay(VectorOverlay vo, Graphics2D g,
      Rectangle iclip) {
    Rectangle clip = clipbox(vo, iclip);
    if( clip != null ) {
      var gg = (Graphics2D)g.create();
      gg.clipRect(clip.x, clip.y, clip.width, clip.height);
      gg.translate(-positionOffsetX, -positionOffsetY);
      vo.paint(gg);
    }
  }

  private Rectangle clipbox(VectorOverlay vo, Rectangle rect) {
    if( vo == null )
      return null;
    AxisRect bbox = vo.boundingBox();
    var xmin = Math.floor(bbox.xmin()) - positionOffsetX;
    var xmax = Math.ceil(bbox.xmax()) - positionOffsetX;
    var ymin = Math.floor(bbox.ymin()) - positionOffsetY;
    var ymax = Math.ceil(bbox.ymax()) - positionOffsetY;
    if( xmax > rect.x && xmin < rect.x + rect.width &&
        ymax > rect.y && ymin < rect.y + rect.height )
      return new Rectangle((int)xmin, (int)ymin,
          (int)(xmax-xmin), (int)(ymax-ymin));
    else
      return null;
  }

  final RenderQueue renderQueue = new RenderQueue();

}
