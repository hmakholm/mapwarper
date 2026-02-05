package net.makholm.henning.mapwarper.georaster.geotiff;

import static net.makholm.henning.mapwarper.georaster.CompoundShortcode.maxisize;
import static net.makholm.henning.mapwarper.georaster.CompoundShortcode.minisPerMaxi;
import static net.makholm.henning.mapwarper.georaster.CompoundShortcode.minisize;
import static net.makholm.henning.mapwarper.georaster.CompoundShortcode.minix;
import static net.makholm.henning.mapwarper.georaster.CompoundShortcode.miniy;
import static net.makholm.henning.mapwarper.georaster.CompoundShortcode.tilex;
import static net.makholm.henning.mapwarper.georaster.CompoundShortcode.tiley;

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageInputStreamImpl;

import net.makholm.henning.mapwarper.georaster.CompoundShortcode;
import net.makholm.henning.mapwarper.georaster.TileBitmap;
import net.makholm.henning.mapwarper.util.MemoryMapped;
import net.makholm.henning.mapwarper.util.NiceError;

/**
 * This class can read compound minitiles from GeoTIFF files -- at least
 * if they are sufficiently like the ones I got from GeoDanmark to test
 * with. I make no claim to support the full generality of what the
 * format can do.
 */
public class CompoundDecoder {

  public static final int NO_SUCH_FILE = 0xFFEE8888;
  public static final int EMPTY_ZIP = 0xFFEECCCC;
  public static final int TRUNCATED = 0xFF0000BB;

  protected void validateToplevelIFD(Tiff tiff, long tilespec) {
    // This may be overridden in a subclass in order to verify that
    // GeoTIFF markers are as expected.
  }

  public TileBitmap decode(Path path, long tilespec)
      throws IOException {
    MappedByteBuffer buffer = null;
    try {
      try( FileChannel fc = FileChannel.open(path) ) {
        long length = fc.size();
        buffer = fc.map(MapMode.READ_ONLY, 0,
            Math.min(length, Integer.MAX_VALUE));
      } catch( NoSuchFileException e ) {
        return TileBitmap.blank(NO_SUCH_FILE);
      }
      return unzipAndDecode(buffer, tilespec);
    } finally {
      if( buffer != null )
        MemoryMapped.close(buffer);
    }
  }

  public TileBitmap unzipAndDecode(ByteBuffer buffer, long tilespec) {
    var unzipped = TrivialZip.unpack(buffer);
    if( unzipped == null )
      return TileBitmap.blank(EMPTY_ZIP);
    return decode(unzipped, tilespec);
  }

  public TileBitmap decode(ByteBuffer buffer, long tilespec) {
    Tiff tiff = Tiff.create(buffer);
    if( tiff == null )
      return TileBitmap.blank(TRUNCATED);
    validateToplevelIFD(tiff, tilespec);

    int maxisize = maxisize(tilespec);
    while(true) {
      if( tiff.numberIs(Tiff.IMAGE_WIDTH, maxisize) &&
          tiff.numberIs(Tiff.IMAGE_HEIGHT, maxisize) &&
          otherwiseGoodLayer(tiff) ) {
        return decodeRightSized(tiff, tilespec);
      } else if( !tiff.hasNext() ) {
        throw NiceError.of("No layer of matching size %d", maxisize);
      } else {
        tiff = tiff.next();
        if( tiff == null )
          return TileBitmap.blank(TRUNCATED);
      }
    }
  }

  private boolean otherwiseGoodLayer(Tiff tiff) {
    int subfileType = tiff.getNumber(Tiff.SUBFILE_TYPE, 0).intValue();
    if( (subfileType & Tiff.SUBFILE_TYPE_ALPHA) != 0 ) return false;
    return true;
  }

