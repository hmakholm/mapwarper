package net.makholm.henning.mapwarper.gui.swing;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

@SuppressWarnings("serial")
class Hamburger extends JComponent {

  private final int margin;

  Hamburger(int totalSize, int margin, GuiMain window) {
    this.margin = margin;
    var dim = new Dimension(totalSize, totalSize);
    setSize(dim);
    setMaximumSize(dim);
    setMinimumSize(dim);
    setPreferredSize(dim);
    setAlignmentX(RIGHT_ALIGNMENT);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if( e.getButton() == 1 || e.getButton() == 3 ) {
          PopupMenu popup = new PopupMenu();
          window.commands.defineMenu(popup);
          popup.show(Hamburger.this, e.getX(), e.getY());
        }
      }
    });
  }

  @Override
  public void paint(Graphics g) {
    var h = Math.max(2, (getHeight()-2*margin)/8);
    var w = getWidth()-2*margin;
    g.fillRect(margin, margin+h, w, h);
    g.fillRect(margin, (getHeight() - h)/2, w, h);
    g.fillRect(margin, getHeight() - margin - 2*h, w, h);
  }

}
