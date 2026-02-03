package net.makholm.henning.mapwarper.georaster.geotiff;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.makholm.henning.mapwarper.util.NiceError;

public class Tiff {

  public static final short TYPE_BYTE = 1;
  public static final short TYPE_ASCII = 2;
  public static final short TYPE_SHORT = 3;
  public static final short TYPE_LONG = 4;
  public static final short TYPE_RATIONAL = 5;
  public static final short TYPE_SBYTE = 6;
  public static final short TYPE_UNDEFINED = 7;
  public static final short TYPE_SSHORT = 8;
  public static final short TYPE_SLONG = 9;
  public static final short TYPE_SRATIONAL = 10;
  public static final short TYPE_FLOAT = 11;
  public static final short TYPE_DOUBLE = 12;

  public static final short SUBFILE_TYPE = 0xFE;
  public static final int SUBFILE_TYPE_ALPHA = 1 << 2;

  public static final short IMAGE_WIDTH = 0x100;
  public static final short IMAGE_HEIGHT = 0x101;

  public static final short COMPRESSION = 0x103;
  public static final int COMPRESSION_JPEG = 7;

  public static final short PHOTOMETRIC = 0x106;
  public static final int PHOTOMETRIC_RGB = 2;

  public static final short PLANAR_CONFIG = 0x11C;
  public static final int PLANAR_CONFIG_INTERLEAVED = 1;

  public static final short TILE_WIDTH = 0x142;
  public static final short TILE_HEIGHT = 0x143;
  public static final short TILE_OFFSETS = 0x144;
  public static final short TILE_BYTE_COUNTS = 0x145;

  public static final short JPEG_TABLES = 0x15B;

  public final ByteBuffer buffer;
  public final int buflen;
  public final int ifdOffset;
  public final int ifdCount;

  public static Tiff create(ByteBuffer buffer) {
    if( buffer.limit() < 8 )
      return null;
    switch( buffer.getShort(0) ) {
    case 0x0101 * 'I':
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      break;
    case 0x0101 * 'M':
      buffer.order(ByteOrder.BIG_ENDIAN);
      break;
    default:
      throw NiceError.of("Bad initial TIFF magic %04x", buffer.getShort(0));
    }
    if( buffer.getShort(2) != 42 )
      throw NiceError.of("Bad secondary TIFF magic %04x", buffer.getShort(2));
    int ifdOffset = buffer.getInt(4);
    return new Tiff(buffer, ifdOffset).nullIfTruncated();
  }

  public boolean hasNext() {
    return nextIfd() != 0;
  }

  public Tiff next() {
    return new Tiff(buffer, nextIfd()).nullIfTruncated();
  }

  public int getCountForTag(short tag) {
    int offset = searchTagRecord(tag);
    if( offset < 0 )
      return -1;
    else
      return buffer.getInt(offset+4);
  }

  public void assertValue(short tag, long value) {
    Number got = getNumber(tag, null);
    if( got == null )
      throw NiceError.of("Missing tag %04x, expected %d", tag, value);
    else if( got.longValue() != value )
      throw NiceError.of("Tag %04x should be %d, but got %s", tag, value, got);
  }

  public void assertValueIfPresent(short tag, long value) {
    Number got = getNumber(tag, null);
    if( got != null && got.longValue() != value )
      throw NiceError.of("Tag %04x should be %d, but got %s", tag, value, got);
  }

  public boolean numberIs(short tag, long value) {
    Number got = getNumber(tag, null);
    return got != null && got.longValue() == value;
  }

  public Number getNumber(short tag, Number defval) {
    return getNumber(tag, 0, defval);
  }

