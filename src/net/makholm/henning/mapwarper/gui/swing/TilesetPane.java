package net.makholm.henning.mapwarper.gui.swing;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;
import javax.swing.event.MouseInputAdapter;

import net.makholm.henning.mapwarper.gui.LensTool;
import net.makholm.henning.mapwarper.tiles.TileContext;
import net.makholm.henning.mapwarper.tiles.Tileset;

@SuppressWarnings("serial")
public class TilesetPane extends JPanel {

  private final Map<String, JComponent> componentMap = new LinkedHashMap<>();

  TilesetPane(GuiMain window, TileContext context) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setMinimumSize(new Dimension(0,0));

    for( Tileset tiles : context.tilesets.values() )
      componentMap.put(tiles.name, new OneTileset(window, tiles, this));
  }

  private static class OneTileset extends JPanel {
    final Tileset tiles;
    final GuiMain window;
    OneTileset(GuiMain window, Tileset tiles, TilesetPane parent) {
      this.tiles = tiles;
      this.window = window;
      setBorder(new EmptyBorder(5,5,5,0));

      Font font = SwingUtils.getANiceDefaultFont();
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

      JLabel label = new JLabel(tiles.desc);
      label.setFont(font);
      add(label);

      font = font.deriveFont(font.getSize()*0.75f);
      for( String annotation : tiles.blurb ) {
        label = new JLabel(annotation);
        label.setFont(font);
        add(label);
      }

      stretchToFillHorizontally(this);
      parent.add(this);

      JSeparator separator = new JSeparator();
      stretchToFillHorizontally(separator);
      parent.add(separator);

      addMouseListener(new MouseInputAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          window.anyUserInputYet = true;
          switch(e.getButton()) {
          case 1:
            window.mainLogic.defaultTilesetClickAction(tiles);
            window.mainLogic.swing.refreshScene();
            break;
          case 3:
            PopupMenu popup = new PopupMenu();
            JLabel heading = new JLabel(tiles.desc, JLabel.CENTER);
            stretchToFillHorizontally(heading);
            popup.add(heading);
            popup.addSeparator();
            window.commands.defineTilesetMenu(tiles, popup);
            popup.show(OneTileset.this, e.getX(), e.getY());
            window.mainLogic.swing.refreshScene();
            break;
          }
        }
      });
    }
    @Override
    public void paint(Graphics g0) {
      super.paint(g0);
      if( window.mainLogic == null ) return;
      var icons = new ArrayList<BufferedImage>(2);
      if( window.mainLogic.warpTiles == tiles )
        window.warpIcon.ifPresent(icons::add);
      if( window.mainLogic.mapTiles == tiles )
        window.mapIcon.ifPresent(icons::add);
      if( window.mainLogic.lensTiles == tiles &&
          (window.mainLogic.currentTool instanceof LensTool ||
              window.mainLogic.lensRect != null) )
        window.lensIcon.ifPresent(icons::add);
      if( !icons.isEmpty() ) {
        var g = SwingUtils.startPaint(g0);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int margin = 4;
        int size = Math.min(31, getHeight()-2*margin);
        int x = getWidth()-margin-size;
        int y = getHeight()-margin-size;
        for( var img : icons ) {
          g.drawImage(img, x, y, size, size, null);
          x -= margin+size;
        }
      }
    }
  }

  private static void stretchToFillHorizontally(JComponent comp) {
    comp.setMaximumSize(new Dimension(Integer.MAX_VALUE,
        comp.getMinimumSize().height));
  }

}
