package net.makholm.henning.mapwarper.gui.swing;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.swing.MapPainter.RenderBuffer;
import net.makholm.henning.mapwarper.gui.swing.MapPainter.RenderInstance;
import net.makholm.henning.mapwarper.util.BackgroundThread;
import net.makholm.henning.mapwarper.util.XmlConfig;

class RenderQueue {

  private final Set<RenderBuffer> queue = new LinkedHashSet<>();
  private boolean blockWakingUp;
  private double mousex, mousey;

  private final RenderThread[] threads;

  RenderQueue(XmlConfig config) {
    Integer numThreads = config.integer("mapRender", "", "numThreads");
    if( numThreads == null || numThreads < 1 )
      numThreads = 1;
    threads = new RenderThread[numThreads];
    for( int i=0; i<threads.length; i++ )
      threads[i] = new RenderThread("Map rendering "+(i+1)+"/"+numThreads);
  }

  void offerMousePosition(Point p) {
    // these fields are updated without synchronization, which is why there
    // are two doubles instead of a reference to the point.
    mousex = p.x; mousey = p.y;
  }

  synchronized void blockWakingUp() {
    blockWakingUp = true;
  }

  synchronized void enqueue(RenderBuffer buffer) {
    if( !buffer.working ) {
      queue.add(buffer);
      if( !blockWakingUp )
        notify();
    }
  }

  synchronized void unblockWakingUp() {
    blockWakingUp = false;
    notify();
  }

  private synchronized RenderInstance findWork(RenderThread thread) {
    if( thread.workingOn != null ) {
      thread.workingOn.working = false;
      queue.add(thread.workingOn);
      thread.workingOn = null;
    }
    for(;;) {
      double mtilex = mousex / 256 - 0.5;
      double mtiley = mousey / 256 - 0.5;
      RenderBuffer best = null;
      int bestPriority = -1;
      double bestScore = 0;
      for( Iterator<RenderBuffer> it = queue.iterator(); it.hasNext(); ) {
        RenderBuffer buf = it.next();
        if( !buf.considerWork() ) {
          it.remove();
        } else {
          int priority = buf.activeInstance.priority();
          if( priority <= 0 ) {
            it.remove();
          } else if( priority < bestPriority ) {
            // Ignore this candidate
          } else {
            long osqrdist = Long.MAX_VALUE;
            for( var t : threads ) {
              if( t.workingOn != null ) {
                long dx = t.workingOn.xtile - buf.xtile;
                long dy = t.workingOn.ytile - buf.ytile;
                osqrdist = Math.min(osqrdist, dx*dx + dy*dy);
              }
            }

            double dx = mtilex - buf.xtile;
            double dy = mtiley - buf.ytile;
            double cdist = Math.sqrt(dx*dx+dy*dy);
            if( osqrdist != Long.MAX_VALUE ) {
              // Lightly prefer to work on tiles that are longer away from
              // other tiles that are being worked on (so they won't both
              // block on _loading_ the same map tile from disk) -- but not
              // so much that it leads us to choose tiles far from the
              // mouse too!
              cdist -= Math.sqrt(osqrdist)/2;
            }

            if( priority > bestPriority || cdist < bestScore ) {
              bestPriority = priority;
              bestScore = cdist;
              best = buf;
            }
          }
        }
      }
      if( best != null ) {
        queue.remove(best);
        best.working = true;
        thread.workingOn = best;
        thread.burstLength++;
        return best.activeInstance;
      }

      try {
        if( verbose && thread.burstLength > 0 ) {
          double secs = (System.nanoTime()-thread.startedBurstAt)*1e-9;
          System.out.printf(Locale.ROOT,"%s worked %d buffers in %.3g secs (avg %.3g)\n",
              thread.getName(), thread.burstLength, secs, secs/thread.burstLength);
        }
        wait();
        thread.startedBurstAt = System.nanoTime();
        thread.burstLength = 0;
      } catch( InterruptedException e ) {
        e.printStackTrace();
      }
    }
  }

  void startRenderThreads() {
    for( var t : threads ) t.start();
  }

  boolean verbose = false;

  private class RenderThread extends BackgroundThread {
    long startedBurstAt = System.nanoTime();
    int burstLength;
    RenderBuffer workingOn;

    RenderThread(String name) {
      super(name);
    }

    @Override
    public void run() {
      for(;;) {
        var work = findWork(this);
        work.doWork();
      }
    }
  }

}
