package net.makholm.henning.mapwarper.gui.maprender;

public abstract class BasicRenderer extends SimpleRenderer {

  private final long sourceChain;

  public BasicRenderer(LayerSpec spec, double xscale, double yscale,
      RenderTarget target, long sourceChain) {
    super(spec, xscale, yscale, target);
    this.sourceChain = sourceChain;
  }

  @Override
  protected boolean renderColumn(int col, double xmid,
      int ymin, int ymax, double ybase) {
    return renderWithoutSupersampling(col, xmid, ymin, ymax, ybase,
        sourceChain, 0);
  }

  @Override
  public long nominalFallbackChain() {
    return sourceChain;
  }

}