  public Number getNumber(short tag, int index, Number defval) {
    int offset = searchTagRecord(tag);
    if( offset < 0 )
      return defval;
    if( index < 0 || index >= countAt(offset) )
      return defval;
    int valoffset = valueOffset(offset, index);
    switch( typeAt(offset) ) {
    case TYPE_BYTE:
      return Integer.valueOf(buffer.get(valoffset) & 0xFF);
    case TYPE_SHORT:
      return Integer.valueOf(buffer.getShort(valoffset) & 0xFFFF);
    case TYPE_LONG:
      int got = buffer.getInt(valoffset);
      return got < 0 ? Long.valueOf(got & U32_MASK) : Integer.valueOf(got);
    case TYPE_RATIONAL:
      return Double.valueOf(buffer.getInt(valoffset) & U32_MASK) /
          (buffer.getInt(valoffset+4) & U32_MASK);
    case TYPE_SSHORT:
      return Short.valueOf(buffer.getShort(valoffset));
    case TYPE_SLONG:
      return Integer.valueOf(buffer.getInt(valoffset));
    case TYPE_SRATIONAL:
      return Double.valueOf((double)buffer.getInt(valoffset) /
          buffer.getInt(valoffset+4));
    case TYPE_FLOAT:
      return Float.valueOf(buffer.getFloat(valoffset));
    case TYPE_DOUBLE:
      return Double.valueOf(buffer.getDouble(valoffset));
    case TYPE_ASCII:
    case TYPE_UNDEFINED:
    default:
      throw NiceError.of("unexpected value type %d for tag %04x",
          typeAt(offset), tag);
    }
  }

  public ByteBuffer getBytes(short tag) {
    int offset = searchTagRecord(tag);
    if( offset < 0 ) return null;
    int valoffset = valueOffset(offset, 0);
    return buffer.slice(valoffset, countAt(offset));
  }

  private static final long U32_MASK = (1L << 32) - 1;

  private Tiff(ByteBuffer buffer, int ifdOffset) {
    this.buffer = buffer;
    this.buflen = buffer.limit();
    this.ifdOffset = ifdOffset;
    if( ifdOffset > 0 && ifdOffset <= buflen - 6 )
      this.ifdCount = buffer.getChar(ifdOffset);
    else
      this.ifdCount = 0;
  }

  private Tiff nullIfTruncated() {
    if( ifdOffset < 0 ) return null;
    if( ifdOffset > buflen - 6 - 12*ifdCount ) return null;
    for( int i=0; i<ifdCount; i++ ) {
      var offset = ifdOffset + 2 + 12*i;
      int count = buffer.getInt(offset+4);
      if( count < 0 )
        throw NiceError.of("Negative number of entries for tag %04x",
            tagAt(offset));
      if( valueOffset(offset, count) >= buflen )
        return null;
    }
    return this;
  }

  private int nextIfd() {
    return buffer.getInt(ifdOffset + 2 + 12*ifdCount);
  }

  public int searchTagRecord(short tag) {
    int lookfor = tag & 0xFFFF;
    int atleast = 0;
    int toohigh = ifdCount;
    while( atleast < toohigh ) {
      int mid = (atleast+toohigh)/2;
      int midOffset = ifdOffset+2+12*mid;
      int got = buffer.getChar(midOffset);
      if( got == lookfor )
        return midOffset;
      else if( got > lookfor )
        toohigh = mid;
      else
        atleast = mid+1;
    }
    return -1;
  }

  private short tagAt(int recoffset) {
    return buffer.getShort(recoffset);
  }

  private short typeAt(int recoffset) {
    return buffer.getShort(recoffset+2);
  }

  private int countAt(int recoffset) {
    return buffer.getInt(recoffset+4) & Integer.MAX_VALUE;
  }

  private int valueOffset(int recoffset, int index) {
    int bpv = bytesPerValue(typeAt(recoffset));
    long length = (long)bpv * countAt(recoffset);
    if( length <= 4 )
      return recoffset + 8 + bpv*index;
    else {
      var result = (0xFFFFFFFFL & buffer.getInt(recoffset+8))
          + (long)bpv*index;
      if( result > Integer.MAX_VALUE )
        return Integer.MAX_VALUE;
      else
        return (int)result;
    }
  }

  public static int bytesPerValue(short type)  {
    switch( type ) {
    case TYPE_BYTE:
    case TYPE_ASCII:
    case TYPE_SBYTE:
    case TYPE_UNDEFINED:
      return 1;
    case TYPE_SHORT:
    case TYPE_SSHORT:
      return 2;
    case TYPE_LONG:
    case TYPE_SLONG:
    case TYPE_FLOAT:
      return 4;
    case TYPE_RATIONAL:
    case TYPE_SRATIONAL:
    case TYPE_DOUBLE:
      return 8;
    default:
      return 1;
    }
  }

}
