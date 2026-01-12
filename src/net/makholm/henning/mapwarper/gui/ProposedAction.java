package net.makholm.henning.mapwarper.gui;

import net.makholm.henning.mapwarper.gui.MouseAction.ToolResponse;

interface ProposedAction {

  default ProposedAction withPreview() {
    return this;
  }

  ToolResponse freeze();

  default boolean executeIfSelectingChain() {
    return false;
  }

}
