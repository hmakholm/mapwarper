package net.makholm.henning.mapwarper.gui.swing;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

final class TempToolReleaser {
  final char key;
  final Command target;

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
    this.target = null;
    this.waitingFor = NEVER;
  }

  TempToolReleaser(char key, Command target) {
    this.key = key;
    this.target = target;
    this.waitingFor = System.nanoTime() + WAIT_MILLIS * 1_000_000L;
  }

  boolean waitingFor(Command cmd) {
    return target == cmd && waitingFor < NEVER;
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
        disable();
        if( target.invocationKeyReleased(false, e.getModifiersEx()) )
          target.mapView.hairy.refreshScene();
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
    waitingFor = NEVER;
    if( target.invocationKeyReleased(true, 0) )
      target.mapView.hairy.refreshScene();
  }

}
