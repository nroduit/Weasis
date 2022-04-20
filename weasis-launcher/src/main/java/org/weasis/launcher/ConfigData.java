/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import static org.weasis.launcher.WeasisLauncher.CONFIG_DIRECTORY;
import static org.weasis.launcher.WeasisLauncher.CONFIG_PROPERTIES_FILE_VALUE;
import static org.weasis.launcher.WeasisLauncher.CONFIG_PROPERTIES_PROP;
import static org.weasis.launcher.WeasisLauncher.EXTENDED_PROPERTIES_FILE_VALUE;
import static org.weasis.launcher.WeasisLauncher.EXTENDED_PROPERTIES_PROP;
import static org.weasis.launcher.WeasisLauncher.P_HTTP_AUTHORIZATION;
import static org.weasis.launcher.WeasisLauncher.P_OS_NAME;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_CODEBASE_EXT_URL;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_CODEBASE_LOCAL;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_CODEBASE_URL;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_CONFIG_HASH;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_CONFIG_URL;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_NAME;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_PATH;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_PROFILE;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_SOURCE_ID;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_USER;
import static org.weasis.launcher.WeasisLauncher.P_WEASIS_VERSION;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.felix.framework.util.Util;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.weasis.launcher.WeasisLauncher.Type;

public class ConfigData {
  private static final Logger LOGGER = System.getLogger(ConfigData.class.getName());

  // Params, see
  // https://nroduit.github.io/en/getting-started/weasis-protocol/#modify-the-launch-parameters
  public static final String PARAM_CONFIG_URL = "wcfg"; // NON-NLS
  public static final String PARAM_ARGUMENT = "arg"; // NON-NLS
  public static final String PARAM_PROPERTY = "pro"; // NON-NLS
  public static final String PARAM_CODEBASE = "cdb"; // NON-NLS
  public static final String PARAM_CODEBASE_EXT = "cdb-ext"; // NON-NLS
  public static final String PARAM_AUTHORIZATION = "auth"; // NON-NLS

  private final List<String> arguments = new ArrayList<>();
  private final Properties properties = new Properties();

  private final StringBuilder configOutput = new StringBuilder();
  private final Map<String, String> felixProps = new HashMap<>();

  public ConfigData(String[] args) {
    init(args);
  }

  public void init(String[] args) {
    this.clear();
    LOGGER.log(Level.INFO, "Starting Weasis...");
    LOGGER.log(Level.INFO, "Initialization of the launch configuration...");

    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        LOGGER.log(Level.INFO, "Main arg {0} = {1}", Integer.toString(i), args[i]);
      }