  public TileBitmap decodeRightSized(Tiff tiff, long tilespec) {
    int minisize = minisize(tilespec);
    tiff.assertValue(Tiff.TILE_WIDTH, minisize);
    tiff.assertValue(Tiff.TILE_HEIGHT, minisize);
    tiff.assertValue(Tiff.PHOTOMETRIC, Tiff.PHOTOMETRIC_RGB);
    tiff.assertValue(Tiff.COMPRESSION, Tiff.COMPRESSION_JPEG);
    tiff.assertValueIfPresent(Tiff.PLANAR_CONFIG,
        Tiff.PLANAR_CONFIG_INTERLEAVED);

    int minix = minix(tilespec);
    int miniy = miniy(tilespec);
    int minicount = minisPerMaxi(tilespec);
    int mininum = miniy*minicount + minix;

    int offset = tiff.getNumber(Tiff.TILE_OFFSETS, mininum, -1).intValue();
    int count = tiff.getNumber(Tiff.TILE_BYTE_COUNTS, mininum, -1).intValue();
    if( offset < 0 || count < 0 || offset+count < 0 )
      throw NiceError.of("Strange tile offset %d or count %d", offset, count);

    if( offset + count > tiff.buflen )
      return TileBitmap.blank(TRUNCATED);

    var tables = tiff.getBytes(Tiff.JPEG_TABLES);
    if( tables != null ) {
      if( tables.limit() > 2 )
        tables.limit(tables.limit()-2);
      if( count > 2 ) {
        count -= 2;
        offset += 2;
      }
    } else {
      tables = ByteBuffer.allocate(0);
    }
    var data = tiff.buffer.slice(offset, count);

    try {
      return decodeJpeg(new ConcatenatingInputStream(tables, data), tilespec);
    } catch( IOException e ) {
      throw NiceError.of("Tile decoding failed: "+e);
    }
  }

  private static final class ConcatenatingInputStream
  extends ImageInputStreamImpl {
    private final ByteBuffer buf1;
    private final ByteBuffer buf2;
    private final int len1;
    private final int totallen;

    ConcatenatingInputStream(ByteBuffer buf1, ByteBuffer buf2) {
      this.buf1 = buf1;
      this.buf2 = buf2;
      this.len1 = buf1.limit();
      this.totallen = len1+buf2.limit();
    }

    @Override
    public int read() throws IOException {
      bitOffset = 0;
      if( streamPos < len1 ) {
        return buf1.get((int)streamPos++) & 0xFF;
      } else if( streamPos < totallen ) {
        return buf2.get((int)streamPos++ - len1) & 0xFF;
      } else {
        return -1;
      }
    }

    @Override
    public int read(byte[] dest, int offset, int length) throws IOException {
      bitOffset = 0;
      if( streamPos < len1 ) {
        length = Math.min(length, len1-(int)streamPos);
        buf1.get((int)streamPos, dest, offset, length);
        streamPos += length;
        return length;
      } else if( streamPos < totallen ) {
        length = Math.min(length, totallen-(int)streamPos);
        buf2.get((int)streamPos - len1, dest, offset, length);
        streamPos += length;
        return length;
      } else {
        return -1;
      }
    }
  }

  public TileBitmap decodeJpeg(ImageInputStream istream, long tilespec)
      throws IOException {
    int minisize = minisize(tilespec);

    ImageReader reader = ImageIO.getImageReadersByMIMEType("image/jpeg").next();
    reader.setInput(istream, false, true);
    int width = reader.getWidth(0);
    int height = reader.getHeight(0);
    if( width != minisize || height != minisize )
      throw NiceError.of("Tile decodes to %dx%d, should be %dx%d",
          width, height, minisize, minisize);

    // We need to read as a Raster, because if we ask for a BufferedImage,
    // we get a completely misguided color conversion (the JPEG reader
    // seems to think the RGBNir data is some kind of YCbCr ...)
    Raster raster = reader.readRaster(0, null);
    if( raster.getNumDataElements() != 4 )
      throw NiceError.of("Got pixels with %d elements, expected 4",
          raster.getNumDataElements());

    DataBuffer buffer = raster.getDataBuffer();
    if( buffer instanceof DataBufferByte dbb ) {
      ByteBuffer bbuf = ByteBuffer.wrap(dbb.getData());
      bbuf.order(ByteOrder.BIG_ENDIAN);
      IntBuffer ibuf = bbuf.asIntBuffer();
      int[] pixdata = new int[ibuf.limit()];
      if( pixdata.length != minisize * minisize )
        throw NiceError.of("Got %d pixels, expected %d", pixdata.length,
            minisize*minisize);
      ibuf.get(pixdata);
      for( int i=0; i<pixdata.length; i++ )
        // pixdata[i] = Integer.reverseBytes(Integer.rotateLeft(pixdata[i], 16));
        pixdata[i] = (pixdata[i] >> 8) | 0xFF000000;
      return new TileBitmap(pixdata);
    } else {
      throw NiceError.of("Data buffer was a %s, not a DataBufferByte",
          buffer.getClass().getTypeName());
    }
  }

  // =====================================================================
  // Code to watch a download in progress and signal when each minitile
  // it can produce has been received.

  private static record Milestone(int bytes, long tilespec) { }

