package net.makholm.henning.mapwarper.gui.swing;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import net.makholm.henning.mapwarper.geometry.Point;
import net.makholm.henning.mapwarper.gui.MouseAction.ExecuteWhy;

final class TempToolReleaser {
  final char key;
  final Tool switchFrom;
  final Tool switchTo;
  final Point initMouseLocal;

  /**
   * After this amount of time the tool selection is assumed to be meant
   * as temporary even if we haven't seen a button press.
   */
  public static final int WAIT_MILLIS = 600;

  private static final long NEVER = Long.MAX_VALUE;
  private static final long MOUSE_RELEASE = Long.MAX_VALUE-1;

  private long waitingFor;

  TempToolReleaser() {
    this.key = 0;
    this.switchFrom = null;
    this.switchTo = null;
    this.initMouseLocal = Point.ORIGIN;
    this.waitingFor = NEVER;
  }

  TempToolReleaser(Tool switchFrom, char key, Tool switchTo) {
    this.switchFrom = switchFrom;
    this.key = key;
    this.switchTo = switchTo;
    this.initMouseLocal = switchFrom.mapView().mouseLocal;
    this.waitingFor = System.nanoTime() + WAIT_MILLIS * 1_000_000L;
  }

  private boolean waitingForKeyRelease() {
    return waitingFor < MOUSE_RELEASE;
  }

  boolean waitingFor(char key) {
    return waitingForKeyRelease() && key == this.key;
  }

  void disable() {
    waitingFor = NEVER;
  }

  void hasActivity() {
    if( waitingForKeyRelease() )
      waitingFor = Long.MIN_VALUE;
  }

  void keyRelease(KeyEvent e) {
    if( e.getKeyChar() == key ) {
      if( System.nanoTime() > waitingFor ) {
        if( lingerForMouse(e) )
          waitingFor = MOUSE_RELEASE;
        else
          switchBack();
      } else {
        var a = switchFrom.simpleKeyAction(initMouseLocal,
            e.getModifiersEx() | Tool.QUICK_COM_MASK);
        if( a != null && a != Tool.NO_RESPONSE ) {
          a.execute(ExecuteWhy.QUICKTOOL);
          switchBack();
        } else {
          disable();
        }
      }
    }
  }

  void mouseRelease(MouseEvent e) {
    if( waitingFor == MOUSE_RELEASE && !lingerForMouse(e) )
      switchBack();
  }

  private boolean lingerForMouse(InputEvent e) {
    return (e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0;
  }

  private void switchBack() {
    System.err.println("[resume "+switchTo.codename+"]");
    waitingFor = NEVER;
    switchTo.mapView().selectTool(switchTo);
    switchTo.mapView().swing.refreshScene();
  }

}
