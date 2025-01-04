package net.makholm.henning.mapwarper.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Regexer {

  public static final String wsp = "\\s*";
  public static final String nat = "\\d+";
  public static final String cNat = "("+nat+")";
  public static final String reUnsigned = nat + "(?:\\.\\d+)?";
  public static final String cUnsigned = "("+reUnsigned+")";
  public static final String cSigned = "(-?"+reUnsigned+")";

  public static final String optComma = "[,; ]" + wsp;

  public final String full;

  public Regexer(String baseString) {
    this.full = baseString;
  }

  public boolean is(String value) {
    return full.equals(value);
  }

  public boolean match(Pattern p) {
    matcher = p.matcher(full);
    return matcher.matches();
  }

  public boolean match(String re) {
    return match(Pattern.compile(re));
  }

  public boolean find(Pattern p) {
    matcher = p.matcher(full);
    return matcher.find();
  }

  public boolean find(String re) {
    return find(Pattern.compile(re));
  }

  private Matcher matcher;

  public String group(int index) {
    return matcher.group(index);
  }

  public int igroup(int index) {
    String s = group(index);
    if( s.startsWith("+") ) s = s.substring(1);
    return Integer.parseInt(s);
  }

  public double dgroup(int index) {
    return Double.parseDouble(group(index));
  }

  public boolean negatingGroup(int index) {
    return group(index) == null || group(index).isEmpty();
  }

}
