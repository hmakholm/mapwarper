package net.makholm.henning.mapwarper.gui.swing;

import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.makholm.henning.mapwarper.gui.Toggles;
import net.makholm.henning.mapwarper.gui.maprender.FrozenLayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.LayerSpec;
import net.makholm.henning.mapwarper.gui.maprender.RenderFactory;
import net.makholm.henning.mapwarper.gui.maprender.RenderTarget;
import net.makholm.henning.mapwarper.gui.maprender.RenderWorker;
import net.makholm.henning.mapwarper.gui.overlays.BoxOverlay;
import net.makholm.henning.mapwarper.gui.projection.Projection;
import net.makholm.henning.mapwarper.rgb.RGB;
import net.makholm.henning.mapwarper.util.AbortRendering;
import net.makholm.henning.mapwarper.util.BadError;

class MapPainter {

  private final SwingMapView owner;

  final Projection projection;
  private FrozenLayerSpec spec;
  private RenderFactory factory;

  private final Map<Long, RenderBuffer> buffers = new LinkedHashMap<>();
  private final Set<Long> youngBuffers = new LinkedHashSet<>();

  private long youthThresholdNanos;

  private boolean superseded;

  private final long positionOffsetX;
  private final long positionOffsetY;
  final BoxOverlay clipRender;

  MapPainter(SwingMapView owner, LayerSpec initialSpec) {
    this.owner = owner;
    this.positionOffsetX = owner.positionOffsetX;
    this.positionOffsetY = owner.positionOffsetY;
    this.spec = new FrozenLayerSpec(initialSpec);
    this.projection = spec.projection;
    this.factory = projection.makeRenderFactory(spec);
    clipRender = Toggles.OVERLAY_MAP.setIn(spec.flags())
        ? owner.logic.lensRect : null;

    createBuffers();
    youthThresholdNanos = System.nanoTime() + 30_000_000_000L;
  }

  boolean stillCurrent() {
    return positionOffsetX == owner.positionOffsetX &&
        positionOffsetY == owner.positionOffsetY &&
        spec.projection.equals(owner.logic.projection) &&
        (clipRender == null || clipRender.equals(owner.logic.lensRect));
  }

  private void createBuffers() {
    int xmin = (int)(owner.logic.visibleArea.left >> 8);
    int xmax = (int)((owner.logic.visibleArea.right-1) >> 8);
    int ymin = (int)(owner.logic.visibleArea.top >> 8);
    int ymax = (int)((owner.logic.visibleArea.bottom-1) >> 8);

    for( int x = xmin; x <= xmax; x++ ) {
      for( int y = ymin; y <= ymax; y++ ) {
        buffers.computeIfAbsent(bufferKey(x,y), RenderBuffer::new);
      }
    }
  }

  boolean perhapsChangeSpec(LayerSpec newSpec) {
    if( spec.equals(newSpec) ) {
      return false;
    } else if( !newSpec.projection().equals(projection) ) {
      throw BadError.of("Unexpected projection change from %s to %s",
          projection, newSpec.projection());
    } else {
      spec = new FrozenLayerSpec(newSpec);
      factory = projection.makeRenderFactory(spec);
      buffers.values().forEach(b -> {
        synchronized(b) {
          b.wantInstance = null;
        }
      });
      return true;
    }
  }

  void discardInvisibleBuffers() {
    int xmin = (int)(owner.logic.visibleArea.left >> 8);
    int xmax = (int)((owner.logic.visibleArea.right-1) >> 8);
    int ymin = (int)(owner.logic.visibleArea.top >> 8);
    int ymax = (int)((owner.logic.visibleArea.bottom-1) >> 8);

    var discardedKeys = new ArrayList<Long>();
    buffers.forEach((key, buffer) -> {
      if( buffer.xtile < xmin || buffer.xtile > xmax ||
          buffer.ytile < ymin || buffer.ytile > ymax ) {
        buffer.discard();
        discardedKeys.add(key);
      }
    });
    discardedKeys.forEach(buffers::remove);
  }

  private ArrayList<RenderBuffer> buffersToStartNow = new ArrayList<>();

  void paint(Graphics2D g, Rectangle bounds) {
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    long left = bounds.x + positionOffsetX;
    long right = left + bounds.width;
    long top = bounds.y + positionOffsetY;
    long bottom = top + bounds.height;

    // System.err.println("Painting ("+left+".."+right+")x("+top+".."+bottom+") of "+projection);

    buffersToStartNow.clear();
    for( int x = (int)(left >> 8); x * 256L < right; x++ ) {
      for( int y = (int)(top >> 8); y * 256L < bottom; y++ ) {
        int xxx = (int)(x * 256L - positionOffsetX);
        int yyy = (int)(y * 256L - positionOffsetY);
        if( !g.hitClip(xxx,  yyy, 256,  256) ) continue;
        Long key = bufferKey(x, y);
        RenderBuffer buffer;
        if( superseded ) {
          // Don't kick off any new rendering in the superseded layer.
          buffer = buffers.get(key);
          if( buffer == null ) continue;
        } else {
          buffer = buffers.computeIfAbsent(key, RenderBuffer::new);
          buffersToStartNow.add(buffer);
        }
        Graphics2D gg = (Graphics2D) g.create(xxx, yyy, 256, 256);
        buffer.paint(gg);
      }
    }
    if( !buffersToStartNow.isEmpty() ) {
      owner.renderQueue.blockWakingUp();
      try {
        buffersToStartNow.forEach(RenderBuffer::updateWantInstance);
      } finally {
        owner.renderQueue.unblockWakingUp();
        buffersToStartNow.clear();
      }
    }
  }

