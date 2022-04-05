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

import com.formdev.flatlaf.FlatSystemProperties;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.awt.EventQueue;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import javax.management.ObjectName;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.apache.felix.framework.Felix;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.util.tracker.ServiceTracker;
import org.weasis.launcher.LookAndFeels.ReadableLookAndFeelInfo;

/**
 * @author Richard S. Hall
 * @author Nicolas Roduit
 */
public class WeasisLauncher {

  private static final Logger LOGGER = System.getLogger(WeasisLauncher.class.getName());

  public enum Type {
    DEFAULT,
    NATIVE
  }

  public enum State {
    UNINSTALLED(0x00000001),
    INSTALLED(0x00000002),
    RESOLVED(0x00000004),
    STARTING(0x00000008),
    STOPPING(0x00000010),
    ACTIVE(0x00000020);

    private final int index;

    State(int state) {
      this.index = state;
    }

    public static String valueOf(int state) {
      for (State s : State.values()) {
        if (s.index == state) {
          return s.name();
        }
      }
      return "UNKNOWN";
    }
  }

  /** Switch for specifying bundle directory. */
  public static final String BUNDLE_DIR_SWITCH = "-b"; // NON-NLS

  /** The property name used to specify whether the launcher should install a shutdown hook. */
  public static final String SHUTDOWN_HOOK_PROP = "felix.shutdown.hook";
  /**
   * The property name used to specify a URL to the configuration property file to be used for the
   * created the framework instance.
   */
  public static final String CONFIG_PROPERTIES_PROP = "felix.config.properties";
  /** The default name used for the configuration properties file. */
  public static final String CONFIG_PROPERTIES_FILE_VALUE = "config.properties";
  /** The property name used to specify a URL to the extended property file. */
  public static final String EXTENDED_PROPERTIES_PROP = "felix.extended.config.properties";
  /** The default name used for the extended properties file. */
  public static final String EXTENDED_PROPERTIES_FILE_VALUE = "ext-config.properties"; // NON-NLS
  /** Name of the configuration directory. */
  public static final String CONFIG_DIRECTORY = "conf";

  public static final String END_LINE = System.lineSeparator();
  public static final String APP_PROPERTY_FILE = "weasis.properties";
  public static final String P_WEASIS_VERSION = "weasis.version";
  public static final String P_WEASIS_PROFILE = "weasis.profile";
  public static final String P_WEASIS_NAME = "weasis.name";
  public static final String P_WEASIS_PATH = "weasis.path";
  public static final String P_WEASIS_RES_DATE = "weasis.resources.date";
  public static final String P_WEASIS_CODEBASE_LOCAL = "weasis.codebase.local";
  public static final String P_WEASIS_SOURCE_ID = "weasis.source.id";
  public static final String P_WEASIS_CODEBASE_URL = "weasis.codebase.url";
  public static final String P_WEASIS_CODEBASE_EXT_URL = "weasis.codebase.ext.url";
  public static final String P_WEASIS_CONFIG_HASH = "weasis.config.hash";
  public static final String P_WEASIS_PREFS_URL = "weasis.pref.url";
  public static final String P_WEASIS_CONFIG_URL = "weasis.config.url";
  public static final String P_WEASIS_USER = "weasis.user";
  public static final String P_WEASIS_SHOW_DISCLAIMER = "weasis.show.disclaimer";
  public static final String P_WEASIS_ACCEPT_DISCLAIMER = "weasis.accept.disclaimer";
  public static final String P_WEASIS_SHOW_RELEASE = "weasis.show.release";
  public static final String P_WEASIS_VERSION_RELEASE = "weasis.version.release";
  public static final String P_WEASIS_I18N = "weasis.i18n";
  public static final String P_OS_NAME = "os.name";
  public static final String P_WEASIS_LOOK = "weasis.theme";
  public static final String P_GOSH_ARGS = "gosh.args";
  public static final String P_WEASIS_CLEAN_CACHE = "weasis.clean.cache";
  public static final String P_HTTP_AUTHORIZATION = "http.authorization";
  public static final String P_NATIVE_LIB_SPEC = "native.library.spec";
  public static final String P_WEASIS_MIN_NATIVE_VERSION = "weasis.min.native.version";
  public static final String P_WEASIS_RESOURCES_URL = "weasis.resources.url";
  public static final String F_RESOURCES = "resources"; // NON-NLS
  static final String MAC_OS_X = "Mac OS X"; // NON-NLS

  protected Felix mFelix = null;
  protected ServiceTracker mTracker = null;
  protected volatile boolean frameworkLoaded = false;

  protected String look = null;
  protected RemotePrefService remotePrefs;
  protected String localPrefsDir;

  protected final Properties modulesi18n;
  protected final ConfigData configData;

  public WeasisLauncher(ConfigData configData) {
    this.configData = Objects.requireNonNull(configData);
    this.modulesi18n = new Properties();
  }

