package net.makholm.henning.mapwarper.gui.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Collections;

import javax.swing.JComponent;

import net.makholm.henning.mapwarper.gui.files.FilePane;
import net.makholm.henning.mapwarper.rgb.RGB;

@SuppressWarnings("serial")
public class SwingFilePane extends JComponent {

  public final FilePane logic;

  public static final int OVERLAP = 3;
  public static final int PREFERRED_WIDTH = 200;

  private final Font italicFont;

  /**
   * Unfortunately needs to be mutable, because we only get a reliable
   * baseline distance at the first repaint operation ...
   */
  private int entryHeight;
  private int iconWidth;

  private final BufferedImage eyeIcon;

  public SwingFilePane(FilePane logic) {
    this.logic = logic;
    italicFont = SwingUtils.getANiceItalicFont();
    Font font = SwingUtils.getANiceDefaultFont();
    setFont(font);
    addMouseListener(mouseListener);

    eyeIcon = SwingUtils.loadBundledImage("eyeIcon.png")
        .orElseGet(() -> {
          var img = new BufferedImage(BufferedImage.TYPE_INT_RGB, 3, 2);
          var g = img.createGraphics();
          g.setColor(Color.RED);
          g.fillRect(0, 0, 3, 2);
          return img;
        });
  }

  private FilePane.Entry[] currentView = {};

  private final MouseAdapter mouseListener = new MouseAdapter() {
    private FilePane.Entry locate(MouseEvent e) {
      int index = e.getY() / entryHeight;
      if( index >= currentView.length )
        return null;
      else
        return currentView[index];
    }

    @Override
    public void mousePressed(MouseEvent e) {
      logic.window.anyUserInputYet = true;
      FilePane.Entry entry = locate(e);
      switch( e.getButton() ) {
      case 1:
        // we have only click actions in this pane, so we
        // can get faster UI response by reacting to button-
        // _presses_ instead of complete clicks.
        if( entry != null )
          logic.mouseClicked(entry, e.getModifiersEx(), e.getX() < iconWidth);
        break;
      }
    }
  };

  public void refreshScene(FilePane.Entry[] newView) {
    var oldView = currentView;
    currentView = newView;
    int minlen = Math.min(oldView.length, newView.length);
    int paintStart = minlen;
    int paintEnd = Math.max(oldView.length, newView.length);
    if( paintStart == paintEnd ) paintEnd = 0;
    for( int i=0; i<minlen; i++ ) {
      if( !oldView[i].equals(newView[i]) ) {
        if( i < paintStart ) paintStart = i;
        if( i >= paintEnd ) {
          paintEnd = i+1;
        } else {
          // We know there's stuff at the end to repaint too
          break;
        }
      }
    }
    if( paintEnd > paintStart ) {
      repaint(0, paintStart*entryHeight, getWidth(), paintEnd*entryHeight);
    }
    if( newView.length != oldView.length )
      computePreferredSize();
  }

  public void computePreferredSize() {
    setPreferredSize(new Dimension(PREFERRED_WIDTH,
        currentView.length*entryHeight));
    invalidate();
  }

  @Override
  public void paint(Graphics g0) {
    Graphics2D g = SwingUtils.startPaint(g0);
    FontMetrics fm = g.getFontMetrics();
    int idealHeight = fm.getHeight();
    if( idealHeight != entryHeight ) {
      entryHeight = idealHeight;
      computePreferredSize();
      iconWidth = entryHeight;
      repaint();
    }

    Rectangle bounds = new Rectangle();
    g.getClipBounds(bounds);
    int last = Math.min(currentView.length - 1,
        (bounds.y + bounds.height - 1 + OVERLAP) / entryHeight);
    for( int i = (bounds.y - OVERLAP) / entryHeight; i <= last; i++ ) {
      Graphics2D gg = (Graphics2D)g.create();
      gg.translate(0, i * entryHeight);
      paintEntry(gg, fm, currentView[i]);
    }
  }

  private void paintEntry(Graphics2D g, FontMetrics fm, FilePane.Entry entry) {
    int x = iconWidth + 3 + 10;
    int y = fm.getAscent();
    String suffix = "/";
    switch( entry.kind ) {
    case TRUNK_DIR:
      x = iconWidth + 3;
      break;
    case FILE:
      suffix = "";
      break;
    case BRANCH_DIR:
    case TIP_DIR:
      break;
    }

    if( (entry.displayFlags & FilePane.SELECTION_FLAG) != 0 ) {
      Color fg = g.getColor();
      Color trans = new Color(fg.getColorSpace(),
          fg.getColorComponents(null), 0.1f);
      g.setColor(trans);
      g.fillRect(0, 0, getWidth(), fm.getHeight());
      g.setColor(fg);
    }

    String text = entry.name + suffix;
    if( (entry.displayFlags & FilePane.PHANTOM_FILE_FLAG) != 0 )
      text = "("+text+")";
    if( (entry.displayFlags & FilePane.ERROR_FLAG) != 0 )
      g.setFont(g.getFont().deriveFont(
          Collections.singletonMap(TextAttribute.STRIKETHROUGH,
              TextAttribute.STRIKETHROUGH_ON)));
    if( (entry.displayFlags & FilePane.MODIFIED_FLAG) != 0 ) {
      g.drawString("*", x, y);
      x += fm.stringWidth("* ");
    }
    if( (entry.displayFlags & FilePane.WARP_FLAG) != 0 )
      g.setFont(italicFont);
    if( (entry.displayFlags & FilePane.ACTIVE_FLAG) != 0 )
      g.setFont(g.getFont().deriveFont(Font.BOLD));

    g.drawString(text, x, y);

    if( (entry.displayFlags & FilePane.SHOW_TRACK_FLAG) != 0 ) {
      int imgWidth = iconWidth - 4;
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      int imgHeight = eyeIcon.getHeight()*imgWidth/eyeIcon.getWidth();
      g.drawImage(eyeIcon, (iconWidth-imgWidth)/2,
          (entryHeight-imgHeight)/2,
          imgWidth, imgHeight, null);
    }
    if( (entry.displayFlags & FilePane.USE_BOUNDS_FLAG) != 0 ) {
      g.setColor(new Color(RGB.OTHER_BOUND));
      double diameter = 8;
      g.fill(new Ellipse2D.Double(
          (iconWidth-diameter)/2,
          (entryHeight-diameter)/2,
          diameter, diameter));
    }
  }

}
