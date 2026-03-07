package net.makholm.henning.mapwarper.gui.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;

import org.w3c.dom.Element;

import net.makholm.henning.mapwarper.gui.Commands;
import net.makholm.henning.mapwarper.util.XmlConfig;

@SuppressWarnings("serial")
class Toolbar extends Box {

  private final GuiMain window;
  private final Map<String, MyButton> buttonsByName = new LinkedHashMap<>();

  static final int BARHEIGHT = 40;
  static final int MARGIN = 4;

  Toolbar(GuiMain window) {
    super(BoxLayout.X_AXIS);
    this.window = window;
    setPreferredSize(new Dimension(200, BARHEIGHT));
    setMinimumSize(new Dimension(BARHEIGHT, BARHEIGHT));
    setMaximumSize(new Dimension(3000, BARHEIGHT));
    makeTheButtons(window.commands, window.mainLogic.tiles.config);
  }

  private static final Pattern nextButton = Pattern.compile("\\s+(\\S+)(.*)",
      Pattern.DOTALL);

  void makeTheButtons(Commands commands, XmlConfig config) {
    add(new ArrowButton(this, commands.toggleFilePane, false));
    Element elt = config.element("toolbar",  "");
    if( elt == null ) {
      add(createGlue());
    } else {
      FontMetrics metrics = null;
      Font font = SwingUtils.getANiceDefaultFont();
      String s = elt.getTextContent();
      for(Matcher m; (m=nextButton.matcher(s)).matches(); s=m.group(2)) {
        String name = m.group(1);
        Command cmd = commands.commandRegistry.get(name);
        if( cmd != null ) {
          var img = SwingUtils.loadBundledImage(false, "icons/"+name+".png");
          if( img.isPresent() ) {
            add(new IconButton(this, cmd, img.get()));
          } else {
            if( metrics == null ) metrics = getFontMetrics(font);
            add(new TextButton(this, cmd, metrics));
          }
        } else if( name.equals("hamburger") ) {
          add(new Hamburger(BARHEIGHT, MARGIN, window));
        } else if( isRepeatedChar(name, '.') ) {
          add(createGlue());
        } else if( isRepeatedChar(name, '-') ) {
          add(new Separator());
        } else {
          System.err.println("Ignoring unknown '"+name+"' for toolbar");
        }
      }
    }
    add(new ArrowButton(this, commands.toggleTilesetPane, true));
  }

  private static boolean isRepeatedChar(String name, char c) {
    for( int i=0; i<name.length(); i++ )
      if( name.charAt(i) != c ) return false;
    return true;
  }

  void perhapsRepaint(Command command) {
    if( command != null ) {
      MyButton b = buttonsByName.get(command.codename);
      if( b != null ) b.perhapsRepaint();
    } else {
      for( MyButton b : buttonsByName.values() ) {
        if( b.command instanceof Tool ||
            b.command.getMenuSelected() != null )
          b.perhapsRepaint();
      }
    }
  }

  private static abstract class MyButton extends JComponent {
    final GuiMain window;
    final Command command;
    MyButton(Toolbar toolbar, Command command, Dimension dim) {
      this.window = toolbar.window;
      this.command = command;
      toolbar.buttonsByName.put(command.codename, this);
      setSize(dim);
      setMinimumSize(dim);
      setMaximumSize(dim);
      setPreferredSize(dim);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if( e.getButton() == 1 ) {
            command.invoke();
            perhapsRepaint();
            window.mainLogic.hairy.refreshScene();
          }
        }
      });
    }

    boolean stateToPaint;

    boolean currentState() {
      return Boolean.TRUE.equals(command.getMenuSelected());
    }

    final void invertIfActive(Graphics2D g) {
      if( stateToPaint ) {
        Color fg = g.getColor(), bg = g.getBackground();
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(bg);
        g.setBackground(fg);
      }
    }

    abstract void myPaint(Graphics2D g);

    void perhapsRepaint() {
      if( currentState() != stateToPaint )
        repaint();
    }

    @Override
    public final void paint(Graphics g0) {
      stateToPaint = currentState();
      myPaint(SwingUtils.startPaint(g0));
    }
  }

  private static final class TextButton extends MyButton {
    TextButton(Toolbar toolbar, Command command, FontMetrics metrics) {
      super(toolbar, command, new Dimension(
          2*MARGIN + metrics.stringWidth(command.codename), BARHEIGHT));
      setFont(metrics.getFont());
    }

    @Override
    void myPaint(Graphics2D g) {
      invertIfActive(g);
      var metrics = g.getFontMetrics();
      var x = (getWidth() - metrics.stringWidth(command.codename)) / 2;
      var y = (getHeight() + metrics.getAscent()) / 2;
      g.drawString(command.codename, x, y);
    }
  }

  private static final class IconButton extends MyButton {
    private final BufferedImage icon;
    private BufferedImage toDraw;
    private int toDrawColor = -1;

    IconButton(Toolbar toolbar, Command command, BufferedImage icon) {
      super(toolbar, command, new Dimension(
          2*MARGIN + (BARHEIGHT-2*MARGIN)*icon.getWidth()/icon.getHeight(),
          BARHEIGHT));
      this.icon = icon;
    }

    @Override
    void myPaint(Graphics2D g) {
      invertIfActive(g);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(getToDraw(g),
          MARGIN, MARGIN, getWidth()-2*MARGIN, getHeight()-2*MARGIN, null);
    }

    private BufferedImage getToDraw(Graphics2D g) {
      if( icon.getColorModel().hasAlpha() )
        return icon;
      int c = g.getColor().getRGB() & 0xFFFFFF;
      if( toDrawColor == c )
        return toDraw;

      int w = icon.getWidth();
      int h = icon.getHeight();
      if( toDraw == null )
        toDraw = g.getDeviceConfiguration().createCompatibleImage(
            w, h, Transparency.TRANSLUCENT);
      int[] pixels = new int[w*h];
      icon.getRGB(0, 0, w, h, pixels, 0,  w);
      for( int i=0; i<pixels.length; i++ )
        pixels[i] = (~pixels[i] << 24) | c;
      toDraw.setRGB(0, 0, w, h, pixels, 0, w);
      return toDraw;
    }
  }

  private static final class ArrowButton extends MyButton {
    private final boolean selectedMeansRight;

    ArrowButton(Toolbar toolbar, Command command, boolean selectedMeansRight) {
      super(toolbar, command, new Dimension(BARHEIGHT*2/3, BARHEIGHT));
      this.selectedMeansRight = selectedMeansRight;
    }

    @Override
    void myPaint(Graphics2D g) {
      g.setStroke(new BasicStroke(3,
          BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
      int x = getWidth() / 2;
      int d = x - MARGIN*2;
      if( stateToPaint != selectedMeansRight ) d = -d;
      Path2D.Double p = new Path2D.Double();
      p.moveTo(x-d, MARGIN);
      p.lineTo(x+d, getHeight()/2);
      p.lineTo(x-d, getHeight()-MARGIN);
      g.draw(p);
    }
  }

  private static final class Separator extends JComponent {
    Separator() {
      var dim = new Dimension(4*MARGIN+1, BARHEIGHT);
      setMinimumSize(new Dimension(2*MARGIN, BARHEIGHT));
      setPreferredSize(dim);
      setMaximumSize(dim);
    }

    @Override
    public void paint(Graphics g0) {
      g0.fillRect(getWidth()/2,  MARGIN, 1, getHeight()-2*MARGIN);
    }
  }

}
