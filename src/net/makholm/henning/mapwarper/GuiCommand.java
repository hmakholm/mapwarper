package net.makholm.henning.mapwarper;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import net.makholm.henning.mapwarper.gui.swing.GuiMain;

public class GuiCommand extends Mapwarper.Command {

  GuiCommand(Mapwarper common) {
    super(common);
  }

  @Override
  protected void run(Deque<String> words) {
    List<String> args = new ArrayList<>(words);
    try {
      UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      SwingUtilities.invokeAndWait(() ->
      GuiMain.main(common.tileContext, args));
    } catch (Exception e) {
      if( e instanceof InvocationTargetException ite &&
          ite.getCause() instanceof Exception re )
        e = re;
      if( e instanceof RuntimeException re ) {
        throw re;
      } else {
        e.printStackTrace();
      }
    }
  }

}
