package net.makholm.henning.mapwarper.tiles;

@SuppressWarnings("serial")
public class TryDownloadLater extends Exception {

  public TryDownloadLater(String msg) {
    super(msg);
  }

  public TryDownloadLater(Throwable cause) {
    super(cause);
  }

}