  public final void launch(Type type) throws Exception {
    Map<String, String> serverProp = configData.getFelixProps();
    String cacheDir = serverProp.get(Constants.FRAMEWORK_STORAGE) + "-" + configData.getSourceID();
    // If there is a passed in bundle cache directory, then
    // that overwrites anything in the config file.
    serverProp.put(Constants.FRAMEWORK_STORAGE, cacheDir);

    // Load local properties and clean if necessary the previous version
    WeasisLoader loader = loadProperties(serverProp, configData.getConfigOutput());
    WeasisMainFrame mainFrame = loader.getMainFrame();

    String minVersion = System.getProperty(P_WEASIS_MIN_NATIVE_VERSION);
    if (Utils.hasText(minVersion)) {
      EventQueue.invokeAndWait(
          () -> {
            String appName = System.getProperty(P_WEASIS_NAME);
            int response =
                JOptionPane.showOptionDialog(
                    mainFrame.getRootPaneContainer() == null
                        ? null
                        : mainFrame.getRootPaneContainer().getContentPane(),
                    String.format(
                        Messages.getString("WeasisLauncher.update_min")
                            + "\n\n"
                            + Messages.getString("WeasisLauncher.continue_local"),
                        appName,
                        minVersion),
                    null,
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE,
                    null,
                    null,
                    null);

            if (response != 0) {
              LOGGER.log(Level.ERROR, "Do not continue the launch with the local version");
              System.exit(-1);
            }
          });
    }

    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownHook));
    registerAdditionalShutdownHook();

    displayStartingAciiIcon();

    int exitStatus = 0;
    try {

      String goshArgs = getGoshArgs(serverProp);
      // Now create an instance of the framework with our configuration properties.
      mFelix = new Felix(serverProp);
      // Initialize the framework, but don't start it yet.
      mFelix.init();

      // Use the system bundle context to process the auto-deploy
      // and auto-install/auto-start properties.
      loader.setFelix(serverProp, mFelix.getBundleContext(), modulesi18n);
      loader.writeLabel(
          String.format(
              Messages.getString("WeasisLauncher.starting"), System.getProperty(P_WEASIS_NAME)));
      mTracker =
          new ServiceTracker(
              mFelix.getBundleContext(), "org.apache.felix.service.command.CommandProcessor", null);
      mTracker.open();

      // Start the framework.
      mFelix.start();

      // End of splash screen
      loader.close();
      loader = null;

      String logActivation = serverProp.get("org.apache.sling.commons.log.file");
      if (Utils.hasText(logActivation)) {
        LOGGER.log(
            Level.INFO,
            "Logs has been delegated to the OSGI service and can be read in {0}",
            logActivation);
      }

      executeCommands(configData.getArguments(), goshArgs);

      checkBundleUI(serverProp);
      frameworkLoaded = true;

      showMessage(mainFrame, serverProp);

      // Wait for framework to stop to exit the VM.
      mFelix.waitForStop(0);
      System.exit(0);
    } catch (Throwable ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      exitStatus = -1;
      LOGGER.log(Level.ERROR, "Cannot not start framework.", ex);
      LOGGER.log(Level.ERROR, "Weasis cache will be cleaned at next launch.");
      LOGGER.log(Level.ERROR, "State of the framework:");
      for (Bundle b : mFelix.getBundleContext().getBundles()) {
        LOGGER.log(
            Level.ERROR,
            " * "
                + b.getSymbolicName()
                + "-"
                + b.getVersion().toString()
                + " "
                + State.valueOf(b.getState()));
      }
      resetBundleCache();
    } finally {
      Runtime.getRuntime().halt(exitStatus);
    }
  }

  private void checkBundleUI(Map<String, String> serverProp) {
    String mainUI = serverProp.getOrDefault("weasis.main.ui", "").trim(); // NON-NLS
    if (Utils.hasText(mainUI)) {
      boolean uiStarted = false;
      for (Bundle b : mFelix.getBundleContext().getBundles()) {
        if (b.getSymbolicName().equals(mainUI) && b.getState() == Bundle.ACTIVE) {
          uiStarted = true;
          break;
        }
      }
      if (!uiStarted) {
        throw new IllegalStateException("Main User Interface bundle cannot be started");
      }
    }
  }

  private static String getGoshArgs(Map<String, String> serverProp) {
    String goshArgs = System.getProperty(P_GOSH_ARGS, serverProp.getOrDefault(P_GOSH_ARGS, ""));
    if (goshArgs.isEmpty()) {
      String val = System.getProperty("gosh.port", ""); // NON-NLS
      if (!val.isEmpty()) {
        try {
          goshArgs = String.format("-sc telnetd -p %d start", Integer.parseInt(val)); // NON-NLS
        } catch (NumberFormatException e) {
          // Do nothing
        }
      }
    }
    serverProp.put(P_GOSH_ARGS, "--nointeractive --noshutdown"); // NON-NLS
    return goshArgs;
  }

  private static void displayStartingAciiIcon() {
    StringBuilder buf = new StringBuilder();
    buf.append(END_LINE);
    buf.append("Starting OSGI Bundles..."); // NON-NLS
    buf.append(END_LINE);
    buf.append(END_LINE);
    buf.append("         | | /| / /__ ___ ____ (_)__");
    buf.append(END_LINE);
    buf.append("         | |/ |/ / -_) _ `(_-</ (_-<");
    buf.append(END_LINE);
    buf.append("         |__/|__/\\__/\\_,_/___/_/___/");
    buf.append(END_LINE);
    LOGGER.log(Level.INFO, buf::toString);
  }

  protected void executeCommands(List<String> commandList, String goshArgs) {
    SwingUtilities.invokeLater(
        () -> {
          mTracker.open();

          // Do not close streams. Workaround for stackoverflow issue when using System.in
          Object commandSession =
              getCommandSession(
                  mTracker.getService(),
                  new Object[] {
                    new FileInputStream(FileDescriptor.in),
                    new FileOutputStream(FileDescriptor.out),
                    new FileOutputStream(FileDescriptor.err)
                  });
          if (commandSession != null) {
            if (goshArgs == null) {
              // Set the main window visible and to the front
              commandSessionExecute(commandSession, "weasis:ui -v"); // NON-NLS
            } else {
              // Start telnet after all other bundles. This will ensure that all the plugins
              // commands are
              // activated once telnet is available
              initCommandSession(commandSession, goshArgs);
            }

            // execute the commands from main argv
            for (String command : commandList) {
              commandSessionExecute(commandSession, command);
            }
            commandSessionClose(commandSession);
          }

          mTracker.close();
        });
  }

  private static void resetBundleCache() {
    // Set flag to clean cache at next launch
    File sourceIdProps =
        new File(
            System.getProperty(P_WEASIS_PATH, ""),
            System.getProperty(P_WEASIS_SOURCE_ID) + ".properties"); // NON-NLS
    Properties localSourceProp = new Properties();
    FileUtil.readProperties(sourceIdProps, localSourceProp);
    localSourceProp.setProperty(P_WEASIS_CLEAN_CACHE, Boolean.TRUE.toString());
    FileUtil.storeProperties(sourceIdProps, localSourceProp, null);
  }

  private void showMessage(final WeasisMainFrame mainFrame, Map<String, String> serverProp) {
    String versionOld = serverProp.get("prev." + P_WEASIS_VERSION); // NON-NLS
    String versionNew = serverProp.getOrDefault(P_WEASIS_VERSION, "0.0.0");
    // First time launch
    if (versionOld == null) {
      String val = serverProp.get("prev." + P_WEASIS_SHOW_DISCLAIMER); // NON-NLS
      String accept = serverProp.get(P_WEASIS_ACCEPT_DISCLAIMER);
      if (Utils.geEmptytoTrue(val) && !Utils.getEmptytoFalse(accept)) {

        EventQueue.invokeLater(
            () -> {
              Object[] options = {
                Messages.getString("WeasisLauncher.ok"), Messages.getString("WeasisLauncher.no")
              };

              String appName = System.getProperty(P_WEASIS_NAME);
              int response =
                  JOptionPane.showOptionDialog(
                      mainFrame.getRootPaneContainer() == null
                          ? null
                          : mainFrame.getRootPaneContainer().getContentPane(),
                      String.format(Messages.getString("WeasisLauncher.msg"), appName),
                      String.format(Messages.getString("WeasisLauncher.first"), appName),
                      JOptionPane.YES_NO_OPTION,
                      JOptionPane.WARNING_MESSAGE,
                      null,
                      options,
                      null);

              if (response == 0) {
                // Write "false" in weasis.properties. It can be useful when preferences are store
                // remotely.
                // The user will accept the disclaimer only once.
                System.setProperty(P_WEASIS_ACCEPT_DISCLAIMER, Boolean.TRUE.toString());
              } else {
                File file =
                    new File(
                        System.getProperty(P_WEASIS_PATH, ""),
                        System.getProperty(P_WEASIS_SOURCE_ID) + ".properties");
                // delete the properties file to ask again
                FileUtil.delete(file);
                LOGGER.log(Level.ERROR, "Refusing the disclaimer");
                System.exit(-1);
              }
            });
      }
    } else if (versionNew != null && !versionNew.equals(versionOld)) {
      String val = serverProp.get("prev." + P_WEASIS_SHOW_RELEASE); // NON-NLS
      if (Utils.geEmptytoTrue(val)) {
        try {
          Version vOld = getVersion(versionOld);
          Version vNew = getVersion(versionNew);
          if (vNew.compareTo(vOld) > 0) {
            String lastTag = serverProp.get(P_WEASIS_VERSION_RELEASE);
            if (lastTag != null) {
              vOld = getVersion(lastTag);
              if (vNew.compareTo(vOld) <= 0) {
                // Message has been already displayed once.
                return;
              }
            }
            System.setProperty(P_WEASIS_VERSION_RELEASE, vNew.toString());
          }
        } catch (Exception e2) {
          LOGGER.log(Level.ERROR, "Cannot read version", e2);
          return;
        }
        final String releaseNotesUrl = serverProp.get("weasis.releasenotes"); // NON-NLS
        final StringBuilder message = new StringBuilder("<P>"); // NON-NLS
        message.append(
            String.format(
                Messages.getString("WeasisLauncher.change.version"),
                System.getProperty(P_WEASIS_NAME),
                versionOld,
                versionNew));

        EventQueue.invokeLater(
            () -> {
              JTextPane jTextPane1 = new JTextPane();
              HTMLEditorKit kit = new HTMLEditorKit();
              StyleSheet ss = kit.getStyleSheet();
              ss.addRule(
                  "body {font-family:sans-serif;font-size:12pt;background-color:#"
                      + Integer.toHexString(
                              (jTextPane1.getBackground().getRGB() & 0xffffff) | 0x1000000)
                          .substring(1)
                      + ";color:#"
                      + Integer.toHexString(
                              (jTextPane1.getForeground().getRGB() & 0xffffff) | 0x1000000)
                          .substring(1)
                      + ";margin:3;font-weight:normal;}");
              jTextPane1.setContentType("text/html");
              jTextPane1.setEditable(false);
              jTextPane1.addHyperlinkListener(
                  e -> {
                    JTextPane pane = (JTextPane) e.getSource();
                    if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
                      pane.setToolTipText(e.getDescription());
                    } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
                      pane.setToolTipText(null);
                    } else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                      if (System.getProperty(P_OS_NAME, "unknown") // NON-NLS
                          .toLowerCase()
                          .startsWith("linux")) { // NON-NLS
                        try {
                          String cmd = String.format("xdg-open %s", e.getURL()); // NON-NLS
                          Runtime.getRuntime().exec(cmd);
                        } catch (IOException e1) {
                          LOGGER.log(Level.ERROR, "Unable to launch the WEB browser");
                        }
                      } else if (Desktop.isDesktopSupported()) {
                        final Desktop desktop = Desktop.getDesktop();
                        if (desktop.isSupported(Desktop.Action.BROWSE)) {
                          try {
                            desktop.browse(e.getURL().toURI());

                          } catch (Exception ex) {
                            LOGGER.log(Level.ERROR, "Unable to launch the WEB browser");
                          }
                        }
                      }
                    }
                  });

              message.append("<BR>");
              String rn = Messages.getString("WeasisLauncher.release"); // NON-NLS
              message.append(
                  String.format(
                      "<a href=\"%s", // NON-NLS
                      releaseNotesUrl));
              message.append("\" style=\"color:#FF9900\">"); // NON-NLS
              message.append(rn);
              message.append("</a>"); // NON-NLS
              message.append("</P>"); // NON-NLS
              jTextPane1.setText(message.toString());
              JOptionPane.showMessageDialog(
                  mainFrame.getRootPaneContainer() == null
                      ? null
                      : mainFrame.getRootPaneContainer().getContentPane(),
                  jTextPane1,
                  Messages.getString("WeasisLauncher.News"),
                  JOptionPane.PLAIN_MESSAGE);
            });
      }
    }
  }

  private static Version getVersion(String version) {
    String v = "";
    if (version != null) {
      int index = version.indexOf('-');
      v = index > 0 ? version.substring(0, index) : version;
    }
    return new Version(v);
  }

  public static Object getCommandSession(Object commandProcessor, Object[] arguments) {
    if (commandProcessor == null) {
      return null;
    }
    Class<?>[] parameterTypes =
        new Class[] {InputStream.class, OutputStream.class, OutputStream.class};
    try {
      Method nameMethod = commandProcessor.getClass().getMethod("createSession", parameterTypes);
      Object commandSession = nameMethod.invoke(commandProcessor, arguments);
      addCommandSessionListener(commandProcessor);
      return commandSession;
    } catch (Exception ex) {
      // Since the services returned by the tracker could become
      // invalid at any moment, we will catch all exceptions, log
      // a message, and then ignore faulty services.
      LOGGER.log(Level.ERROR, "Create a command session", ex);
    }

    return null;
  }

  private static void addCommandSessionListener(Object commandProcessor) {
    try {
      ClassLoader loader = commandProcessor.getClass().getClassLoader();
      Class<?> c = loader.loadClass("org.apache.felix.service.command.CommandSessionListener");
      Method nameMethod = commandProcessor.getClass().getMethod("addListener", c);

      Object listener =
          Proxy.newProxyInstance(
              loader,
              new Class[] {c},
              (proxy, method, args) -> {
                String listenerMethod = method.getName();

                if (listenerMethod.equals("beforeExecute")) {
                  String arg = args[1].toString();
                  if (arg.startsWith("gosh") || arg.startsWith("gogo:gosh")) { // NON-NLS
                    // Force gogo to not use Expander to concatenate parameter with the current
                    // directory (Otherwise "*(|<[?" are interpreted, issue with URI parameters)
                    commandSessionExecute(args[0], "gogo.option.noglob=on"); // NON-NLS
                  }
                } else if (listenerMethod.equals("equals")) { // NON-NLS
                  // Only add once in the set of listeners
                  return proxy.getClass().isAssignableFrom((args[0].getClass()));
                }
                return null;
              });
      nameMethod.invoke(commandProcessor, listener);
    } catch (Exception e) {
      LOGGER.log(Level.ERROR, "Add command session listener", e);
    }
  }

  public static boolean initCommandSession(Object commandSession, String args) {
    try {
      // wait for gosh command to be registered
      for (int i = 0;
          (i < 100) && commandSessionGet(commandSession, "gogo:gosh") == null; // NON-NLS
          ++i) {
        TimeUnit.MILLISECONDS.sleep(10);
      }

      Class<?>[] parameterTypes = new Class[] {CharSequence.class};
      Object[] arguments =
          new Object[] {"gogo:gosh --login " + (args == null ? "" : args)}; // NON-NLS
      Method nameMethod = commandSession.getClass().getMethod("execute", parameterTypes);
      nameMethod.invoke(commandSession, arguments);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      LOGGER.log(Level.ERROR, "Init command session", e);
    }
    return false;
  }

  public static Object commandSessionGet(Object commandSession, String key) {
    if (commandSession == null || key == null) {
      return null;
    }

    Class<?>[] parameterTypes = new Class[] {String.class};
    Object[] arguments = new Object[] {key};

    try {
      Method nameMethod = commandSession.getClass().getMethod("get", parameterTypes);
      return nameMethod.invoke(commandSession, arguments);
    } catch (Exception ex) {
      LOGGER.log(Level.ERROR, "Invoke a command", ex);
    }

    return null;
  }

  public static boolean commandSessionClose(Object commandSession) {
    if (commandSession == null) {
      return false;
    }
    try {
      Method nameMethod = commandSession.getClass().getMethod("close");
      nameMethod.invoke(commandSession);
      return true;
    } catch (Exception ex) {
      LOGGER.log(Level.ERROR, "Close command session", ex);
    }

    return false;
  }

  public static Object commandSessionExecute(Object commandSession, CharSequence charSequence) {
    if (commandSession == null) {
      return false;
    }
    Class<?>[] parameterTypes = new Class[] {CharSequence.class};

    Object[] arguments = new Object[] {charSequence};

    try {
      Method nameMethod = commandSession.getClass().getMethod("execute", parameterTypes);
      return nameMethod.invoke(commandSession, arguments);
    } catch (Exception ex) {
      LOGGER.log(Level.ERROR, "Execute command", ex);
    }

    return null;
  }

  /** This following part has been copied from the Main class of the Felix project */
  public static void readProperties(URI propURI, Properties props) {
    try (InputStream is = FileUtil.getAdaptedConnection(propURI.toURL(), false).getInputStream()) {
      props.load(is);
    } catch (Exception ex) {
      LOGGER.log(
          Level.ERROR,
          () -> String.format("Cannot read properties file: %s", propURI), // NON-NLS
          ex);
    }
  }

  private static String getGeneralProperty(
      String key,
      String defaultValue,
      Map<String, String> serverProp,
      Properties localProp,
      boolean storeInLocalPref,
      boolean serviceProperty) {
    String value = localProp.getProperty(key, null);
    String defaultVal = System.getProperty(key, null);
    if (defaultVal == null) {
      defaultVal = serverProp.getOrDefault(key, defaultValue);
    }

    if (value == null) {
      value = defaultVal;
      if (storeInLocalPref && value != null) {
        // When first launch, set property that can be written later
        localProp.setProperty(key, value);
      }
    }
    if (serviceProperty) {
      serverProp.put(key, value);
      serverProp.put("def." + key, defaultVal); // NON-NLS
    }
    LOGGER.log(Level.INFO, "Config of {0} = {1}", key, value);
    return value;
  }

  public WeasisLoader loadProperties(Map<String, String> serverProp, StringBuilder conf) {
    String dir = configData.getProperty(P_WEASIS_PATH);
    String profileName = configData.getProperty(P_WEASIS_PROFILE, "default"); // NON-NLS
    String user = configData.getProperty(P_WEASIS_USER);

    // If proxy configuration, activate it
    configData.applyProxy(
        dir + File.separator + "data" + File.separator + "weasis-core-ui"); // NON-NLS

    StringBuilder bufDir = new StringBuilder(dir);
    bufDir.append(File.separator);
    bufDir.append("preferences"); // NON-NLS
    bufDir.append(File.separator);
    bufDir.append(user);
    bufDir.append(File.separator);
    bufDir.append(profileName);
    File prefDir = new File(bufDir.toString());
    try {
      prefDir.mkdirs();
    } catch (Exception e) {
      prefDir = new File(dir);
      LOGGER.log(Level.ERROR, "Cannot create preferences folders", e);
    }
    localPrefsDir = prefDir.getPath();
    serverProp.put("weasis.pref.dir", prefDir.getPath());

    Properties currentProps = new Properties();
    FileUtil.readProperties(new File(prefDir, APP_PROPERTY_FILE), currentProps);
    currentProps
        .stringPropertyNames()
        .forEach(key -> serverProp.put("wp.init." + key, currentProps.getProperty(key))); // NON-NLS

    String remotePrefURL = configData.getProperty(WeasisLauncher.P_WEASIS_PREFS_URL);
    if (Utils.hasText(remotePrefURL)) {
      String storeLocalSession = "weasis.pref.store.local.session";
      String defaultVal = configData.getProperty(storeLocalSession, null);
      if (defaultVal == null) {
        defaultVal = serverProp.getOrDefault(storeLocalSession, Boolean.FALSE.toString());
      }
      serverProp.put(storeLocalSession, defaultVal);
      try {
        remotePrefs = new RemotePrefService(remotePrefURL, serverProp, user, profileName);
        Properties remote = remotePrefs.readLauncherPref(null);
        currentProps.putAll(remote); // merge remote to local
        if (remote.size() < currentProps.size()) {
          // Force to have difference for saving preferences
          serverProp.put("wp.init.diff.remote.pref", Boolean.TRUE.toString()); // NON-NLS
        }
      } catch (Exception e) {
        String msg = String.format("Cannot read Launcher preference for user: %s", user); // NON-NLS
        LOGGER.log(Level.ERROR, () -> msg, e);
      }
    }

    // General Preferences priority order:
    // 1) Last value (does not exist for first launch of Weasis in an operating system session).
    // 2) Java System property
    // 3) Property defined in config.properties or in ext-config.properties
    // 4) default value
    final String lang =
        getGeneralProperty(
            "locale.lang.code", "en", serverProp, currentProps, true, false); // NON-NLS
    getGeneralProperty(
        "locale.format.code", "system", serverProp, currentProps, true, false); // NON-NLS

    // Set value back to the bundle context properties, sling logger uses
    // bundleContext.getProperty(prop)
    getGeneralProperty(
        "org.apache.sling.commons.log.level", "INFO", serverProp, currentProps, true, true);
    // Empty string make the file log writer disable
    String logActivation =
        getGeneralProperty(
            "org.apache.sling.commons.log.file.activate",
            Boolean.FALSE.toString(),
            serverProp,
            currentProps,
            true,
            true);
    if (Utils.getEmptytoFalse(logActivation)) {
      String logFile = dir + File.separator + "log" + File.separator + "default.log"; // NON-NLS
      serverProp.put("org.apache.sling.commons.log.file", logFile);
      currentProps.remove("org.apache.sling.commons.log.file");
    }

    getGeneralProperty(
        "org.apache.sling.commons.log.file.number", "5", serverProp, currentProps, true, true);
    getGeneralProperty(
        "org.apache.sling.commons.log.file.size",
        "10MB", // NON-NLS
        serverProp,
        currentProps,
        true,
        true);
    getGeneralProperty(
        "org.apache.sling.commons.log.stack.limit", "3", serverProp, currentProps, true, true);
    getGeneralProperty(
        "org.apache.sling.commons.log.pattern",
        "{0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4}* [{2}] {3}: {5}", // NON-NLS
        serverProp,
        currentProps,
        false,
        true);

    loadI18nModules();

    Locale locale = textToLocale(lang);
    if (Locale.ENGLISH.equals(locale)) {
      // if English no need to load i18n bundle fragments
      modulesi18n.clear();
    } else {
      String suffix = locale.toString();
      SwingResources.loadResources("/swing/basic_" + suffix + ".properties"); // NON-NLS
      SwingResources.loadResources("/swing/synth_" + suffix + ".properties"); // NON-NLS
    }

    String nativeLook;
    String sysSpec = System.getProperty(P_NATIVE_LIB_SPEC, "unknown"); // NON-NLS
    int index = sysSpec.indexOf('-');
    if (index > 0) {
      nativeLook = "weasis.theme." + sysSpec.substring(0, index); // NON-NLS
      look = System.getProperty(nativeLook, null);
      if (look == null) {
        look = serverProp.get(nativeLook);
      }
    }
    if (look == null) {
      look = System.getProperty(P_WEASIS_LOOK, null);
      if (look == null) {
        look = serverProp.get(P_WEASIS_LOOK);
      }
    }
    String localLook = currentProps.getProperty(P_WEASIS_LOOK, null);
    // If look is in local preferences, use it
    if (localLook != null) {
      look = localLook;
    }
    final LookAndFeels lookAndFeels = new LookAndFeels();
    final ReadableLookAndFeelInfo lookAndFeelInfo =
        lookAndFeels.getAvailableLookAndFeel(look, profileName);

    // See https://github.com/JFormDesigner/FlatLaf/issues/482
    if (SystemInfo.isLinux) {
      String decoration =
          getGeneralProperty(
              "weasis.linux.windows.decoration",
              Boolean.FALSE.toString(),
              serverProp,
              currentProps,
              true,
              true);
      if (Utils.getEmptytoFalse(decoration)) {
        // enable custom window decorations
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
      }
    } else if (SystemInfo.isMacOS) {
      // Enable screen menu bar - MUST BE initialized before UI components
      System.setProperty("apple.laf.useScreenMenuBar", "true");
      System.setProperty("apple.awt.application.name", System.getProperty(P_WEASIS_NAME));
      System.setProperty(
          "apple.awt.application.appearance",
          lookAndFeelInfo.isDark() ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
    }

    // JVM Locale
    Locale.setDefault(locale);
    // LookAndFeel Locale
    UIManager.getDefaults().setDefaultLocale(locale);
    // For new components
    JComponent.setDefaultLocale(locale);

    UIManager.setInstalledLookAndFeels(
        lookAndFeels.getLookAndFeels().toArray(new LookAndFeelInfo[0]));

    final String scaleFactor =
        getGeneralProperty(
            FlatSystemProperties.UI_SCALE, null, serverProp, currentProps, true, false);
    if (scaleFactor != null) {
      System.setProperty(FlatSystemProperties.UI_SCALE, scaleFactor);
    }

    // Init after default properties for UI
    Desktop app = Desktop.getDesktop();
    if (app.isSupported(Action.APP_OPEN_URI)) {
      app.setOpenURIHandler(
          e -> {
            String uri = e.getURI().toString();
            LOGGER.log(Level.INFO, "Get URI event from OS. URI: {0}}", uri);
            executeCommands(List.of(uri), null);
          });
    }

    /*
     * Build a Frame
     *
     * This will ensure the popup message or other dialogs to have frame parent. When the parent is
     *  null the dialog can be hidden under the main frame
     */
    final WeasisMainFrame mainFrame = new WeasisMainFrame();

    try {
      SwingUtilities.invokeAndWait(
          () -> {
            // Set look and feels
            look = lookAndFeels.setLookAndFeel(lookAndFeelInfo);

            try {
              // Build a JFrame which will be used later in base.ui module
              ObjectName objectName2 = new ObjectName("weasis:name=MainWindow"); // NON-NLS
              mainFrame.setRootPaneContainer(new JFrame());
              ManagementFactory.getPlatformMBeanServer().registerMBean(mainFrame, objectName2);
            } catch (Exception e1) {
              LOGGER.log(Level.ERROR, "Cannot register the main frame", e1);
            }
          });
    } catch (Exception e) {
      LOGGER.log(Level.ERROR, "Unable to set the Look&Feel {0}", look);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    currentProps.put(P_WEASIS_LOOK, look);

    File sourceIDProps =
        new File(dir, configData.getProperty(P_WEASIS_SOURCE_ID) + ".properties"); // NON-NLS
    Properties localSourceProp = new Properties();
    FileUtil.readProperties(sourceIDProps, localSourceProp);

    final String versionOld = localSourceProp.getProperty(P_WEASIS_VERSION);
    if (Utils.hasText(versionOld)) {
      serverProp.put("prev." + P_WEASIS_VERSION, versionOld); // NON-NLS
    }
    final String versionNew = serverProp.getOrDefault(P_WEASIS_VERSION, "0.0.0"); // NON-NLS
    String cleanCacheAfterCrash = localSourceProp.getProperty(P_WEASIS_CLEAN_CACHE);

    boolean update = false;
    // Loads the resource files
    String defaultResources = "/resources.zip"; // NON-NLS
    boolean mavenRepo = Utils.hasText(System.getProperty("maven.localRepository", null));
    String resPath = configData.getProperty(P_WEASIS_RESOURCES_URL, null);
    if (!Utils.hasText(resPath)) {
      resPath = serverProp.getOrDefault(P_WEASIS_RESOURCES_URL, null);
      if (!mavenRepo && !Utils.hasText(resPath)) {
        String cdb = configData.getProperty(P_WEASIS_CODEBASE_URL, null);
        // Don't try to guess remote URL from pure local distribution
        if (Utils.hasText(cdb) && !cdb.startsWith("file:")) { // NON-NLS
          resPath = cdb + defaultResources;
        }
      }
    }
    File cacheDir = null;
    try {
      if (isZipResource(resPath)) {
        cacheDir =
            new File(
                dir
                    + File.separator
                    + "data"
                    + File.separator
                    + System.getProperty(P_WEASIS_SOURCE_ID),
                F_RESOURCES);
        String date =
            FileUtil.writeResources(
                resPath, cacheDir, localSourceProp.getProperty(P_WEASIS_RES_DATE));
        if (date != null) {
          update = true;
          localSourceProp.put(P_WEASIS_RES_DATE, date);
        }
      }
    } catch (Exception e) {
      cacheDir = null;
      LOGGER.log(Level.ERROR, "Loads the resource folder", e);
    }

    if (cacheDir == null) {
      if (mavenRepo) {
        // In Development mode
        File f = new File(System.getProperty("user.dir"));
        cacheDir = new File(f.getParent(), "weasis-distributions" + File.separator + F_RESOURCES);
      } else {
        String cdbl = configData.getProperty(P_WEASIS_CODEBASE_LOCAL);
        cacheDir = new File(cdbl, F_RESOURCES);
      }
    }
    serverProp.put("weasis.resources.path", cacheDir.getPath());

    // Splash screen that shows bundles loading
    final WeasisLoader loader = new WeasisLoader(cacheDir.toPath(), mainFrame);
    // Display splash screen
    loader.open();

    if (versionNew != null) {
      localSourceProp.put(P_WEASIS_VERSION, versionNew);
      if (versionOld == null || !versionOld.equals(versionNew)) {
        update = true;
      }
    }
    String showDisclaimer =
        getGeneralProperty(
            P_WEASIS_SHOW_DISCLAIMER,
            Boolean.TRUE.toString(),
            serverProp,
            currentProps,
            false,
            false);
    if (Utils.hasText(showDisclaimer)) {
      serverProp.put("prev." + P_WEASIS_SHOW_DISCLAIMER, showDisclaimer); // NON-NLS
    }
    String showRelease =
        getGeneralProperty(
            P_WEASIS_SHOW_RELEASE, Boolean.TRUE.toString(), serverProp, currentProps, false, false);
    if (Utils.hasText(showRelease)) {
      serverProp.put("prev." + P_WEASIS_SHOW_RELEASE, showRelease); // NON-NLS
    }

    // Clean cache if Weasis has crashed during the previous launch
    boolean cleanCache = Boolean.parseBoolean(serverProp.get("weasis.clean.previous.version"));
    if (Boolean.TRUE.toString().equals(cleanCacheAfterCrash)) {
      serverProp.put(
          Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
      localSourceProp.remove(P_WEASIS_CLEAN_CACHE);
      update = true;
      LOGGER.log(
          Level.INFO, "Clean plug-in cache because Weasis has crashed during the previous launch");
    }
    // Clean cache when version has changed
    else if (cleanCache && versionNew != null && !versionNew.equals(versionOld)) {
      LOGGER.log(Level.INFO, "Clean previous Weasis version: {0}", versionOld);
      serverProp.put(
          Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
      LOGGER.log(Level.INFO, "Clean plug-in cache because the version has changed");
    }

    if (update) {
      FileUtil.storeProperties(sourceIDProps, localSourceProp, null);
    }

    // Transmit weasis.properties
    Set<String> pKeys = currentProps.stringPropertyNames();
    serverProp.put("wp.list", String.join(",", pKeys)); // NON-NLS
    pKeys.forEach(key -> serverProp.put(key, currentProps.getProperty(key)));

    String pevConf = conf.toString();
    conf.setLength(0);
    conf.append("\n***** Configuration *****"); // NON-NLS
    conf.append("\n  Last running version = "); // NON-NLS
    conf.append(versionOld);
    conf.append("\n  Current version = "); // NON-NLS
    conf.append(versionNew);
    conf.append("\n  Application name = "); // NON-NLS
    conf.append(configData.getProperty(P_WEASIS_NAME));
    conf.append("\n  Application Source ID = "); // NON-NLS
    conf.append(System.getProperty(P_WEASIS_SOURCE_ID));
    conf.append("\n  Application Profile = "); // NON-NLS
    conf.append(profileName);
    conf.append(pevConf);
    conf.append("\n  User = "); // NON-NLS
    conf.append(System.getProperty(P_WEASIS_USER, "user")); // NON-NLS
    conf.append("\n  User home directory = "); // NON-NLS
    conf.append(dir);
    conf.append("\n  Resources path = "); // NON-NLS
    conf.append(cacheDir.getPath());
    conf.append("\n  Preferences directory = "); // NON-NLS
    conf.append(prefDir.getPath());
    conf.append("\n  Look and Feel = "); // NON-NLS
    conf.append(look);
    String i18nPath = System.getProperty(P_WEASIS_I18N);
    if (Utils.hasText(i18nPath)) {
      conf.append("\n  Languages path = "); // NON-NLS
      conf.append(i18nPath);
    }
    conf.append("\n  Languages available = "); // NON-NLS
    conf.append(System.getProperty("weasis.languages", "en")); // NON-NLS
    conf.append("\n  OSGI native specs = "); // NON-NLS
    conf.append(System.getProperty(P_NATIVE_LIB_SPEC));
    conf.append("\n  HTTP user agent = "); // NON-NLS
    conf.append(System.getProperty("http.agent")); // NON-NLS
    conf.append("\n  Operating system = "); // NON-NLS
    conf.append(System.getProperty(P_OS_NAME));
    conf.append(' ');
    conf.append(System.getProperty("os.version"));
    conf.append(' ');
    conf.append(System.getProperty("os.arch"));
    conf.append("\n  Java vendor = "); // NON-NLS
    conf.append(System.getProperty("java.vendor"));
    conf.append("\n  Java version = "); // NON-NLS
    conf.append(System.getProperty("java.version")); // NON-NLS
    conf.append("\n  Java Path = "); // NON-NLS
    conf.append(System.getProperty("java.home")); // NON-NLS
    conf.append("\n  Java max memory (less survivor space) = "); // NON-NLS
    conf.append(FileUtil.humanReadableByteCount(Runtime.getRuntime().maxMemory(), false));

    conf.append("\n***** End of Configuration *****"); // NON-NLS
    LOGGER.log(Level.INFO, conf::toString);
    return loader;
  }

  private static boolean isZipResource(String path) {
    if (Utils.hasText(path) && path.endsWith(".zip")) {
      if (path.startsWith("file:")) { // NON-NLS
        try {
          URLConnection connection = new URL(path).openConnection();
          return connection.getContentLength() > 0;
        } catch (IOException e) {
          // Do nothing
        }
      } else {
        return true;
      }
    }
    return false;
  }

  private void loadI18nModules() {
    try {
      String cdbl = configData.getProperty(P_WEASIS_CODEBASE_LOCAL);
      String path = configData.getProperty(P_WEASIS_I18N, null);
      if (Utils.hasText(path)) {
        path +=
            path.endsWith("/") ? "buildNumber.properties" : "/buildNumber.properties"; // NON-NLS
        WeasisLauncher.readProperties(new URI(path), modulesi18n);
      } else if (cdbl == null) {
        String cdb = configData.getProperty(P_WEASIS_CODEBASE_URL, null);
        if (Utils.hasText(cdb)) {
          path = cdb.substring(0, cdb.lastIndexOf('/')) + "/weasis-i18n"; // NON-NLS
          WeasisLauncher.readProperties(
              new URI(path + "/buildNumber.properties"), modulesi18n); // NON-NLS
          if (!modulesi18n.isEmpty()) {
            System.setProperty(P_WEASIS_I18N, path);
          }
        }
      }

      // Try to find the native installation
      if (modulesi18n.isEmpty()) {
        if (cdbl == null) {
          cdbl = ConfigData.findLocalCodebase().getPath();
        }
        File file =
            new File(cdbl, "bundle-i18n" + File.separator + "buildNumber.properties"); // NON-NLS
        if (file.canRead()) {
          WeasisLauncher.readProperties(file.toURI(), modulesi18n);
          if (!modulesi18n.isEmpty()) {
            System.setProperty(P_WEASIS_I18N, file.getParentFile().toURI().toString());
          }
        }
      }

      if (!modulesi18n.isEmpty()) {
        System.setProperty("weasis.languages", modulesi18n.getProperty("languages", "")); // NON-NLS
      }
    } catch (Exception e) {
      LOGGER.log(Level.ERROR, "Cannot load translation modules", e);
    }
  }

  static class HaltTask extends TimerTask {
    @Override
    public void run() {
      System.out.println("Force to close the application"); // NON-NLS
      Runtime.getRuntime().halt(1);
    }
  }

  public static Locale textToLocale(String value) {
    if (!Utils.hasText(value)) {
      return Locale.ENGLISH;
    }

    if ("system".equals(value)) { // NON-NLS
      String language = System.getProperty("user.language", "en"); // NON-NLS
      String country = System.getProperty("user.country", ""); // NON-NLS
      String variant = System.getProperty("user.variant", ""); // NON-NLS
      return new Locale(language, country, variant);
    }

    String[] val = value.split("_", 3);
    String language = val.length > 0 ? val[0] : "";
    String country = val.length > 1 ? val[1] : "";
    String variant = val.length > 2 ? val[2] : "";

    return new Locale(language, country, variant);
  }

  private void registerAdditionalShutdownHook() {
    try {
      Class.forName("sun.misc.Signal");
      Class.forName("sun.misc.SignalHandler");
      sun.misc.Signal.handle(new sun.misc.Signal("TERM"), signal -> shutdownHook());
    } catch (IllegalArgumentException e) {
      LOGGER.log(Level.ERROR, "Register shutdownHook", e);
    } catch (ClassNotFoundException e) {
      LOGGER.log(Level.ERROR, "Cannot find sun.misc.Signal for shutdown hook extension", e);
    }
  }

  private void shutdownHook() {
    try {
      if (mFelix != null) {
        mFelix.stop();
        // wait asynchronous stop (max 30 seconds to stop all bundles)
        mFelix.waitForStop(30_000);
      }
    } catch (Exception ex) {
      System.err.println("Error stopping framework: " + ex); // NON-NLS
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    } finally {
      cleanImageCache();
      stopSingletonServer();

      // If System.exit() hangs call Runtime.getRuntime().halt(1) to kill the application
      Timer timer = new Timer();
      timer.schedule(new HaltTask(), 15000);
    }
  }

  protected void stopSingletonServer() {
    // Do nothing in this class
  }

  static void cleanImageCache() {
    // Clean temp folder.
    String dir = System.getProperty("weasis.tmp.dir");
    if (Utils.hasText(dir)) {
      FileUtil.deleteDirectoryContents(new File(dir), 3, 0);
    }
  }
}
