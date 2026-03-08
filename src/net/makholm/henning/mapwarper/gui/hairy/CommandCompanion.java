package net.makholm.henning.mapwarper.gui.hairy;

public interface CommandCompanion {

  public boolean mouseHeld(int modifiers);
  public boolean shiftHeld(int modifiers);
  public boolean ctrlHeld(int modifiers);
  public boolean altHeld(int modifiers);
  public boolean isQuickBitSet(int modifiers);

  public int setQuickBit(int modifiers);
  public int setAltBit(int modifiers);

}
