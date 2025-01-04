package net.makholm.henning.mapwarper.util;

import java.nio.file.Path;
import java.util.function.Function;

public final class FileUtil {

  public static Path rewriteFilename(Path p, Function<String, String> func) {
    return p.resolveSibling(func.apply(p.getFileName().toString()));
  }

}
