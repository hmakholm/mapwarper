package net.makholm.henning.mapwarper.gui.swing;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import net.makholm.henning.mapwarper.geometry.Point;

final class TempToolReleaser {
  final char key;
  final Tool switchFrom;
  final Tool switchTo;
  final Point initMouseLocal;

  private boolean enabled;
  private boolean seenActivity;
  private boolean waitingForMouseRelease;

  TempToolReleaser() {
    this.key = 0;
    this.switchFrom = null;
    this.switchTo = null;
    this.initMouseLocal = Point.ORIGIN;
  }

  TempToolReleaser(Tool switchFrom, char key, Tool switchTo) {
    this.switchFrom = switchFrom;
    this.key = key;
    this.switchTo = switchTo;
    this.initMouseLocal = switchTo.mapView().mouseLocal;
    enabled = key != KeyEvent.CHAR_UNDEFINED;
  }

  boolean waitingFor(char key) {
    return enabled && key == this.key;
  }

  void disable() {
    enabled = false;
  }

  void hasActivity() {
    seenActivity = true;
  }

  void mouseMove(Point newLocal) {
    if( enabled && newLocal.sqDist(initMouseLocal) > 200 )
      hasActivity();
  }

  void keyRelease(KeyEvent e) {
    if( enabled && e.getKeyChar() == key ) {
      if( !seenActivity )
        disable();
      else if( lingerForMouse(e) )
        waitingForMouseRelease = true;
      else
        switchBack();
    }
  }

  void mouseRelease(MouseEvent e) {
    if( enabled && waitingForMouseRelease && !lingerForMouse(e) )
      switchBack();
  }

  private boolean lingerForMouse(InputEvent e) {
    return (e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0;
  }

  private void switchBack() {
    System.err.println("[resume "+switchTo.codename+"]");
    enabled = false;
    switchTo.mapView().selectTool(switchTo);
    switchTo.mapView().swing.refreshScene();
  }

}
