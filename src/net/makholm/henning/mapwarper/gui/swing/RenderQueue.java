package net.makholm.henning.mapwarper.gui.swing;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.swing.MapPainter.RenderBuffer;
import net.makholm.henning.mapwarper.gui.swing.MapPainter.RenderInstance;
import net.makholm.henning.mapwarper.util.BackgroundThread;

class RenderQueue {

  private final Set<RenderBuffer> queue = new LinkedHashSet<>();
  private double mousex, mousey;

  void offerMousePosition(Point p) {
    // these fields are updated without synchronization, which is why there
    // are two doubles instead of a reference to the point.
    mousex = p.x; mousey = p.y;
  }

  synchronized void enqueue(RenderBuffer buffer) {
    if( !buffer.working ) {
      queue.add(buffer);
      notify();
    }
  }

  private synchronized RenderInstance findWork(RenderInstance justFinished) {
    double mtilex = mousex / 256 - 0.5;
    double mtiley = mousey / 256 - 0.5;
    if( justFinished != null ) {
      justFinished.buffer.working = false;
      queue.add(justFinished.buffer);
    }
    for(;;) {
      RenderBuffer best = null;
      double bestPriority = -1;
      for( Iterator<RenderBuffer> it = queue.iterator(); it.hasNext(); ) {
        RenderBuffer buf = it.next();
        if( !buf.considerWork() ) {
          it.remove();
        } else {
          double priority = buf.activeInstance.priority();
          if( priority <= 0 ) {
            it.remove();
          } else if( priority > 0 ) {
            double dx = mtilex - buf.xtile;
            double dy = mtiley - buf.ytile;
            priority += 1/(dx*dx+dy*dy+1);
            if( priority > bestPriority ) {
              bestPriority = priority;
              best = buf;
            }
          }
        }
      }
      if( best != null ) {
        queue.remove(best);
        best.working = true;
        return best.activeInstance;
      }

      try {
        wait();
      } catch( InterruptedException e ) {
        e.printStackTrace();
      }
    }
  }

  void startRenderThreads() {
    new RenderThread("Map rendering").start();
  }

  private class RenderThread extends BackgroundThread {
    RenderThread(String name) {
      super(name);
    }

    @Override
    public void run() {
      RenderInstance work = null;
      for(;;) {
        work = findWork(work);
        work.doWork();
      }
    }
  }

}