  public class DownloadSnooper implements Consumer<ByteBuffer> {
    private final long tilespec;
    private final LongConsumer callback;

    private static final int HEADLEN = 10_000;
    private int bytesSeen;
    private ByteBuffer headBuffer;
    private PriorityQueue<Milestone> milestones = new PriorityQueue<>(
        (a,b) -> Integer.compare(a.bytes(), b.bytes()));

    public DownloadSnooper(long tilespec, LongConsumer callback) {
      this.tilespec = tilespec;
      this.callback = callback;
    }

    @Override
    public void accept(ByteBuffer buffer) {
      justSnoop(buffer);
      flush();
    }

    public void justSnoop(ByteBuffer buffer) {
      int length = buffer.remaining();
      if( length == 0 ) return;

      if( bytesSeen == 0 ) {
        headBuffer = ByteBuffer.allocate(HEADLEN);
      } else if( headBuffer == null || bytesSeen >= HEADLEN ) {
        bytesSeen += length;
        return;
      }

      int toCopy = Math.min(length, HEADLEN-bytesSeen);
      headBuffer.put(bytesSeen, buffer, buffer.position(), toCopy);
      bytesSeen += length;
    }

    public void flush() {
      if( bytesSeen < HEADLEN ) return;
      if( headBuffer != null ) {
        headBuffer.position(0);
        headBuffer.limit(HEADLEN);
        analyzeHead(headBuffer);
        headBuffer = null;
      }
      while( !milestones.isEmpty() &&
          bytesSeen >= milestones.peek().bytes() )
        callback.accept(milestones.remove().tilespec());
    }

    private void analyzeHead(ByteBuffer buf) {
      int origLen = buf.capacity();
      buf = TrivialZip.unpack(buf);
      if( buf == null ) return;
      int zipHeader = buf.capacity()-origLen;
      Tiff tiff = Tiff.create(buf);
      if( tiff == null )
        throw NiceError.of("The TIFF is not head-heavy");
      int tilex = tilex(tilespec);
      int tiley = tiley(tilespec);
      for( int idtCount=0; ; tiff = tiff.next(), idtCount++ ) {
        if( tiff == null )
          throw NiceError.of("TIFF is truncated after "+idtCount+" images");
        int width = tiff.getNumber(Tiff.IMAGE_WIDTH, 0).intValue();
        int height = tiff.getNumber(Tiff.IMAGE_HEIGHT, 0).intValue();
        if( width == height && width > 0 && width < 65535 &&
            otherwiseGoodLayer(tiff) ) {
          int twidth = tiff.getNumber(Tiff.TILE_WIDTH, 0).intValue();
          int theight = tiff.getNumber(Tiff.TILE_HEIGHT, 0).intValue();
          int log2tsize = Integer.numberOfTrailingZeros(twidth);
          if( log2tsize >= 0 && log2tsize < 16 &&
              twidth == (1 << log2tsize) && theight == (1 << log2tsize) ) {
            int ntiles = (width+twidth-1)/twidth;
            var codemaker = new CompoundShortcode(width, twidth);
            for( int y=0; y<ntiles; y++ )
              for( int x=0; x<ntiles; x++ ) {
                int tidx = y*ntiles + x;
                int endOffset =
                    zipHeader +
                    tiff.getNumber(Tiff.TILE_OFFSETS, tidx, 0).intValue() +
                    tiff.getNumber(Tiff.TILE_BYTE_COUNTS, tidx, 0).intValue();
                long tspec = codemaker.makeShortcode(tilex, tiley, x, y);
                milestones.add(new Milestone(endOffset, tspec));
              }
          }
        }
        if( !tiff.hasNext() )
          return;
      }
    }
  }

  public class HttpDownloadSnooper<T> extends DownloadSnooper
  implements HttpResponse.BodySubscriber<T> {
    final HttpResponse.BodySubscriber<T> next;

    public HttpDownloadSnooper(HttpResponse.BodySubscriber<T> next,
        long tilespec, LongConsumer callback) {
      super(tilespec, callback);
      this.next = next;
    }

    @Override
    public void onNext(List<ByteBuffer> list) {
      list.forEach(this::justSnoop);
      next.onNext(list);
      flush();
    }

    @Override public void onSubscribe(Subscription s) { next.onSubscribe(s); }
    @Override public void onComplete() { next.onComplete(); }
    @Override public void onError(Throwable t) { next.onError(t); }
    @Override public CompletionStage<T> getBody() { return next.getBody(); }
  }

}
