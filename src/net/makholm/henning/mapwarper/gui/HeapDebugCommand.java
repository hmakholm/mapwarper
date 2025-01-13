package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.tiles.TileContext;

class HeapDebugCommand {

  static void run(TileContext tiles) {
    reportMemUsage();
    System.err.println("GC"); System.gc();
    reportMemUsage();
    System.err.println("GC"); System.gc();
    reportMemUsage();
    tiles.ramCache.clear();
    System.err.println("GC"); System.gc();
    reportMemUsage();
  }

  private static void reportMemUsage() {
    var runtime = Runtime.getRuntime();
    long max = runtime.maxMemory();
    long used = runtime.totalMemory() - runtime.freeMemory();
    int percent = (int)(used*100/max);
    System.err.println("Using "+(used>>20)+" of "+(max>>20)+" MB ("+percent+"%)");
  }

}