  boolean stillYoung() {
    synchronized( youngBuffers ) {
      if( youngBuffers.isEmpty() ) {
        return false;
      } else if( System.nanoTime() > youthThresholdNanos ) {
        return false;
      } else {
        return true;
      }
    }
  }

  void supersede() {
    superseded = true;
  }

  void disposeCompletely() {
    superseded = true; // just in case ...
    buffers.values().forEach(RenderBuffer::discard);
    buffers.clear();
  }

  @Override
  public String toString() {
    return "MapPainter "+projection+" @ "+positionOffsetX+","+positionOffsetY;
  }

  private static Long bufferKey(int x, int y) {
    return Long.valueOf(((long)x << 32) | (y & 0xFFFF_FFFFL));
  }

  /**
   * This class handles background rendering of one 256x256 tile in a
   * particular projection. The tiles positions always align on local
   * coordinates that are multiples of 256; the then the _multipliers_
   * can be represented as 32-bit integers, and there's room for addressing
   * 2^39 actual device pixels to scroll around in. If that is spread
   * across the whole earth, they would have a pitch of about 300 dpi
   * at the equator.
   *
   * This also conveniently lets us use Longs as keys for the maps that
   * contain the managers.
   *
   * All these objects are discarded when we change projection, so they
   * don't need to be keyed on the projection itself.
   *
   * The code <em>here</em> runs in the UI thread. Code that runs
   * in rendering threads is instead in {@link RenderInstance} below.
   */
  class RenderBuffer {
    final int xtile, ytile;

    /**
     * Thread-shared but without explicit synchronization. The UI thread
     * just reads whatever is in the buffer when it needs to paint.
     */
    final BufferedImage buffer;

    final long xmin, ymin;
    final short imgWidth, imgHeight;

    /** Belongs to the render thread. */
    boolean isYoung = true;

    /**
     * Thread-shared, so setting this to true takes the {@link RenderBuffer}
     * lock.
     */
    boolean dead;

    /**
     * Thread-shared, so accesses must take the {@link RenderBuffer} lock.
     *
     * This is initialized to a non-null value in the UI thread by
     * {@link #updateWantInstance()} before the buffer is first given
     * to the render queue. After that has happened, once the field gets
     * set to {@code null} (to indicate that the buffer will never be
     * used again), it stays {@code null} for ever, so it is admissible
     * to check <em>that</em> without taking the lock.
     */
    RenderInstance wantInstance;

    /**
     * This belongs to the render queue.
     */
    RenderInstance activeInstance;

    /** This belongs to the render queue. */
    boolean working;

    /** Runs in the UI thread */
    RenderBuffer(Long key) {
      this.xtile = (int)(key >> 32);
      this.ytile = key.intValue();

      if( clipRender == null ) {
        xmin = 256L * xtile;
        ymin = 256L * ytile;
        imgWidth = imgHeight = 256;
      } else {
        var box = clipRender.box;
        xmin = Math.max(256L*xtile,Math.round(box.xmin()));
        ymin = Math.max(256L*ytile,Math.round(box.ymin()));
        long xmax = Math.min(256L*(xtile+1),Math.round(box.xmax()));
        long ymax = Math.min(256L*(ytile+1),Math.round(box.ymax()));
        if( xmin >= xmax || ymin >= ymax ) {
          imgWidth = imgHeight = 0;
          buffer = null;
          return;
        } else {
          imgWidth = (short)(xmax-xmin);
          imgHeight = (short)(ymax-ymin);
        }
      }

      synchronized( youngBuffers ) {
        youngBuffers.add(key);
      }

      this.buffer = owner.getGraphicsConfiguration()
          .createCompatibleImage(imgWidth, imgHeight, Transparency.TRANSLUCENT);
    }

    /** Runs in the UI thread */
    void updateWantInstance() {
      if( buffer == null ) return;
      synchronized( RenderBuffer.this ) {
        if( wantInstance != null )
          return ;
        wantInstance = new RenderInstance(this, MapPainter.this.spec);
      }
      owner.renderQueue.enqueue(this);
    }

    /**
     * This runs in a <em>render</em> thread. Returns true if we still
     * want work, false to drop silently from the queue.
     */
    boolean considerWork() {
      if( superseded || dead || buffer == null ) {
        if( activeInstance != null ) {
          activeInstance.dispose();
          activeInstance = null;
        }
      } else {
        synchronized( RenderBuffer.this ) {
          if( activeInstance != wantInstance ) {
            if( activeInstance != null )
              activeInstance.dispose();
            activeInstance = wantInstance;
          }
        }
      }
      return activeInstance != null;
    }