      int index = Utils.getWeasisProtocolIndex(args);
      if (index < 0) {
        splitArgToCmd(args);
      } else {
        extractArgFromUri(args[index]);
        if (args.length > 1) {
          ArrayList<String> otherArgs = new ArrayList<>(args.length - 1);
          for (int i = 0; i < args.length; i++) {
            if (i != index) {
              otherArgs.add(args[i]);
            }
          }
          splitArgToCmd(otherArgs.toArray(new String[0]));
        }
      }
    }

    // Define the HttpUrlConnection userAgent (without Weasis Version) for the writeRequests() call
    // that gets remote configuration files
    System.setProperty(
        "http.agent",
        "Weasis"
            + " ("
            + System.getProperty(P_OS_NAME)
            + "; "
            + System.getProperty("os.version")
            + "; "
            + System.getProperty("os.arch")
            + "; "
            + System.getProperty("weasis.launch.type")
            + ")");

    // Add config and weasis properties from previously set Java System Properties
    // It avoids any given launch arguments from overloading them.
    applyJavaProperties();

    // Extract config and weasis properties from arguments, but has no effect on those already set
    applyConfigFromArguments();

    // Load all the felix properties
    Properties felixConfig = loadConfigProperties();

    // Set "application config" properties, but has no effect on those already set
    initWeasisProperties(felixConfig);
  }

  private void applyJavaProperties() {
    applyJavaProperty(CONFIG_PROPERTIES_PROP);
    applyJavaProperty(EXTENDED_PROPERTIES_PROP);
    for (String propertyName : System.getProperties().stringPropertyNames()) {
      if (propertyName.startsWith("weasis.")) { // NON-NLS
        applyJavaProperty(propertyName);
      }
    }
  }

  private void applyJavaProperty(String key) {
    addProperty(key, System.getProperty(key));
  }

  public Map<String, String> getFelixProps() {
    return felixProps;
  }

  private void initWeasisProperties(Properties felixConfig) {
    // Set system property for dynamically loading only native libraries corresponding of the
    // current platform
    setOsgiNativeLibSpecification();

    String profile = felixConfig.getProperty(P_WEASIS_PROFILE, "default");
    addProperty(P_WEASIS_PROFILE, profile);

    String name = felixConfig.getProperty(P_WEASIS_NAME, "Weasis");
    addProperty(P_WEASIS_NAME, name);

    String version = felixConfig.getProperty(P_WEASIS_VERSION, "0.0.0");
    addProperty(P_WEASIS_VERSION, version);

    String codebase = properties.getProperty(P_WEASIS_CODEBASE_URL);
    addProperty(P_WEASIS_SOURCE_ID, toHex((codebase + profile).hashCode()));

    String user = properties.getProperty(P_WEASIS_USER);
    if (!Utils.hasText(user)) {
      user = System.getProperty("user.name", "unknown"); // NON-NLS
      addProperty(P_WEASIS_USER, user);
      addProperty("weasis.pref.local.session", Boolean.TRUE.toString());
    }

    // Define the http user agent
    addProperty(
        "http.agent",
        name
            + "/"
            + version
            + " ("
            + System.getProperty(P_OS_NAME)
            + "; "
            + System.getProperty("os.version")
            + "; " // NON-NLS
            + System.getProperty("os.arch")
            + "; "
            + profile
            + "; "
            + System.getProperty("weasis.launch.type")
            + ")"); // NON-NLS

    String portable = properties.getProperty("weasis.portable.dir");
    if (portable != null) {
      LOGGER.log(Level.INFO, "Set default relative folders");
      String pkey = "weasis.portable.dicom.directory";
      addProperty(pkey, felixConfig.getProperty(pkey, "dicom,DICOM,IMAGES,images"));
    }

    // Set weasis properties to Java System Properties before variables substitution.
    applyConfigToSystemProperties();

    filterConfigProperties(felixConfig);
    if (LOGGER.isLoggable(Level.TRACE)) {
      felixProps.forEach(
          (k, v) ->
              LOGGER.log(
                  Level.TRACE, () -> String.format("Felix config: %s = %s", k, v))); // NON-NLS
    }

    File appFolder = new File(felixProps.get(Constants.FRAMEWORK_STORAGE)).getParentFile();
    appFolder.mkdirs();
    addProperty(P_WEASIS_PATH, appFolder.getPath());
    System.setProperty(P_WEASIS_PATH, appFolder.getPath());
    LOGGER.log(Level.INFO, "Properties: {0}", properties);
  }

  private void filterConfigProperties(Properties felixConfig) {
    // Only required for dev purposes (running the app in IDE)
    String mvnRepo =
        System.getProperty(
            "maven.localRepository", felixConfig.getProperty("maven.local.repo")); // NON-NLS
    if (mvnRepo != null) {
      System.setProperty("maven.localRepository", Utils.adaptPathToUri(mvnRepo));
    }

    // Perform variable substitution for system properties and convert to dictionary.

    for (Enumeration<?> e = felixConfig.propertyNames(); e.hasMoreElements(); ) {
      String name = (String) e.nextElement();
      felixProps.put(name, Util.substVars(felixConfig.getProperty(name), name, null, felixConfig));
    }
  }

  private void extractArgFromUri(String uri) {
    String url = URLDecoder.decode(uri, StandardCharsets.UTF_8);
    String[] cmds = url.split("\\$");
    boolean windows = System.getProperty(P_OS_NAME, "").toLowerCase().startsWith("win"); // NON-NLS
    if (cmds.length > 0) {
      for (int i = 1; i < cmds.length; i++) {
        // Fix Windows issue (add a trailing slash)
        if (windows && i == cmds.length - 1 && cmds[i].endsWith("/")) {
          cmds[i] = cmds[i].substring(0, cmds[i].length() - 1);
        }
        arguments.add(cmds[i]);
      }
    }
  }

  /**
   * First extract $weasis:config arguments from command launch. If serviceConfigUrl is given then
   * config parameters will be read from this URl. <br>
   * If none codebase/or weasis properties URLs are given from arguments, then default values will
   * be set.<br>
   * Local path is considered when codebaseUrl is not defined.
   *
   * <p>Any command launch argument takes priority over serviceConfig parameters. <br>
   */
  private void applyConfigFromArguments() {
    List<String> configArgs = extractWeasisConfigArguments();
    applyConfigParams(getConfigParamsFromArgs(configArgs));
    applyConfigParams(getConfigParamsFromServicePath());

    String codeBaseUrl = properties.getProperty(P_WEASIS_CODEBASE_URL, "");
    if (!Utils.hasText(codeBaseUrl)) {
      applyLocalCodebase();
    }

    if (!properties.containsKey(CONFIG_PROPERTIES_PROP) && Utils.hasText(codeBaseUrl)) {
      String configProp =
          String.format(
              "%s/%s/%s", codeBaseUrl, CONFIG_DIRECTORY, CONFIG_PROPERTIES_FILE_VALUE); // NON-NLS
      addProperty(CONFIG_PROPERTIES_PROP, configProp);
    }

    String codeBaseExtUrl = properties.getProperty(P_WEASIS_CODEBASE_EXT_URL, "");
    if (!properties.containsKey(EXTENDED_PROPERTIES_PROP) && Utils.hasText(codeBaseExtUrl)) {
      String extConfigProp =
          String.format(
              "%s/%s/%s", // NON-NLS
              codeBaseExtUrl, CONFIG_DIRECTORY, EXTENDED_PROPERTIES_FILE_VALUE);
      addProperty(EXTENDED_PROPERTIES_PROP, extConfigProp);
    }

    configOutput.append("\n  Application local codebase = "); // NON-NLS
    configOutput.append(properties.getProperty(P_WEASIS_CODEBASE_LOCAL));
    configOutput.append("\n  Application codebase URL = "); // NON-NLS
    configOutput.append(properties.getProperty(P_WEASIS_CODEBASE_URL));
  }

  private String applyLocalCodebase() {
    File localCodebase = findLocalCodebase();
    String baseURI = localCodebase.toURI().toString();
    if (baseURI.endsWith("/")) {
      baseURI = baseURI.substring(0, baseURI.length() - 1);
    }
    try {
      addProperty(P_WEASIS_CODEBASE_LOCAL, localCodebase.getAbsolutePath());
      addProperty(P_WEASIS_CODEBASE_URL, baseURI);
      baseURI += "/" + CONFIG_DIRECTORY + "/";
      addProperty(CONFIG_PROPERTIES_PROP, baseURI + CONFIG_PROPERTIES_FILE_VALUE);
      addProperty(EXTENDED_PROPERTIES_PROP, baseURI + EXTENDED_PROPERTIES_FILE_VALUE);

      // Allow export feature for local/portable version
      addProperty("weasis.export.dicom", Boolean.TRUE.toString());
      addProperty("weasis.export.dicom.send", Boolean.TRUE.toString());
      addProperty("weasis.import.dicom", Boolean.TRUE.toString());
      addProperty("weasis.import.dicom.qr", Boolean.TRUE.toString());
    } catch (Exception e) {
      LOGGER.log(Level.ERROR, "Apply Codebase", e);
    }
    return baseURI;
  }

  private void applyConfigParams(Map<String, List<String>> configParams) {
    if (configParams == null) {
      return;
    }

    configParams.forEach(
        (k, v) -> {
          switch (k) {
            case PARAM_CONFIG_URL -> addProperty(P_WEASIS_CONFIG_URL, v.get(0));
            case PARAM_CODEBASE -> addProperty(P_WEASIS_CODEBASE_URL, v.get(0));
            case PARAM_CODEBASE_EXT -> addProperty(P_WEASIS_CODEBASE_EXT_URL, v.get(0));
            case PARAM_AUTHORIZATION -> addProperty(P_HTTP_AUTHORIZATION, v.get(0));
            case PARAM_PROPERTY -> addProperties(v);
            case PARAM_ARGUMENT -> addArguments(v);
          }
        });
  }

  public List<String> getArguments() {
    return arguments;
  }

  private void addArgument(String arg) {
    if (arg != null) {
      arguments.add(arg);
    }
  }

  private void addArguments(Collection<String> args) {
    if (args != null) {
      arguments.addAll(args);
    }
  }

  public Properties getProperties() {
    return properties;
  }

  private void addProperty(String key, String val) {
    if (Utils.hasText(key) && Utils.hasText(val)) {
      properties.putIfAbsent(key, val);
    }
  }

  private void addProperties(Collection<String> properties) {
    Pattern pattern = Pattern.compile("\\s+");

    properties.forEach(
        value -> {
          String[] result = pattern.split(value, 2);
          if (result.length == 2) {
            addProperty(result[0], result[1]);
          } else {
            LOGGER.log(Level.WARNING, "Cannot parse property: {0}", value);
          }
        });
  }

  public StringBuilder getConfigOutput() {
    return configOutput;
  }

  public boolean isPropertyValueSimilar(String prop, String value) {
    String p = properties.getProperty(Objects.requireNonNull(prop));
    return Objects.equals(p, value);
  }

  public String getSourceID() {
    return properties.getProperty(P_WEASIS_SOURCE_ID);
  }

  private void clear() {
    arguments.clear();
    properties.clear();
  }

  private void applyConfigToSystemProperties() {
    for (String key : properties.stringPropertyNames()) {
      System.setProperty(key, properties.getProperty(key));
    }
  }

  public void applyProxy(String dir) {
    File file = new File(dir, "persistence.properties");
    if (!file.canRead()) {
      // Fix old name
      File oldFile = new File(dir, "persitence.properties");
      if (!oldFile.canRead() || !oldFile.renameTo(file)) {
        return;
      }
    }
    Properties p = new Properties();
    FileUtil.readProperties(file, p);

    boolean mproxy = Utils.getEmptytoFalse(p.getProperty("proxy.manual"));

    if (mproxy) {
      String exceptions = p.getProperty("proxy.exceptions");
      String val = p.getProperty("proxy.http.host");
      applyProxyProperty("http.proxyHost", val, mproxy);
      if (Utils.hasText(val)) {
        applyProxyProperty("http.proxyPort", p.getProperty("proxy.http.port"), mproxy); // NON-NLS
        applyProxyProperty("http.nonProxyHosts", exceptions, mproxy);
      }

      val = p.getProperty("proxy.https.host");
      applyProxyProperty("https.proxyHost", val, mproxy);
      if (Utils.hasText(val)) {
        applyProxyProperty("https.proxyPort", p.getProperty("proxy.https.port"), mproxy); // NON-NLS
        applyProxyProperty("http.nonProxyHosts", exceptions, mproxy);
      }

      val = p.getProperty("proxy.ftp.host");
      applyProxyProperty("ftp.proxyHost", val, mproxy);
      if (Utils.hasText(val)) {
        applyProxyProperty("ftp.proxyPort", p.getProperty("proxy.ftp.port"), mproxy); // NON-NLS
        applyProxyProperty("ftp.nonProxyHosts", exceptions, mproxy);
      }

      val = p.getProperty("proxy.socks.host");
      applyProxyProperty("socksProxyHost", val, mproxy);
      if (Utils.hasText(val)) {
        applyProxyProperty("socksProxyPort", p.getProperty("proxy.socks.port"), mproxy); // NON-NLS
      }

      boolean auth = Utils.getEmptytoFalse(p.getProperty("proxy.auth"));
      if (auth) {
        String authUser = p.getProperty("proxy.auth.user");
        String authPassword;
        try {
          byte[] pwd = Utils.getByteArrayProperty(p, "proxy.auth.pwd", null);
          if (pwd != null) {
            pwd = Utils.decrypt(pwd, "proxy.auth");
            if (pwd != null && pwd.length > 0) {
              authPassword = new String(pwd, StandardCharsets.UTF_8);
              applyPasswordAuthentication(authUser, authPassword);
              applyProxyProperty("http.proxyUser", authUser, mproxy);
              applyProxyProperty("http.proxyPassword", authPassword, mproxy);
            }
          }
        } catch (Exception e) {
          LOGGER.log(Level.ERROR, "Cannot store the proxy password", e);
        }
      }
    }
  }

  private static void applyPasswordAuthentication(
      final String authUser, final String authPassword) {
    Authenticator.setDefault(
        new Authenticator() {
          @Override
          public PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(authUser, authPassword.toCharArray());
          }
        });
  }

  private static void applyProxyProperty(String key, String value, boolean manual) {
    if (manual && Utils.hasText(value)) {
      System.setProperty(key, value);
    }
  }

  private void splitArgToCmd(String... args) {
    boolean files = true;
    int length = args.length;
    for (int i = 0; i < length; i++) {
      if (args[i].startsWith("$") && args[i].length() > 1) {
        files = false;
        StringBuilder command = new StringBuilder(args[i].substring(1));
        // look for parameters
        while (i + 1 < length && !args[i + 1].startsWith("$")) {
          i++;
          command.append(' ');
          command.append(args[i]);
        }
        arguments.add(command.toString());
      }
    }

    if (files) {
      for (String arg : args) {
        String val = arg;
        // DICOM files
        if (val.startsWith("file:")) { // NON-NLS
          try {
            val = new File(new URI(arg)).getPath();
          } catch (URISyntaxException e) {
            LOGGER.log(Level.ERROR, "Convert URI to file", e);
          }
        }
        arguments.add("dicom:get -l \"" + val + "\""); // NON-NLS
      }
    }
  }

  private List<String> extractWeasisConfigArguments() {
    String configCmd = "weasis:config"; // NON-NLS
    for (String cmd : arguments) {
      if (cmd.startsWith(configCmd) && cmd.length() > configCmd.length() + 2) {
        arguments.remove(cmd);
        return Utils.splitSpaceExceptInQuotes(cmd.substring(configCmd.length() + 1));
      }
    }
    return Collections.emptyList();
  }

  private Map<String, List<String>> getConfigParamsFromArgs(List<String> configArguments) {
    Map<String, List<String>> configParams = new HashMap<>();
    configArguments.forEach(a -> addConfigParam(configParams, a));
    return configParams;
  }

  private void addConfigParam(Map<String, List<String>> configParams, String argument) {
    if (argument == null) {
      return;
    }

    String[] vals = argument.split("=", 2);
    if (vals.length != 2) {
      return;
    }

    addConfigParam(configParams, vals[0], vals[1]);
  }

  private void addConfigParam(Map<String, List<String>> configParams, String name, String value) {
    if (!Utils.hasText(name) || value == null) {
      return;
    }

    List<String> paramList = configParams.computeIfAbsent(name, p -> new LinkedList<>());
    paramList.add(Utils.removeEnglobingQuotes(value));
  }

  private Map<String, List<String>> getConfigParamsFromServicePath() {

    String configServicePath = properties.getProperty(P_WEASIS_CONFIG_URL);
    if (!Utils.hasText(configServicePath)) {
      return null;
    }

    InputStream stream = null;
    try {
      URI configServiceUri = new URI(configServicePath);

      if (configServiceUri.getScheme().startsWith("file")) {
        stream = new FileInputStream(new File(configServiceUri));
      } else {
        URLConnection urlConnection =
            FileUtil.getAdaptedConnection(new URI(configServicePath).toURL(), false);

        urlConnection.setRequestProperty("Accept", "application/xml"); // NON-NLS
        urlConnection.setConnectTimeout(
            Integer.parseInt(System.getProperty("UrlConnectionTimeout", "1000"))); // NON-NLS
        urlConnection.setReadTimeout(
            Integer.parseInt((System.getProperty("UrlReadTimeout", "2000")))); // NON-NLS

        if (urlConnection instanceof HttpURLConnection httpURLConnection) {
          if (httpURLConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException(httpURLConnection.getResponseMessage());
            // TODO ## redirection stream is not handled
            // @see weasis.core.api.util.NetworkUtil.applyRedirectionStream()
          }
        }
        stream = urlConnection.getInputStream();
      }

      XMLStreamReader xmler = null;
      try {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        xmler = factory.createXMLStreamReader(stream);
        return readServiceConfigStream(xmler);
      } finally {
        FileUtil.safeClose(xmler);
      }

    } catch (Exception e) {
      LOGGER.log(
          Level.ERROR,
          () -> String.format("Error Loading config service %s", configServicePath), // NON-NLS
          e);
    } finally {
      FileUtil.safeClose(stream);
    }

    return null;
  }

  private Map<String, List<String>> readServiceConfigStream(XMLStreamReader xmler)
      throws XMLStreamException {

    Map<String, List<String>> configParams = new HashMap<>();

    while (xmler.hasNext()) {
      if (xmler.next() == XMLStreamConstants.START_ELEMENT) {
        String localName = xmler.getLocalName();
        if ("property".equals(localName)) { // NON-NLS
          String name = xmler.getAttributeValue(null, "name"); // NON-NLS
          String value = xmler.getAttributeValue(null, "value"); // NON-NLS
          addConfigParam(
              configParams, PARAM_PROPERTY, String.format("%s %s", name, value)); // NON-NLS
        } else if ("argument".equals(localName)) { // NON-NLS
          addConfigParam(configParams, PARAM_ARGUMENT, xmler.getElementText());
        }
      }
    }
    return configParams;
  }

  /**
   * Reads application config files and compute WEASIS_CONFIG_HASH to check if those had been
   * updated.
   */
  public Properties loadConfigProperties() {
    URI propURI = getPropertiesURI(CONFIG_PROPERTIES_PROP, CONFIG_PROPERTIES_FILE_VALUE);
    Properties felixConfig = new Properties();
    // Read the properties file
    if (propURI != null) {
      configOutput.append("\n  Application configuration file = "); // NON-NLS
      configOutput.append(propURI);
      WeasisLauncher.readProperties(propURI, felixConfig);

    } else {
      LOGGER.log(Level.ERROR, "No config.properties path found, Weasis cannot start!");
    }

    propURI = getPropertiesURI(EXTENDED_PROPERTIES_PROP, EXTENDED_PROPERTIES_FILE_VALUE);
    if (propURI != null) {
      configOutput.append("\n  Application extension configuration file = "); // NON-NLS
      configOutput.append(propURI);
      // Extended properties, add or override existing properties
      WeasisLauncher.readProperties(propURI, felixConfig);
    }
    if (Type.NATIVE.name().equals(System.getProperty("weasis.launch.type"))) {
      checkMinimalVersion(felixConfig);
    }

    if (felixConfig.isEmpty()) {
      throw new IllegalStateException("Cannot load weasis config!");
    }

    // Build a hash the properties just after reading. It will allow comparing with a new app
    // instance.
    properties.put(P_WEASIS_CONFIG_HASH, String.valueOf(felixConfig.hashCode()));

    return felixConfig;
  }

  private void checkMinimalVersion(Properties felixConfig) {
    String val = felixConfig.getProperty(WeasisLauncher.P_WEASIS_MIN_NATIVE_VERSION);
    if (Utils.hasText(val) && getProperty(P_WEASIS_CODEBASE_LOCAL) == null) {
      try {
        URI propURI = getLocalPropertiesURI(CONFIG_PROPERTIES_PROP, CONFIG_PROPERTIES_FILE_VALUE);
        Properties localProps = new Properties();
        WeasisLauncher.readProperties(propURI, localProps);
        Version loc =
            new Version(
                localProps.getProperty(WeasisLauncher.P_WEASIS_VERSION).replaceFirst("-", "."));
        Version min = new Version(val.replaceFirst("-", "."));
        if (loc.compareTo(min) < 0) {
          LOGGER.log(
              Level.WARNING,
              "Start only with the native plug-ins because the version ({}) is lower the minimal version ({}) required remotely",
              loc,
              min);
          felixConfig.clear();
          felixConfig.putAll(localProps);
          propURI = getLocalPropertiesURI(EXTENDED_PROPERTIES_PROP, EXTENDED_PROPERTIES_FILE_VALUE);
          WeasisLauncher.readProperties(propURI, felixConfig);
          System.setProperty(WeasisLauncher.P_WEASIS_MIN_NATIVE_VERSION, val);
        }
      } catch (Exception e) {
        LOGGER.log(Level.ERROR, "Cannot check compatibility with remote package", e);
      }
    }
  }

  public URI getPropertiesURI(String configProp, String configFile) {
    // See if the property URL was specified as a property.
    URI propURL;
    String custom = properties.getProperty(configProp);
    if (Utils.hasText(custom)) {
      try {
        if (custom.startsWith("file:conf/")) { // NON-NLS
          propURL = new File(findLocalCodebase(), custom.substring(5)).toURI();
        } else {
          propURL = new URI(custom);
        }
      } catch (URISyntaxException e) {
        LOGGER.log(Level.ERROR, configProp, e);
        return null;
      }
    } else {
      propURL = getLocalPropertiesURI(configProp, configFile);
    }
    return propURL;
  }

  private URI getLocalPropertiesURI(String configProp, String configFile) {
    File confDir = new File(findLocalCodebase(), CONFIG_DIRECTORY);
    try {
      return new File(confDir, configFile).toURI();
    } catch (Exception ex) {
      LOGGER.log(Level.ERROR, configFile, ex);
      return null;
    }
  }

  private static String toHex(int val) {
    final char[] hexDigit = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };
    char[] ch8 = new char[8];
    for (int i = 8; --i >= 0; val >>= 4) {
      ch8[i] = hexDigit[val & 0xf];
    }
    return String.valueOf(ch8);
  }

  static File findLocalCodebase() {
    // Determine where the configuration directory is by figuring
    // out where weasis-launcher.jar is located on the system class path.
    String jarLocation = null;
    String classpath = System.getProperty("java.class.path");
    String[] vals = classpath.split(File.pathSeparator);
    for (String cp : vals) {
      if (cp.endsWith("weasis-launcher.jar")) { // NON-NLS
        jarLocation = cp;
      }
    }
    if (jarLocation == null) {
      return new File(
              ConfigData.class.getProtectionDomain().getCodeSource().getLocation().getPath())
          .getParentFile();
    } else {
      return new File(new File(jarLocation).getAbsolutePath()).getParentFile();
    }
  }

  public String getProperty(String key) {
    return properties.getProperty(key);
  }

  public String getProperty(String key, String defaultValue) {
    return properties.getProperty(key, defaultValue);
  }

  public static void setOsgiNativeLibSpecification() {
    // Follows the OSGI specification to use Bundle-NativeCode in the bundle fragment :
    // http://www.osgi.org/Specifications/Reference
    String osName = System.getProperty(P_OS_NAME);
    String osArch = System.getProperty("os.arch");
    if (Utils.hasText(osName) && Utils.hasText(osArch)) {
      if (osName.toLowerCase().startsWith("win")) {
        // All Windows versions with a specific processor architecture (x86 or x86-64) are grouped
        // under
        // windows. If you need to make different native libraries for the Windows versions, define
        // it in the
        // Bundle-NativeCode tag of the bundle fragment.
        osName = "windows"; // NON-NLS
      } else if (osName.equals(WeasisLauncher.MAC_OS_X)) {
        osName = "macosx"; // NON-NLS
      } else if (osName.equals("SymbianOS")) {
        osName = "epoc32"; // NON-NLS
      } else if (osName.equals("hp-ux")) {
        osName = "hpux"; // NON-NLS
      } else if (osName.equals("Mac OS")) { // NON-NLS
        osName = "macos"; // NON-NLS
      } else if (osName.equals("OS/2")) {
        osName = "os2"; // NON-NLS
      } else if (osName.equals("procnto")) {
        osName = "qnx"; // NON-NLS
      } else {
        osName = osName.toLowerCase();
      }

      if (osArch.equals("pentium")
          || osArch.equals("i386")
          || osArch.equals("i486")
          || osArch.equals("i586")
          || osArch.equals("i686")) {
        osArch = "x86"; // NON-NLS
      } else if (osArch.equals("amd64") || osArch.equals("em64t") || osArch.equals("x86_64")) {
        osArch = "x86-64"; // NON-NLS
      } else if (osArch.equals("arm")) {
        osArch = "armv7a"; // NON-NLS
      } else if (osArch.equals("power ppc")) {
        osArch = "powerpc"; // NON-NLS
      } else if (osArch.equals("psc1k")) {
        osArch = "ignite"; // NON-NLS
      } else {
        osArch = osArch.toLowerCase();
      }
      System.setProperty(WeasisLauncher.P_NATIVE_LIB_SPEC, osName + "-" + osArch);
    }
  }
}
