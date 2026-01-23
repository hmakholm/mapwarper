package net.makholm.henning.mapwarper.gui.swing;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;

import javax.imageio.ImageIO;

import net.makholm.henning.mapwarper.gui.Commands;

public class SwingUtils {

  public static Graphics2D startPaint(Graphics g0) {
    Graphics2D g = (Graphics2D)g0;
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
    return g;
  }

  public static Font getANiceDefaultFont() {
    // Hard-coded font choice that works on my machine;
    // but is nicer than what Swing provides by default.
    // Eventually a better way of doing this should be found.
    return new Font("Roboto", Font.PLAIN, 15);

    // GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    // String[] names = ge.getAvailableFontFamilyNames();
  }

  public static Font getANiceItalicFont() {
    // This shouldn't be necessary but it is -- asking for an
    // italic version of Roboto inexplicably produces _bold_ italic for me.
    return new Font("Roboto Italic", Font.PLAIN, 15);
  }

  public static Optional<BufferedImage> loadBundledImage(String name) {
    try( var imgStream = Commands.class.getResourceAsStream(name) ) {
      if( imgStream == null ) {
        System.err.println("Cannot read "+name+" -- not found?");
        return Optional.empty();
      }
      return Optional.of(ImageIO.read(imgStream));
    } catch( IOException e ) {
      System.err.println("Cannot read "+name);
      e.printStackTrace();
      return Optional.empty();
    }
  }

  public static void beep() {
    Toolkit.getDefaultToolkit().beep();
  }

}