    /** Runs in the UI thread */
    void paint(Graphics2D g) {
      if( buffer != null )
        g.drawImage(buffer, (int)xmin & 0xFF, (int)ymin & 0xFF, null);
    }

    /** Runs in the UI thread */
    void discard() {
      synchronized( RenderBuffer.this ) {
        if( wantInstance != null ) {
          wantInstance.dispose();
          wantInstance = null;
        }
        dead = true;
      }
    }

    @Override
    public String toString() {
      return String.format(Locale.ROOT, "tile buffer (%d,%d) of %s",
          xtile, ytile, projection);
    }
  }

  /**
   * Most things in this class runs in a render thread.
   */
  class RenderInstance implements RenderTarget {
    final RenderBuffer buffer;
    final LayerSpec instanceSpec;
    final int flags;
    final int alphaBits;
    final int darkenShift;
    final int darkenMask;

    RenderWorker worker;

    /**
     * The constructor runs in the UI thread.
     */
    RenderInstance(RenderBuffer buffer, LayerSpec spec) {
      this.buffer = buffer;
      this.instanceSpec = spec;
      this.flags = spec.flags();
      this.alphaBits = Toggles.LENS_MAP.setIn(flags) ? 0 : RGB.OPAQUE;
      if( Toggles.LENS_MAP.setIn(flags) &&
          spec.targetZoom() <= spec.mainTiles().guiTargetZoom ) {
        darkenShift = 3;
        darkenMask = 0x0000001F;
      } else if( Toggles.DARKEN_MAP.setIn(flags) &&
          !Toggles.TILEGRID.setIn(flags)) {
        darkenShift = 2;
        darkenMask = 0x003F3F3F;
      } else {
        darkenShift = 0;
        darkenMask = 0;
      }
    }

    @Override
    public long left() {
      return buffer.xmin;
    }

    @Override
    public long top() {
      return buffer.ymin;
    }

    @Override
    public int columns() {
      return buffer.imgWidth;
    }

    @Override
    public int rows() {
      return buffer.imgHeight;
    }

    @Override
    public boolean isUrgent() {
      return buffer.isYoung;
    }

    int priority() {
      return worker == null ? 1000 : worker.priority();
    }

    void doWork() {
      if( worker == null )
        worker = factory.makeWorker(this);
      clearQueuedRepaints();
      try {
        worker.doSomeWork();
      } catch( AbortRendering e ) {
        return;
      }
      flushRepainting();
    }

    private int smallestX, largestX;
    private int smallestY, largestY;
    long repaintNanosThreshold;

    private void clearQueuedRepaints() {
      smallestX = 256;
      largestX = -1;
      repaintNanosThreshold = System.nanoTime() + 20_000_000;
    }

    @Override
    public void givePixel(int x, int y, int rgb) {
      rgb = (rgb | alphaBits) - ((rgb >> darkenShift) & darkenMask);
      buffer.buffer.setRGB(x, y, rgb);
      if( x < smallestX ) smallestX = x;
      if( x > largestX ) largestX = x;
      if( y < smallestY ) smallestY = y;
      if( y > largestY ) largestY = y;
    }

    @Override
    public void isNowGrownUp() {
      if( buffer.isYoung ) {
        synchronized( youngBuffers ) {
          youngBuffers.remove(bufferKey(buffer.xtile, buffer.ytile));
          buffer.isYoung = false;
        }
      }
    }

    @Override
    public void checkCanceled() throws AbortRendering {
      synchronized( buffer ) {
        if( buffer.wantInstance != this )
          throw new AbortRendering();
      }
      if( System.nanoTime() > repaintNanosThreshold ) {
        flushRepainting();
        clearQueuedRepaints();
      }
    }

    void flushRepainting() {
      if( smallestY <= largestY ) {
        int x0 = (int)(buffer.xmin + smallestX - positionOffsetX);
        int y0 = (int)(buffer.ymin + smallestY - positionOffsetY);
        int width = largestX - smallestX + 1;
        int height = largestY - smallestY + 1;
        EventQueue.invokeLater(() -> owner.repaint(x0, y0, width, height));
      }
    }

    /**
     * This is generally called with the render buffer lock held,
     * and must be careful not to let that spiral into deadlocks.
     */
    void dispose() {
      if( worker != null ) {
        var toDispose = worker;
        worker = null;
        owner.logic.window.miscAsyncWork.execute(toDispose::dispose);
      }
    }

    @Override
    public void pokeSchedulerAsync() {
      synchronized( buffer ) {
        if( buffer.activeInstance != this )
          return;
      }
      owner.renderQueue.enqueue(buffer);
    }

    @Override
    public String toString() {
      return buffer + " with " + instanceSpec;
    }
  }

}
