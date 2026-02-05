package net.makholm.henning.mapwarper.georaster.geotiff;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import net.makholm.henning.mapwarper.util.NiceError;

public class TrivialZip {

  /**
   * Return a slice of the ByteBuffer containing the content of a
   * singleton non-compressed zip file. It tries to work even though
   * the zip file is truncated, and will then return a similarly
   * truncated slice -- it's up to the caller to handle that situation
   * gracefully.
   *
   * If the input is not recognizably a zip file, it will be returned
   * unchanged.
   *
   * If the input <em>is</em> a zip file, and we can see it contains no
   * files, return {@code null}.
   *
   * If the input <em>is</em> a zip file, but it contains more than one
   * file, or contains a compressed file, throw a {@link NiceError}.
   */
  public static ByteBuffer unpack(ByteBuffer input) {
    int length = input.limit();
    if( length < 20 ) return input;
    input.order(ByteOrder.LITTLE_ENDIAN);
    int magic = input.getInt(0);

    if( magic == 0x06054B50 ) {
      // special case for an empty zip file: it starts with the
      // end-of-central directory record
      if( input.getShort(10) == 0 &&
          length == 22 + input.getChar(20) ) {
        // This looks legit.
        return null;
      } else {
        // Hmm, not an empty zip; perhaps not a zip at all?
        return input;
      }
    }

    if( magic != 0x04034B50 ) {
      // Initial magic is not right, so don't think this is a zip file
      return input;
    }
    if( length < 30 + 46 + 20 ) {
      // There's not room for both a header and central directory, and
      // we don't bother to cater for truncations that harsh.
      return ByteBuffer.allocate(0);
    }
    if( input.getShort(8) != 0 ) {
      throw NiceError.of("The zip has nontrivial compression");
    }
    int headerlength = 30 + input.getChar(26) + input.getChar(28);

    completeZipAttempt: {
      int eocdOffset = length-22;
      if( eocdOffset < headerlength ) break completeZipAttempt;
      if( input.getInt(eocdOffset) != 0x06054B50 ) break completeZipAttempt;

      // This looks like an end-of-central-directory record at the right
      // place, but do some sanity checks to guard against random
      // happenstance with a truncated file.
      int numFiles = input.getShort(eocdOffset+10);
      if( numFiles < 0 ) break completeZipAttempt;
      int cdSize = input.getInt(eocdOffset+12);
      int cdOffset = input.getInt(eocdOffset+16);
      int commentLength = input.getShort(eocdOffset+20);
      if( commentLength != 0 ) break completeZipAttempt;
      if( cdSize < 0 ) break completeZipAttempt;
      if( cdOffset < 0 || cdOffset > length - cdSize ) break completeZipAttempt;
      int walker = cdOffset;
      for( int i=0; i<numFiles; i++ ) {
        if( walker >= eocdOffset ) break completeZipAttempt;
        if( input.getInt(walker) != 0x0201b450 ) break completeZipAttempt;
        int reclen = 46 +
            input.getChar(walker+28) +
            input.getChar(walker+30) +
            input.getChar(walker+32);
        walker += reclen;
      }
      if( walker != eocdOffset ) break completeZipAttempt;

      // At this point we're committed to trusting the central directory.

      if( numFiles != 1 )
        throw NiceError.of("The ZIP file contains %d files", numFiles);

      int localOffset = input.getInt(cdOffset+42);
      if( localOffset != 0 )
        throw NiceError.of("Local offset of the single file is %x, not 0",
            localOffset);
      int fileLength = input.getInt(cdOffset+24);
      if( fileLength < 0 )
        throw NiceError.of("Negative content length %x",
            fileLength);

      if( fileLength < cdOffset - headerlength )
        throw NiceError.of("Not enough space for the content file");

      return input.slice(headerlength, fileLength);
    }

    // We get here when we can't find the central directory.
    // The file is probably truncated. Very well.
    int contentlength = length - headerlength;
    if( contentlength <= 0 ) return ByteBuffer.allocate(0);

    int flags = input.getShort(6);
    if( (flags & 8) == 0 ) {
      contentlength = Math.min(contentlength, input.getInt(22));
      if( contentlength < 0 )
        throw NiceError.of("The file claims to have nevative length");
    }
    return input.slice(headerlength, contentlength);
  }

  public static void writeEmptyZip(Path dest) throws IOException {
    Files.createDirectories(dest.getParent());
    try( var fos = new FileOutputStream(dest.toFile());
        var zos = new ZipOutputStream(fos) ) {
      zos.finish();
    }
  }

}
