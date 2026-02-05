package net.makholm.henning.mapwarper.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class XmlConfig {

  public boolean verbose;
  public String Appname, appname;
  public final Map<Path, Document> files = new LinkedHashMap<>();
  public final Map<String, Map<String, Element>> elements =
      new LinkedHashMap<>();

  public Map<String, Element> tagmap(String tag) {
    var got = elements.get(tag);
    return got == null ? Collections.emptyMap() : got;
  }

  public Element element(String tag, String name) {
    return tagmap(tag).get(name);
  }

  public String string(String tag, String name, String attr) {
    var elt = element(tag, name);
    if( elt == null ) return null;
    if( elt.hasAttribute(attr) ) return elt.getAttribute(attr);
    if( elt.hasAttribute("value") ) return elt.getAttribute("value");
    var s = elt.getTextContent().trim();
    if( !s.isEmpty() ) return s;
    return null;
  }

  public String string(String tag, String name) {
    return string(tag, name, "value");
  }

  public XmlConfig(boolean verbose, Class<?> mainClass) {
    this.verbose = verbose;
    readConfigFiles(mainClass);
  }

  public void override(String tag, String name, String value) {
    Element elt = freshElement(tag);
    elt.setAttribute("name", name);
    elt.setAttribute("value",  value);
    makeTagmap(tag).put(name, elt);
  }

  public static Element freshElement(String tag) {
    try {
      return DocumentBuilderFactory
          .newInstance()
          .newDocumentBuilder()
          .newDocument()
          .createElement(tag);
    } catch( ParserConfigurationException e) {
      e.printStackTrace();
      throw BadError.of("%s", e);
    }
  }

  public void setSystemProperties() {
    for( var prop : tagmap("systemProperty").keySet() ) {
      String val = string("systemProperty", prop);
      if( !prop.isEmpty() && val != null )
        System.setProperty(prop, val);
    }
  }

  private void readConfigFiles(Class<?> mainClass) {
    Appname = mainClass.getSimpleName();
    appname = Appname.toLowerCase(Locale.ROOT);

    String s = System.getProperty("user.home");
    Path home = s == null || s.isEmpty() ? null : Path.of(s);

    if( (s = System.getenv("XDG_CONFIG_HOME")) != null && !s.isEmpty() ) {
      attemptFile(Path.of(s), appname, "config.xml");
      // and try even the unixy standard location in this case
      attemptFile(home, ".config", appname, "config.xml");
    } else if( (s = System.getenv("LOCALAPPDATA")) != null && !s.isEmpty() ) {
      attemptFile(Path.of(s), Appname, "config.yml");
    } else {
      attemptFile(home, ".config", appname, "config.xml");
      attemptFile(home, "Library", "Preferences",
          mainClass.getPackageName(), "config.yml");
      attemptFile(home, "AppData", "Local", Appname, "config.xml");
    }

    var source = mainClass.getProtectionDomain().getCodeSource().getLocation();
    if( source.getProtocol().equals("file") ) {
      var spath = Path.of(source.getPath());
      if( Files.isDirectory(spath) ) {
        if( spath.getFileName().toString().equals("src") ||
            spath.getFileName().toString().equals("bin") ) {
          attemptSourceDir(spath.getParent());
        } else {
          attemptSourceDir(spath);
        }
      } else {
        if( Files.isRegularFile(spath) )
          attemptSourceDir(spath.getParent());
      }
    }
  }

  private void attemptSourceDir(Path p) {
    // local config file comes first
    attemptFile(p, "config", appname+"-local.xml");
    attemptFile(p, "config", appname+".xml");
  }

  private void attemptFile(Path p, String... components) {
    if( p == null ) return;
    for( var comp : components )
      p = p.resolve(comp);
    if( files.keySet().contains(p) )
      return;
    files.put(p, null);
    if( !Files.isRegularFile(p) ) {
      System.err.println("Ignoring non-existing "+p);
      return;
    }

    System.err.println("Using config file "+p);
    Document doc;
    try {
      var dbf = DocumentBuilderFactory.newInstance();
      dbf.setIgnoringElementContentWhitespace(true);
      var builder = dbf.newDocumentBuilder();
      doc = builder.parse(p.toFile());
      files.put(p, doc);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      System.out.println("Could not parse "+p+": "+e);
      return;
    }

    var nodes = doc.getDocumentElement().getChildNodes();
    for( int i=0; i<nodes.getLength(); i++ ) {
      var node = nodes.item(i);
      if( node instanceof Element elt ) {
        String name = elt.getAttribute("name");
        makeTagmap(elt.getTagName()).putIfAbsent(name, elt);
      }
    }
  }

  private Map<String, Element> makeTagmap(String tag) {
    return elements.computeIfAbsent(tag, t -> new LinkedHashMap<>());
  }

}
