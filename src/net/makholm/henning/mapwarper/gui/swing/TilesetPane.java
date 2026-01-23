package net.makholm.henning.mapwarper.gui.swing;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;
import javax.swing.event.MouseInputAdapter;

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
    OneTileset(GuiMain window, Tileset tiles, TilesetPane parent) {
      setBorder(new EmptyBorder(5,5,5,0));

      Font font = SwingUtils.getANiceDefaultFont();
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

      JLabel label = new JLabel(tiles.desc);
      label.setFont(font);
      add(label);

      font = font.deriveFont(font.getSize()*0.75f);
      for( String annotation : tiles.getCopyrightBlurb() ) {
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
  }

  private static void stretchToFillHorizontally(JComponent comp) {
    comp.setMaximumSize(new Dimension(Integer.MAX_VALUE,
        comp.getMinimumSize().height));
  }

}
