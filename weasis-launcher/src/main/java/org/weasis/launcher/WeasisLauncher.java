/*******************************************************************************
 * Copyright (C) 2009-2018 Weasis Team and others
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.launcher;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Font;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.management.ObjectName;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.Util;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.util.tracker.ServiceTracker;

public class WeasisLauncher {

    static {
        // Configuration of java.util.logging.Logger
        InputStream loggerProps =
            WeasisLauncher.class.getResourceAsStream(System.getProperty("java.logging.path", "/logging.properties")); //$NON-NLS-1$ //$NON-NLS-2$
        try {
            LogManager.getLogManager().readConfiguration(loggerProps);
        } catch (SecurityException | IOException e) {
            e.printStackTrace(); //NOSONAR cannot use logger
        }
    }

    private static final Logger LOGGER = Logger.getLogger(WeasisLauncher.class.getName());

    public enum Type {
        DEFAULT, NATIVE, JWS
    }

    public enum State {
        UNINSTALLED(0x00000001), INSTALLED(0x00000002), RESOLVED(0x00000004), STARTING(0x00000008),
        STOPPING(0x00000010), ACTIVE(0x00000020);

        private int index;

        private State(int state) {
            this.index = state;
        }

        public static String valueOf(int state) {
            for (State s : State.values()) {
                if (s.index == state) {
                    return s.name();
                }
            }
            return "UNKNOWN"; //$NON-NLS-1$
        }
    }

    /**
     * Switch for specifying bundle directory.
     **/
    public static final String BUNDLE_DIR_SWITCH = "-b"; //$NON-NLS-1$

    /**
     * The property name used to specify whether the launcher should install a shutdown hook.
     **/
    public static final String SHUTDOWN_HOOK_PROP = "felix.shutdown.hook"; //$NON-NLS-1$
    /**
     * The property name used to specify an URL to the configuration property file to be used for the created the
     * framework instance.
     **/
    public static final String CONFIG_PROPERTIES_PROP = "felix.config.properties"; //$NON-NLS-1$
    /**
     * The default name used for the configuration properties file.
     **/
    public static final String CONFIG_PROPERTIES_FILE_VALUE = "config.properties"; //$NON-NLS-1$
    /**
     * The property name used to specify an URL to the extended property file.
     **/
    public static final String EXTENDED_PROPERTIES_PROP = "felix.extended.config.properties"; //$NON-NLS-1$
    /**
     * The default name used for the extended properties file.
     **/
    public static final String EXTENDED_PROPERTIES_FILE_VALUE = "ext-config.properties"; //$NON-NLS-1$
    /**
     * Name of the configuration directory.
     */
    public static final String CONFIG_DIRECTORY = "conf"; //$NON-NLS-1$

    public static final String END_LINE = System.lineSeparator();
    public static final String APP_PROPERTY_FILE = "weasis.properties"; //$NON-NLS-1$
    public static final String P_WEASIS_VERSION = "weasis.version"; //$NON-NLS-1$
    public static final String P_WEASIS_PROFILE = "weasis.profile"; //$NON-NLS-1$
    public static final String P_WEASIS_NAME = "weasis.name"; //$NON-NLS-1$
    public static final String P_WEASIS_PATH = "weasis.path"; //$NON-NLS-1$
    public static final String P_WEASIS_RES_DATE = "weasis.resources.date"; //$NON-NLS-1$
    public static final String P_WEASIS_SOURCE_ID = "weasis.source.id"; //$NON-NLS-1$
    public static final String P_WEASIS_CODEBASE_URL = "weasis.codebase.url"; //$NON-NLS-1$
    public static final String P_WEASIS_PREFS_URL = "weasis.pref.url"; //$NON-NLS-1$
    public static final String P_WEASIS_USER = "weasis.user"; //$NON-NLS-1$
    public static final String P_OS_NAME = "os.name"; //$NON-NLS-1$
    public static final String P_WEASIS_LOOK = "weasis.look"; //$NON-NLS-1$
    public static final String P_GOSH_ARGS = "gosh.args"; //$NON-NLS-1$
    public static final String P_WEASIS_CLEAN_CACHE = "weasis.clean.cache"; //$NON-NLS-1$
    private static final String P_NATIVE_LIB_SPEC = "native.library.spec";
    public static final String F_RESOURCES = "resources"; //$NON-NLS-1$
    private static final String MAC_OS_X = "Mac OS X";

    protected Felix mFelix = null;
    protected ServiceTracker mTracker = null;
    protected volatile boolean frameworkLoaded = false;

    static Properties modulesi18n = null;
    protected String look = null;
    protected RemotePrefService remotePrefs;
    protected String localPrefsDir;

    protected final ConfigData configData;
    protected final Properties initProps;
    protected final Properties currentProps;

    public WeasisLauncher(ConfigData configData) {
        this.configData = Objects.requireNonNull(configData);
        this.initProps = new Properties();
        this.currentProps = new Properties();
    }

    public static void main(String[] argv) throws Exception {
        setSystemProperties(argv); // @Deprecated
        WeasisLauncher instance = new WeasisLauncher(new ConfigData(argv));
        instance.launch(Type.DEFAULT);
    }

    public void launch(Type type) throws Exception {
        // Set system property for dynamically loading only native libraries corresponding of the current platform
        setOsgiNativeLibSpecification();

        configData.applyConfig();

        final List<String> commandList = configData.getArguments();
        // Look for bundle directory and/or cache directory.
        // We support at most one argument, which is the bundle
        // cache directory.
        String bundleDir = null;
        String cacheDir = null;
        for (String command : commandList) {
            if (command.startsWith("felix")) { //$NON-NLS-1$
                String[] params = command.split(" "); //$NON-NLS-1$
                if (params.length < 3 || params.length > 4) {
                    LOGGER.log(Level.WARNING, "Usage: [$felix -b <bundle-deploy-dir>] [<bundle-cache-dir>]"); //$NON-NLS-1$
                } else {
                    bundleDir = params[2];
                    if (params.length > 3) {
                        cacheDir = params[3];
                    }
                }
                commandList.remove(command);
                break;
            }
        }

        // Deprecated to use "weasis.portable.dir"
        String portable = System.getProperty("weasis.portable.dir"); //$NON-NLS-1$
        if (portable != null) {
            applyCodebase(new File(portable, "weasis")); //$NON-NLS-1$
        }
        LOGGER.log(Level.INFO, "Starting the configuration..."); //$NON-NLS-1$
        StringBuilder conf = new StringBuilder();
        // Read configuration properties.
        Map<String, String> serverProp = WeasisLauncher.loadConfigProperties(conf);

        // If there is a passed in bundle auto-deploy directory, then
        // that overwrites anything in the config file.
        if (bundleDir != null) {
            serverProp.put(AutoProcessor.AUTO_DEPLOY_DIR_PROPERTY, bundleDir);
        }

        String profileName = serverProp.getOrDefault(P_WEASIS_PROFILE, "default"); //$NON-NLS-1$
        serverProp.put(P_WEASIS_PROFILE, profileName);

        // Define the sourceID for the temp and cache directory.
        String sourceID = configData.getSourceID();
        System.setProperty(P_WEASIS_SOURCE_ID, sourceID);

        cacheDir = serverProp.get(Constants.FRAMEWORK_STORAGE) + "-" + sourceID; //$NON-NLS-1$
        // If there is a passed in bundle cache directory, then
        // that overwrites anything in the config file.
        serverProp.put(Constants.FRAMEWORK_STORAGE, cacheDir);

        // Remove bundle not designed for Java 8
        if ("1.8".equals(System.getProperty("java.specification.version"))) { //$NON-NLS-1$ //$NON-NLS-2$
            serverProp.remove("felix.auto.start.7"); //$NON-NLS-1$
        }

        // Load local properties and clean if necessary the previous version
        WeasisLoader loader = loadProperties(serverProp, conf);
        WeasisMainFrame mainFrame = loader.getMainFrame();

        // If enabled, register a shutdown hook to make sure the framework is
        // cleanly shutdown when the VM exits
        if (Type.JWS == type) {
            handleWebstartHookBug();
            System.setProperty("http.bundle.cache", "false");
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
            loader.setFelix(serverProp, mFelix.getBundleContext());
            loader.writeLabel(
                String.format(Messages.getString("WeasisLauncher.starting"), System.getProperty(P_WEASIS_NAME))); //$NON-NLS-1$
            mTracker =
                new ServiceTracker(mFelix.getBundleContext(), "org.apache.felix.service.command.CommandProcessor", //$NON-NLS-1$
                    null);
            mTracker.open();

            // Start the framework.
            mFelix.start();

            // End of splash screen
            loader.close();
            loader = null;

            executeCommands(commandList, goshArgs);

            checkBundleUI(serverProp);
            frameworkLoaded = true;

            showMessage(mainFrame, serverProp);

            writeProperties();

            // Wait for framework to stop to exit the VM.
            mFelix.waitForStop(0);
            System.exit(0);
        } catch (Throwable ex) {
            exitStatus = -1;
            LOGGER.log(Level.SEVERE, "Cannot not start framework: " + ex); //$NON-NLS-1$
            LOGGER.log(Level.SEVERE, "Weasis cache will be cleaned at next launch."); //$NON-NLS-1$
            LOGGER.log(Level.SEVERE, "State of the framework:"); //$NON-NLS-1$
            for (Bundle b : mFelix.getBundleContext().getBundles()) {
                LOGGER.log(Level.SEVERE, " * " + b.getSymbolicName() + "-" + b.getVersion().toString() + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + State.valueOf(b.getState()));
            }
            resetBundleCache();
        } finally {
            Runtime.getRuntime().halt(exitStatus);
        }
    }

    public static void setJnlpSystemProperties() {

        final String PREFIX = "jnlp.weasis."; //$NON-NLS-1$
        final int PREFIX_LENGTH = PREFIX.length();

        Properties properties = System.getProperties();

        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(PREFIX)) {
                String value = properties.getProperty(propertyName);
                System.setProperty(propertyName.substring(PREFIX_LENGTH), value);
                properties.remove(propertyName);
            }
        }

        // Disabling extension framework is mandatory to work with Java Web Start.
        // From framework 4.4.1, See https://issues.apache.org/jira/browse/FELIX-4281.
        System.setProperty(FelixConstants.FELIX_EXTENSIONS_DISABLE, "true"); //$NON-NLS-1$
    }

    private static void setSystemProperties(String[] argv) {
        for (int i = 0; i < argv.length; i++) {
            // @Deprecated : use properties with the prefix "jnlp.weasis" instead
            if (argv[i].startsWith("-VMP") && argv[i].length() > 4) { //$NON-NLS-1$
                String[] vmarg = argv[i].substring(4).split("=", 2); //$NON-NLS-1$
                argv[i] = ""; //$NON-NLS-1$
                if (vmarg.length == 2) {
                    if (vmarg[1].startsWith("\"") && vmarg[1].endsWith("\"")) { //$NON-NLS-1$ //$NON-NLS-2$
                        vmarg[1] = vmarg[1].substring(1, vmarg[1].length() - 1);
                    }
                    System.setProperty(vmarg[0], Util.substVars(vmarg[1], vmarg[0], null, null));
                }
            }
        }
    }

    private void checkBundleUI(Map<String, String> serverProp) {
        String mainUI = serverProp.getOrDefault("weasis.main.ui", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
        if (Utils.hasText(mainUI)) {
            boolean uiStarted = false;
            for (Bundle b : mFelix.getBundleContext().getBundles()) {
                if (b.getSymbolicName().equals(mainUI) && b.getState() == Bundle.ACTIVE) {
                    uiStarted = true;
                    break;
                }
            }
            if (!uiStarted) {
                throw new IllegalStateException("Main User Interface bundle cannot be started"); //$NON-NLS-1$
            }
        }
    }

    private static String getGoshArgs(Map<String, String> serverProp) {
        String goshArgs = System.getProperty(P_GOSH_ARGS, serverProp.getOrDefault(P_GOSH_ARGS, "")); //$NON-NLS-1$
        if (goshArgs.isEmpty()) {
            String val = System.getProperty("gosh.port", ""); //$NON-NLS-1$ //$NON-NLS-2$
            if (!val.isEmpty()) {
                try {
                    goshArgs = String.format("-sc telnetd -p %d start", Integer.parseInt(val)); //$NON-NLS-1$
                } catch (NumberFormatException e) {
                    // Do nothing
                }
            }
        }
        serverProp.put(P_GOSH_ARGS, "--nointeractive --noshutdown"); //$NON-NLS-1$
        return goshArgs;
    }

    private static void displayStartingAciiIcon() {
        StringBuilder buf = new StringBuilder();
        buf.append(END_LINE);
        buf.append("Starting..."); //$NON-NLS-1$
        buf.append(END_LINE);
        buf.append(END_LINE);
        buf.append("         | | /| / /__ ___ ____ (_)__"); //$NON-NLS-1$
        buf.append(END_LINE);
        buf.append("         | |/ |/ / -_) _ `(_-</ (_-<"); //$NON-NLS-1$
        buf.append(END_LINE);
        buf.append("         |__/|__/\\__/\\_,_/___/_/___/"); //$NON-NLS-1$
        buf.append(END_LINE);
        LOGGER.log(Level.INFO, buf::toString);
    }

    protected void executeCommands(List<String> commandList, String goshArgs) {
        SwingUtilities.invokeLater(() -> {
            mTracker.open();

            // Do not close streams. Workaround for stackoverflow issue when using System.in
            Object commandSession =
                getCommandSession(mTracker.getService(), new Object[] { new FileInputStream(FileDescriptor.in),
                    new FileOutputStream(FileDescriptor.out), new FileOutputStream(FileDescriptor.err) });
            if (commandSession != null) {
                if (goshArgs == null) {
                    // Set the main window visible and to the front
                    commandSessionExecute(commandSession, "weasis:ui -v"); //$NON-NLS-1$
                } else {
                    // Start telnet after all other bundles. This will ensure that all the plugins commands are
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
            new File(System.getProperty(P_WEASIS_PATH, ""), System.getProperty(P_WEASIS_SOURCE_ID) + ".properties"); //$NON-NLS-1$ //$NON-NLS-2$
        Properties localSourceProp = new Properties();
        FileUtil.readProperties(sourceIdProps, localSourceProp);
        localSourceProp.setProperty(P_WEASIS_CLEAN_CACHE, "true"); //$NON-NLS-1$
        FileUtil.storeProperties(sourceIdProps, localSourceProp, null);
    }

    private void showMessage(final WeasisMainFrame mainFrame, Map<String, String> serverProp) {
        String versionOld = serverProp.get("prev." + P_WEASIS_VERSION); //$NON-NLS-1$
        String versionNew = serverProp.get(P_WEASIS_VERSION);
        // First time launch
        if (versionOld == null) {
            String val = getGeneralProperty("weasis.show.disclaimer", "true", serverProp, currentProps, false, false); //$NON-NLS-1$ //$NON-NLS-2$
            if (Boolean.valueOf(val)) {

                EventQueue.invokeLater(() -> {
                    Object[] options =
                        { Messages.getString("WeasisLauncher.ok"), Messages.getString("WeasisLauncher.no") }; //$NON-NLS-1$ //$NON-NLS-2$

                    String appName = System.getProperty(P_WEASIS_NAME);
                    int response = JOptionPane.showOptionDialog(
                        mainFrame.getRootPaneContainer() == null ? null
                            : mainFrame.getRootPaneContainer().getContentPane(),
                        String.format(Messages.getString("WeasisLauncher.msg"), appName), //$NON-NLS-1$
                        String.format(Messages.getString("WeasisLauncher.first"), appName), //$NON-NLS-1$
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, null);

                    if (response == 0) {
                        // Write "false" in weasis.properties. It can be useful when preferences are store remotely.
                        // The user will accept the disclaimer only once.
                        currentProps.setProperty("weasis.show.disclaimer", Boolean.FALSE.toString()); //$NON-NLS-1$
                    } else {
                        File file = new File(System.getProperty(P_WEASIS_PATH, ""), //$NON-NLS-1$
                            System.getProperty(P_WEASIS_SOURCE_ID) + ".properties"); //$NON-NLS-1$
                        // delete the properties file to ask again
                        FileUtil.delete(file);
                        LOGGER.log(Level.SEVERE, "Refusing the disclaimer"); //$NON-NLS-1$
                        System.exit(-1);
                    }
                });
            }
        } else if (versionNew != null && !versionNew.equals(versionOld)) {
            String val = getGeneralProperty("weasis.show.release", "true", serverProp, currentProps, false, false); //$NON-NLS-1$ //$NON-NLS-2$
            if (Boolean.valueOf(val)) {
                Version vOld = getVersion(versionOld);
                Version vNew = getVersion(versionNew);
                if (vNew.compareTo(vOld) > 0) {

                    String lastTag = currentProps.getProperty("weasis.version.release", null); //$NON-NLS-1$
                    if (lastTag != null) {
                        vOld = getVersion(lastTag);
                        if (vNew.compareTo(vOld) <= 0) {
                            // Message has been already displayed once.
                            return;
                        }
                    }
                    currentProps.setProperty("weasis.version.release", vNew.toString()); //$NON-NLS-1$
                }
                final String releaseNotesUrl = serverProp.get("weasis.releasenotes"); //$NON-NLS-1$
                final StringBuilder message = new StringBuilder("<P>"); //$NON-NLS-1$
                message.append(String.format(Messages.getString("WeasisLauncher.change.version"), //$NON-NLS-1$
                    System.getProperty(P_WEASIS_NAME), versionOld, versionNew));

                EventQueue.invokeLater(() -> {
                    JTextPane jTextPane1 = new JTextPane();
                    HTMLEditorKit kit = new HTMLEditorKit();
                    StyleSheet ss = kit.getStyleSheet();
                    ss.addRule("body {font-family:sans-serif;font-size:12pt;background-color:#" //$NON-NLS-1$
                        + Integer.toHexString((jTextPane1.getBackground().getRGB() & 0xffffff) | 0x1000000).substring(1)
                        + ";color:#" //$NON-NLS-1$
                        + Integer.toHexString((jTextPane1.getForeground().getRGB() & 0xffffff) | 0x1000000).substring(1)
                        + ";margin:3;font-weight:normal;}"); //$NON-NLS-1$
                    jTextPane1.setContentType("text/html"); //$NON-NLS-1$
                    jTextPane1.setEditable(false);
                    jTextPane1.addHyperlinkListener(e -> {
                        JTextPane pane = (JTextPane) e.getSource();
                        if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
                            pane.setToolTipText(e.getDescription());
                        } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
                            pane.setToolTipText(null);
                        } else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            if (System.getProperty(P_OS_NAME, "unknown").toLowerCase().startsWith("linux")) { //$NON-NLS-1$ //$NON-NLS-2$
                                try {
                                    String cmd = String.format("xdg-open %s", e.getURL()); //$NON-NLS-1$
                                    Runtime.getRuntime().exec(cmd);
                                } catch (IOException e1) {
                                    LOGGER.log(Level.SEVERE, "Unable to launch the WEB browser"); //$NON-NLS-1$
                                }
                            } else if (Desktop.isDesktopSupported()) {
                                final Desktop desktop = Desktop.getDesktop();
                                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                                    try {
                                        desktop.browse(e.getURL().toURI());

                                    } catch (Exception ex) {
                                        LOGGER.log(Level.SEVERE, "Unable to launch the WEB browser"); //$NON-NLS-1$
                                    }
                                }
                            }
                        }
                    });

                    message.append("<BR>"); //$NON-NLS-1$
                    String rn = Messages.getString("WeasisLauncher.release"); //$NON-NLS-1$
                    message.append(String.format("<a href=\"%s", //$NON-NLS-1$
                        releaseNotesUrl));
                    message.append("\" style=\"color:#FF9900\">"); //$NON-NLS-1$
                    message.append(rn);
                    message.append("</a>");//$NON-NLS-1$
                    message.append("</P>"); //$NON-NLS-1$
                    jTextPane1.setText(message.toString());
                    JOptionPane.showMessageDialog(
                        mainFrame.getRootPaneContainer() == null ? null
                            : mainFrame.getRootPaneContainer().getContentPane(),
                        jTextPane1, Messages.getString("WeasisLauncher.News"), JOptionPane.PLAIN_MESSAGE); //$NON-NLS-1$
                });
            }
        }
    }

    private static Version getVersion(String version) {
        String v = ""; //$NON-NLS-1$
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
        Class<?>[] parameterTypes = new Class[] { InputStream.class, OutputStream.class, OutputStream.class };
        try {
            Method nameMethod = commandProcessor.getClass().getMethod("createSession", parameterTypes); //$NON-NLS-1$
            Object commandSession = nameMethod.invoke(commandProcessor, arguments);
            addCommandSessionListener(commandProcessor);
            return commandSession;
        } catch (Exception ex) {
            // Since the services returned by the tracker could become
            // invalid at any moment, we will catch all exceptions, log
            // a message, and then ignore faulty services.
            LOGGER.log(Level.SEVERE, "Create a command session", ex); //$NON-NLS-1$
        }

        return null;
    }

    private static void addCommandSessionListener(Object commandProcessor) {
        try {
            ClassLoader loader = commandProcessor.getClass().getClassLoader();
            Class<?> c = loader.loadClass("org.apache.felix.service.command.CommandSessionListener"); //$NON-NLS-1$
            Method nameMethod = commandProcessor.getClass().getMethod("addListener", c); //$NON-NLS-1$

            Object listener = Proxy.newProxyInstance(loader, new Class[] { c }, new InvocationHandler() {

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String listenerMethod = method.getName();

                    if (listenerMethod.equals("beforeExecute")) { //$NON-NLS-1$
                        String arg = args[1].toString();
                        if (arg.startsWith("gosh") || arg.startsWith("gogo:gosh")) { //$NON-NLS-1$ //$NON-NLS-2$
                            // Force gogo to not use Expander to concatenate parameter with the current directory
                            // (Otherwise "*(|<[?" are interpreted, issue with URI parameters)
                            commandSessionExecute(args[0], "gogo.option.noglob=on"); //$NON-NLS-1$
                        }
                    } else if (listenerMethod.equals("equals")) { //$NON-NLS-1$
                        // Only add once in the set of listeners
                        return proxy.getClass().isAssignableFrom((args[0].getClass()));
                    }
                    return null;
                }
            });
            nameMethod.invoke(commandProcessor, listener);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Add command session listener", e); //$NON-NLS-1$
        }
    }

    public static boolean initCommandSession(Object commandSession, String args) {
        try {
            // wait for gosh command to be registered
            for (int i = 0; (i < 100) && commandSessionGet(commandSession, "gogo:gosh") == null; ++i) { //$NON-NLS-1$
                TimeUnit.MILLISECONDS.sleep(10);
            }

            Class<?>[] parameterTypes = new Class[] { CharSequence.class };
            Object[] arguments = new Object[] { "gogo:gosh --login " + (args == null ? "" : args) }; //$NON-NLS-1$ //$NON-NLS-2$
            Method nameMethod = commandSession.getClass().getMethod("execute", parameterTypes); //$NON-NLS-1$
            nameMethod.invoke(commandSession, arguments);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Init command session", e); //$NON-NLS-1$
        }
        return false;
    }

    public static Object commandSessionGet(Object commandSession, String key) {
        if (commandSession == null || key == null) {
            return null;
        }

        Class<?>[] parameterTypes = new Class[] { String.class };
        Object[] arguments = new Object[] { key };

        try {
            Method nameMethod = commandSession.getClass().getMethod("get", parameterTypes); //$NON-NLS-1$
            return nameMethod.invoke(commandSession, arguments);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Invoke a command", ex); //$NON-NLS-1$
        }

        return null;
    }

    public static boolean commandSessionClose(Object commandSession) {
        if (commandSession == null) {
            return false;
        }
        try {
            Method nameMethod = commandSession.getClass().getMethod("close"); //$NON-NLS-1$
            nameMethod.invoke(commandSession);
            return true;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Close command session", ex); //$NON-NLS-1$
        }

        return false;
    }

    public static Object commandSessionExecute(Object commandSession, CharSequence charSequence) {
        if (commandSession == null) {
            return false;
        }
        Class<?>[] parameterTypes = new Class[] { CharSequence.class };

        Object[] arguments = new Object[] { charSequence };

        try {
            Method nameMethod = commandSession.getClass().getMethod("execute", parameterTypes); //$NON-NLS-1$
            return nameMethod.invoke(commandSession, arguments);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Execute command", ex); //$NON-NLS-1$
        }

        return null;
    }

    /**
     * This following part has been copied from the Main class of the Felix project
     *
     **/

    /**
     * <p>
     * Loads the configuration properties in the configuration property file associated with the framework installation;
     * these properties are accessible to the framework and to bundles and are intended for configuration purposes. By
     * default, the configuration property file is located in the <tt>conf/</tt> directory of the Felix installation
     * directory and is called "<tt>config.properties</tt>". The installation directory of Felix is assumed to be the
     * parent directory of the <tt>felix.jar</tt> file as found on the system class path property. The precise file from
     * which to load configuration properties can be set by initializing the "<tt>felix.config.properties</tt>" system
     * property to an arbitrary URL.
     * </p>
     *
     * @return A <tt>Properties</tt> instance or <tt>null</tt> if there was an error.
     **/
    public static Map<String, String> loadConfigProperties(StringBuilder conf) {
        URI propURI = getPropertiesURI(CONFIG_PROPERTIES_PROP, CONFIG_PROPERTIES_FILE_VALUE);
        // Read the properties file
        Properties props = null;
        if (propURI != null) {
            conf.append("\n  Application configuration file = "); //$NON-NLS-1$
            conf.append(propURI);
            props = readProperties(propURI, null);
        } else {
            LOGGER.log(Level.SEVERE, "No config.properties path found, Weasis cannot start!"); //$NON-NLS-1$
        }

        propURI = getPropertiesURI(EXTENDED_PROPERTIES_PROP, EXTENDED_PROPERTIES_FILE_VALUE);
        if (propURI != null) {
            conf.append("\n  Application extension configuration file = "); //$NON-NLS-1$
            conf.append(propURI);
            // Extended properties, add or override existing properties
            props = readProperties(propURI, props);
        }

        if (props == null || props.isEmpty()) {
            throw new IllegalStateException("Cannot load weasis config!"); //$NON-NLS-1$
        }

        // Only required for dev purposes (running the app in IDE)
        String mvnRepo = System.getProperty("maven.localRepository", props.getProperty("maven.local.repo")); //$NON-NLS-1$ //$NON-NLS-2$
        if (mvnRepo != null) {
            System.setProperty("maven.localRepository", mvnRepo.replace("\\", "/")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        String cdb = System.getProperty(P_WEASIS_CODEBASE_URL);
        if (mvnRepo == null && cdb == null) {
            // Set the code base for the installed version
            applyCodebase(null);
        }

        // Perform variable substitution for system properties and
        // convert to dictionary.
        Map<String, String> map = new HashMap<>();
        for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            map.put(name, Util.substVars(props.getProperty(name), name, null, props));
        }
        return map;
    }

    public static URI getPropertiesURI(String configProp, String configFile) {

        // The config properties file is either specified by a system
        // property or it is in the conf/ directory of the Felix
        // installation directory. Try to load it from one of these
        // places.

        // See if the property URL was specified as a property.
        URI propURL;
        String custom = System.getProperty(configProp);
        if (custom != null) {
            try {
                propURL = new URI(custom);
            } catch (URISyntaxException e) {
                LOGGER.log(Level.SEVERE, configProp, e);
                return null;
            }
        } else {
            // Development folder only
            File confDir = new File(System.getProperty("user.dir") + File.separator + "target", CONFIG_DIRECTORY); //$NON-NLS-1$ //$NON-NLS-2$
            if (!confDir.canRead()) {
                confDir = null;
            }

            // Installed version
            if (confDir == null) {
                confDir = new File(findLocalCodebase(), CONFIG_DIRECTORY);
            }

            try {
                propURL = new File(confDir, configFile).toURI();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, configFile, ex);
                return null;
            }
        }
        return propURL;
    }

    public static Properties readProperties(URI propURI, Properties props) {
        Properties p = props == null ? new Properties() : props;

        try (InputStream is = FileUtil.getAdaptedConnection(propURI.toURL(), false).getInputStream()) {
            // Try to load config.properties
            p.load(is);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex, () -> String.format("Cannot read properties file: %s", propURI)); //$NON-NLS-1$
        }
        return p;
    }

    private static String getGeneralProperty(String key, String defaultValue, Map<String, String> serverProp,
        Properties localProp, boolean storeInLocalPref, boolean serviceProperty) {
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
            serverProp.put("def." + key, defaultVal); //$NON-NLS-1$
        }
        LOGGER.log(Level.CONFIG, "Config of {0} = {1}", new Object[] { key, value }); //$NON-NLS-1$
        return value;
    }

    public static void setOsgiNativeLibSpecification() {
        // Follows the OSGI specification to use Bundle-NativeCode in the bundle fragment :
        // http://www.osgi.org/Specifications/Reference
        String osName = System.getProperty(P_OS_NAME);
        String osArch = System.getProperty("os.arch"); //$NON-NLS-1$
        if (Utils.hasText(osName) && Utils.hasText(osArch)) {
            if (osName.toLowerCase().startsWith("win")) { //$NON-NLS-1$
                // All Windows versions with a specific processor architecture (x86 or x86-64) are grouped under
                // windows. If you need to make different native libraries for the Windows versions, define it in the
                // Bundle-NativeCode tag of the bundle fragment.
                osName = "windows"; //$NON-NLS-1$
            } else if (osName.equals(MAC_OS_X)) { // $NON-NLS-1$
                osName = "macosx"; //$NON-NLS-1$
            } else if (osName.equals("SymbianOS")) { //$NON-NLS-1$
                osName = "epoc32"; //$NON-NLS-1$
            } else if (osName.equals("hp-ux")) { //$NON-NLS-1$
                osName = "hpux"; //$NON-NLS-1$
            } else if (osName.equals("Mac OS")) { //$NON-NLS-1$
                osName = "macos"; //$NON-NLS-1$
            } else if (osName.equals("OS/2")) { //$NON-NLS-1$
                osName = "os2"; //$NON-NLS-1$
            } else if (osName.equals("procnto")) { //$NON-NLS-1$
                osName = "qnx"; //$NON-NLS-1$
            } else {
                osName = osName.toLowerCase();
            }

            if (osArch.equals("pentium") || osArch.equals("i386") || osArch.equals("i486") || osArch.equals("i586") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                || osArch.equals("i686")) { //$NON-NLS-1$
                osArch = "x86"; //$NON-NLS-1$
            } else if (osArch.equals("amd64") || osArch.equals("em64t") || osArch.equals("x86_64")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                osArch = "x86-64"; //$NON-NLS-1$
            } else if (osArch.equals("power ppc")) { //$NON-NLS-1$
                osArch = "powerpc"; //$NON-NLS-1$
            } else if (osArch.equals("psc1k")) { //$NON-NLS-1$
                osArch = "ignite"; //$NON-NLS-1$
            } else {
                osArch = osArch.toLowerCase();
            }
            System.setProperty(P_NATIVE_LIB_SPEC, osName + "-" + osArch); //$NON-NLS-1$
        }
    }

    public WeasisLoader loadProperties(Map<String, String> serverProp, StringBuilder conf) {
        currentProps.clear();

        String dir = new File(serverProp.get(Constants.FRAMEWORK_STORAGE)).getParent();
        System.setProperty(P_WEASIS_PATH, dir);

        String weasisName = serverProp.getOrDefault(P_WEASIS_NAME, "Weasis");//$NON-NLS-1$
        System.setProperty(P_WEASIS_NAME, weasisName);

        String profileName = serverProp.getOrDefault(P_WEASIS_PROFILE, "default"); //$NON-NLS-1$
        System.setProperty(P_WEASIS_PROFILE, profileName);

        boolean localSessionUser = false;
        String user = System.getProperty(P_WEASIS_USER);
        if (!Utils.hasText(user)) {
            localSessionUser = true;
            user = System.getProperty("user.name", "unknown"); //$NON-NLS-1$ //$NON-NLS-2$
            System.setProperty(P_WEASIS_USER, user);
        }

        StringBuilder bufDir = new StringBuilder(dir);
        bufDir.append(File.separator);
        bufDir.append("preferences"); //$NON-NLS-1$
        bufDir.append(File.separator);
        bufDir.append(user);
        bufDir.append(File.separator);
        bufDir.append(profileName);
        File prefDir = new File(bufDir.toString());
        try {
            prefDir.mkdirs();
        } catch (Exception e) {
            prefDir = new File(dir);
            LOGGER.log(Level.SEVERE, "Cannot create preferences folders", e); //$NON-NLS-1$
        }
        localPrefsDir = prefDir.getPath();
        serverProp.put("weasis.pref.dir", prefDir.getPath());
        
        boolean notContent = false;
        String remotePrefURL = System.getProperty(WeasisLauncher.P_WEASIS_PREFS_URL);
        if (Utils.hasText(remotePrefURL)) {
            String storeLocalSession = "weasis.pref.store.local.session";
            String defaultVal = System.getProperty(storeLocalSession, null);
            if (defaultVal == null) {
                defaultVal = serverProp.getOrDefault(storeLocalSession, "false");
            }
            serverProp.put(WeasisLauncher.P_WEASIS_PREFS_URL, remotePrefURL);
            serverProp.put("weasis.pref.local.session", String.valueOf(localSessionUser));
            serverProp.put(storeLocalSession, defaultVal);
            try {
                remotePrefs = new RemotePrefService(serverProp, user, profileName);
                remotePrefs.readLauncherPref(currentProps);
                if (currentProps.isEmpty()) {
                    notContent = true;
                }
            } catch (Exception e) {
                String msg = String.format("Cannot read Launcher preference for user: %s", user);
                LOGGER.log(Level.SEVERE, e, () -> msg);
            }
        }

        String portable = System.getProperty("weasis.portable.dir"); //$NON-NLS-1$
        if (portable != null) {
            LOGGER.log(Level.INFO, "Starting portable version"); //$NON-NLS-1$
            System.setProperty("weasis.portable.dicom.directory", //$NON-NLS-1$
                serverProp.get("weasis.portable.dicom.directory")); //$NON-NLS-1$
        }

        if (currentProps.isEmpty()) {
            File profileProps = new File(prefDir, APP_PROPERTY_FILE);
            FileUtil.readProperties(profileProps, currentProps);
        }
        resetInitProperties();
        if(notContent) {
            initProps.setProperty("no.content", "true");
        }
        
        // General Preferences priority order:
        // 1) Last value (does not exist for first launch of Weasis in an operating system session).
        // 2) Java System property
        // 3) Property defined in weasis/conf/config.properties or in ext-config.properties (extension of config)
        // 4) default value
        final String lang = getGeneralProperty("locale.lang.code", "en", serverProp, currentProps, true, false); //$NON-NLS-1$ //$NON-NLS-2$
        getGeneralProperty("locale.format.code", "system", serverProp, currentProps, true, false); //$NON-NLS-1$ //$NON-NLS-2$

        // Set value back to the bundle context properties, sling logger uses bundleContext.getProperty(prop)
        getGeneralProperty("org.apache.sling.commons.log.level", "INFO", serverProp, currentProps, true, true); //$NON-NLS-1$ //$NON-NLS-2$
        // Empty string make the file log writer disable
        String logActivatation = getGeneralProperty("org.apache.sling.commons.log.file.activate", "false", serverProp, //$NON-NLS-1$ //$NON-NLS-2$
            currentProps, true, true);
        if (Utils.getEmptytoFalse(logActivatation)) {
            String logFile = dir + File.separator + "log" + File.separator + "default.log"; //$NON-NLS-1$ //$NON-NLS-2$
            serverProp.put("org.apache.sling.commons.log.file", logFile); //$NON-NLS-1$
            currentProps.remove("org.apache.sling.commons.log.file"); //$NON-NLS-1$
        }

        getGeneralProperty("org.apache.sling.commons.log.file.number", "5", serverProp, currentProps, true, true); //$NON-NLS-1$ //$NON-NLS-2$
        getGeneralProperty("org.apache.sling.commons.log.file.size", "10MB", serverProp, currentProps, true, true); //$NON-NLS-1$ //$NON-NLS-2$
        getGeneralProperty("org.apache.sling.commons.log.stack.limit", "3", serverProp, currentProps, true, true); //$NON-NLS-1$ //$NON-NLS-2$
        getGeneralProperty("org.apache.sling.commons.log.pattern", //$NON-NLS-1$
            "{0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4}* [{2}] {3}: {5}", serverProp, currentProps, false, true); //$NON-NLS-1$

        String cdb = System.getProperty("weasis.codebase.local"); //$NON-NLS-1$
        URI translationModules = null;
        if (cdb != null) {
            File file = new File(cdb, "bundle-i18n/buildNumber.properties"); //$NON-NLS-1$
            if (file.canRead()) {
                translationModules = file.toURI();
                String path = file.getParentFile().toURI().toString();
                System.setProperty("weasis.i18n", path); //$NON-NLS-1$
            }
        } else {
            String path = System.getProperty("weasis.i18n", null); //$NON-NLS-1$
            if (path != null) {
                path += path.endsWith("/") ? "buildNumber.properties" : "/buildNumber.properties"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                try {
                    translationModules = new URI(path);
                } catch (URISyntaxException e) {
                    LOGGER.log(Level.SEVERE, "Cannot find translation modules", e); //$NON-NLS-1$
                }
            }
        }
        if (translationModules != null) {
            modulesi18n = readProperties(translationModules, null);
            if (modulesi18n != null) {
                System.setProperty("weasis.languages", modulesi18n.getProperty("languages", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }

        Locale locale = textToLocale(lang);
        if (Locale.ENGLISH.equals(locale)) {
            // if English no need to load i18n bundle fragments
            modulesi18n = null;
        } else {
            String suffix = locale.toString();
            SwingResources.loadResources("/swing/basic_" + suffix + ".properties"); //$NON-NLS-1$ //$NON-NLS-2$
            SwingResources.loadResources("/swing/synth_" + suffix + ".properties"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // JVM Locale
        Locale.setDefault(locale);
        // LookAndFeel Locale
        UIManager.getDefaults().setDefaultLocale(locale);
        // For new components
        JComponent.setDefaultLocale(locale);

        String nativeLook;
        String sysSpec = System.getProperty(P_NATIVE_LIB_SPEC, "unknown"); //$NON-NLS-1$
        int index = sysSpec.indexOf('-');
        if (index > 0) {
            nativeLook = "weasis.look." + sysSpec.substring(0, index); //$NON-NLS-1$
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
        // installSubstanceLookAndFeels must be the first condition to install substance if necessary
        if (LookAndFeels.installSubstanceLookAndFeels() && look == null) {
            if (MAC_OS_X.equals(System.getProperty(P_OS_NAME))) { // $NON-NLS-1$
                look = "com.apple.laf.AquaLookAndFeel"; //$NON-NLS-1$
            } else {
                look = "org.pushingpixels.substance.api.skin.SubstanceTwilightLookAndFeel"; //$NON-NLS-1$
            }
        }
        // Set the default value for L&F
        if (look == null) {
            look = getAvailableLookAndFeel(look);
        }
        serverProp.put(P_WEASIS_LOOK, look);

        // If look is in local prefs, use it
        if (localLook != null) {
            look = localLook;
        }

        /*
         * Build a Frame
         *
         * This will ensure the popup message or other dialogs to have frame parent. When the parent is null the dialog
         * can be hidden under the main frame
         */
        final WeasisMainFrame mainFrame = new WeasisMainFrame();

        try {
            SwingUtilities.invokeAndWait(() -> {
                // Set look and feels
                boolean substance = look.startsWith("org.pushingpixels"); //$NON-NLS-1$
                if (substance) {
                    // Keep system window for the main frame
                    // JFrame.setDefaultLookAndFeelDecorated(true);
                    JDialog.setDefaultLookAndFeelDecorated(true);
                }
                look = setLookAndFeel(look);

                try {
                    // Build a JFrame which will be used later in base.ui module
                    ObjectName objectName2 = new ObjectName("weasis:name=MainWindow"); //$NON-NLS-1$
                    mainFrame.setRootPaneContainer(new JFrame());
                    ManagementFactory.getPlatformMBeanServer().registerMBean(mainFrame, objectName2);
                } catch (Exception e1) {
                    LOGGER.log(Level.SEVERE, "Cannot register the main frame", e1); //$NON-NLS-1$
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unable to set the Look&Feel {0}", look); //$NON-NLS-1$
        }
        currentProps.put(P_WEASIS_LOOK, look);

        File sourceIDProps = new File(dir, System.getProperty(P_WEASIS_SOURCE_ID) + ".properties"); //$NON-NLS-1$
        Properties localSourceProp = new Properties();
        FileUtil.readProperties(sourceIDProps, localSourceProp);

        final String versionOld = localSourceProp.getProperty(P_WEASIS_VERSION);
        if (versionOld != null) {
            serverProp.put("prev." + P_WEASIS_VERSION, versionOld); //$NON-NLS-1$
        }
        final String versionNew = serverProp.get(P_WEASIS_VERSION);
        String cleanCacheAfterCrash = localSourceProp.getProperty(P_WEASIS_CLEAN_CACHE);

        boolean update = false;
        // Loads the resource files
        String defaultResources = "/resources.zip"; //$NON-NLS-1$
        String resPath = serverProp.getOrDefault("weasis.resources.url", //$NON-NLS-1$
            System.getProperty(P_WEASIS_CODEBASE_URL, "") + defaultResources); //$NON-NLS-1$
        File cacheDir = null;
        boolean localRes = cdb != null && new File(cdb, F_RESOURCES).exists();
        try {
            if (!localRes && resPath.endsWith(".zip") && !resPath.equals(defaultResources)) { //$NON-NLS-1$
                cacheDir =
                    new File(dir + File.separator + "data" + File.separator + System.getProperty(P_WEASIS_SOURCE_ID), //$NON-NLS-1$
                        F_RESOURCES);
                String date =
                    FileUtil.writeResources(resPath, cacheDir, localSourceProp.getProperty(P_WEASIS_RES_DATE));
                if (date != null) {
                    update = true;
                    localSourceProp.put(P_WEASIS_RES_DATE, date);
                }
            }
        } catch (Exception e) {
            cacheDir = null;
            LOGGER.log(Level.SEVERE, "Loads the resource folder", e); //$NON-NLS-1$
        }

        if (cacheDir == null) {
            if (cdb != null) {
                cacheDir = new File(cdb, F_RESOURCES);
            } else {
                File f = new File(System.getProperty("user.dir")); //$NON-NLS-1$
                cacheDir = new File(f.getParent(), "weasis-distributions" + File.separator + F_RESOURCES); //$NON-NLS-1$
            }
        }
        serverProp.put("weasis.resources.path", cacheDir.getPath()); //$NON-NLS-1$

        // Splash screen that shows bundles loading
        final WeasisLoader loader = new WeasisLoader(cacheDir, mainFrame);
        // Display splash screen
        loader.open();

        if (versionNew != null) {
            // Add also to java properties for the about
            System.setProperty(P_WEASIS_VERSION, versionNew);
            localSourceProp.put(P_WEASIS_VERSION, versionNew);
            if (versionOld == null || !versionOld.equals(versionNew)) {
                update = true;
            }
        }

        // Clean cache if Weasis has crashed during the previous launch
        boolean cleanCache = Boolean.parseBoolean(serverProp.get("weasis.clean.previous.version")); //$NON-NLS-1$
        if (cleanCacheAfterCrash != null && "true".equals(cleanCacheAfterCrash)) { //$NON-NLS-1$
            serverProp.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
            localSourceProp.remove(P_WEASIS_CLEAN_CACHE);
            update = true;
            LOGGER.log(Level.INFO, "Clean plug-in cache because Weasis has crashed during the previous launch"); //$NON-NLS-1$
        }
        // Clean cache when version has changed
        else if (cleanCache && versionNew != null && !versionNew.equals(versionOld)) {
            LOGGER.log(Level.INFO, "Clean previous Weasis version: {0}", versionOld); //$NON-NLS-1$
            serverProp.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
            LOGGER.log(Level.INFO, "Clean plug-in cache because the version has changed"); //$NON-NLS-1$
        }

        if (update) {
            FileUtil.storeProperties(sourceIDProps, localSourceProp, null);
        }

        String pevConf = conf.toString();
        conf.setLength(0);
        conf.append("\n***** Configuration *****"); //$NON-NLS-1$
        conf.append("\n  Last running version = "); //$NON-NLS-1$
        conf.append(versionOld);
        conf.append("\n  Current version = "); //$NON-NLS-1$
        conf.append(versionNew);
        conf.append("\n  Application name = "); //$NON-NLS-1$
        conf.append(weasisName);
        conf.append("\n  Application ID = "); //$NON-NLS-1$
        conf.append(System.getProperty(P_WEASIS_SOURCE_ID));
        conf.append("\n  Application Profile = "); //$NON-NLS-1$
        conf.append(profileName);
        conf.append(pevConf);
        conf.append("\n  User = "); //$NON-NLS-1$
        conf.append(System.getProperty(P_WEASIS_USER, "user")); //$NON-NLS-1$
        conf.append("\n  User home directory = "); //$NON-NLS-1$
        conf.append(dir);
        conf.append("\n  Resources path = "); //$NON-NLS-1$
        conf.append(cacheDir.getPath());
        conf.append("\n  Preferences directory = "); //$NON-NLS-1$
        conf.append(prefDir.getPath());
        conf.append("\n  Look and Feel = "); //$NON-NLS-1$
        conf.append(look);
        if (translationModules != null) {
            conf.append("\n  Languages path = "); //$NON-NLS-1$
            conf.append(translationModules);
        }
        conf.append("\n  Languages available = "); //$NON-NLS-1$
        conf.append(System.getProperty("weasis.languages", "en")); //$NON-NLS-1$ //$NON-NLS-2$
        conf.append("\n  OSGI native specs = "); //$NON-NLS-1$
        conf.append(System.getProperty(P_NATIVE_LIB_SPEC)); // $NON-NLS-1$
        conf.append("\n  Operating system = "); //$NON-NLS-1$
        conf.append(System.getProperty(P_OS_NAME));
        conf.append(' ');
        conf.append(System.getProperty("os.version")); //$NON-NLS-1$
        conf.append(' ');
        conf.append(System.getProperty("os.arch")); //$NON-NLS-1$
        conf.append("\n  Java vendor = "); //$NON-NLS-1$
        conf.append(System.getProperty("java.vendor")); //$NON-NLS-1$
        conf.append("\n  Java version = "); //$NON-NLS-1$
        conf.append(System.getProperty("java.version")); //$NON-NLS-1$
        conf.append("\n  Java Path = "); //$NON-NLS-1$
        conf.append(System.getProperty("java.home")); //$NON-NLS-1$
        conf.append("\n  Java max memory (less survivor space) = "); //$NON-NLS-1$
        conf.append(FileUtil.humanReadableByteCount(Runtime.getRuntime().maxMemory(), false));

        conf.append("\n***** End of Configuration *****"); //$NON-NLS-1$
        LOGGER.log(Level.INFO, conf::toString);
        return loader;
    }

    /**
     * Changes the look and feel for the whole GUI
     */

    public static String setLookAndFeel(String look) {
        // Do not display metal LAF in bold, it is ugly
        UIManager.put("swing.boldMetal", Boolean.FALSE); //$NON-NLS-1$
        // Display slider value is set to false (already in all LAF by the panel title), used by GTK LAF
        UIManager.put("Slider.paintValue", Boolean.FALSE); //$NON-NLS-1$

        String laf = getAvailableLookAndFeel(look);
        try {
            UIManager.setLookAndFeel(laf);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unable to set the Look&Feel", e); //$NON-NLS-1$
            laf = UIManager.getSystemLookAndFeelClassName();
        }
        // Fix font issue for displaying some Asiatic characters. Some L&F have special fonts.
        LookAndFeels.setUIFont(new javax.swing.plaf.FontUIResource(Font.SANS_SERIF, Font.PLAIN, 12)); // $NON-NLS-1$
        return laf;
    }

    public static String getAvailableLookAndFeel(String look) {
        UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
        String laf = null;
        if (look != null) {
            for (int i = 0, n = lafs.length; i < n; i++) {
                if (lafs[i].getClassName().equals(look)) {
                    laf = look;
                    break;
                }
            }
        }
        if (laf == null) {
            if (MAC_OS_X.equals(System.getProperty(P_OS_NAME))) { // $NON-NLS-1$
                laf = "com.apple.laf.AquaLookAndFeel"; //$NON-NLS-1$
            } else {
                // Try to set Nimbus, concurrent thread issue
                // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6785663
                for (int i = 0, n = lafs.length; i < n; i++) {
                    if (lafs[i].getName().equals("Nimbus")) { //$NON-NLS-1$
                        laf = lafs[i].getClassName();
                        break;
                    }
                }
            }
            // Should never happen
            if (laf == null) {
                laf = UIManager.getSystemLookAndFeelClassName();
            }

        }
        return laf;
    }

    static class HaltTask extends TimerTask {
        @Override
        public void run() {
            System.out.println("Force to close the application"); //$NON-NLS-1$
            Runtime.getRuntime().halt(1);
        }
    }

    public static Locale textToLocale(String value) {
        if (!Utils.hasText(value)) {
            return Locale.ENGLISH;
        }

        if ("system".equals(value)) { //$NON-NLS-1$
            String language = System.getProperty("user.language", "en"); //$NON-NLS-1$ //$NON-NLS-2$
            String country = System.getProperty("user.country", ""); //$NON-NLS-1$ //$NON-NLS-2$
            String variant = System.getProperty("user.variant", ""); //$NON-NLS-1$ //$NON-NLS-2$
            return new Locale(language, country, variant);
        }

        String[] val = value.split("_", 3); //$NON-NLS-1$
        String language = val.length > 0 ? val[0] : ""; //$NON-NLS-1$
        String country = val.length > 1 ? val[1] : ""; //$NON-NLS-1$
        String variant = val.length > 2 ? val[2] : ""; //$NON-NLS-1$

        return new Locale(language, country, variant);
    }

    private void registerAdditionalShutdownHook() {
        try {
            Class.forName("sun.misc.Signal"); //$NON-NLS-1$
            Class.forName("sun.misc.SignalHandler"); //$NON-NLS-1$
            sun.misc.Signal.handle(new sun.misc.Signal("TERM"), signal -> shutdownHook()); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Register shutdownHook", e); //$NON-NLS-1$
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Cannot find sun.misc.Signal for shutdown hook exstension", e); //$NON-NLS-1$
        }
    }

    public static int getJavaMajorVersion() {
        // Handle new versioning from Java 9
        String jvmVersionString = System.getProperty("java.specification.version"); //$NON-NLS-1$
        int verIndex = jvmVersionString.indexOf("1."); //$NON-NLS-1$
        if (verIndex >= 0) {
            jvmVersionString = jvmVersionString.substring(verIndex + 2);
        }
        return Integer.parseInt(jvmVersionString);
    }

    /**
     * @see https://bugs.openjdk.java.net/browse/JDK-8054639
     *
     */
    private static void handleWebstartHookBug() {
        if (getJavaMajorVersion() < 9) {
            // there is a bug that arrived sometime around the mid java7 releases. shutdown hooks get created that
            // shutdown loggers and close down the classloader jars that means that anything we try to do in our
            // shutdown hook throws an exception, but only after some random amount of time
            try {
                Class<?> clazz = Class.forName("java.lang.ApplicationShutdownHooks"); //$NON-NLS-1$
                Field field = clazz.getDeclaredField("hooks"); //$NON-NLS-1$
                field.setAccessible(true);
                Map<?, Thread> hooks = (Map<?, Thread>) field.get(clazz);
                for (Iterator<Thread> it = hooks.values().iterator(); it.hasNext();) {
                    Thread thread = it.next();
                    if ("javawsSecurityThreadGroup".equals(thread.getThreadGroup().getName())) { //$NON-NLS-1$
                        it.remove();
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "JWS shutdownHook", e); //$NON-NLS-1$
            }
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
            System.err.println("Error stopping framework: " + ex); //$NON-NLS-1$
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

    private void writeProperties() {
        if (!currentProps.equals(initProps) && localPrefsDir != null) {
            File file = new File(localPrefsDir, APP_PROPERTY_FILE);
            if (remotePrefs == null) {
                FileUtil.storeProperties(file, currentProps, null);
                resetInitProperties();
            } else {
                try {
                    Properties remoteProps = remotePrefs.storeLauncherPref(currentProps);
                    if (remoteProps != null) {
                        FileUtil.storeProperties(file, remoteProps, null);
                        currentProps.putAll(remoteProps);
                        resetInitProperties();
                    }
                } catch (Exception e) {
                    String msg = String.format("Cannot store Launcher preference for user: %s", remotePrefs.getUser());
                    LOGGER.log(Level.SEVERE, e, () -> msg);
                }
            }
        }
        currentProps.clear();
        initProps.clear();
    }

    private void resetInitProperties() {
        initProps.clear();
        initProps.putAll(currentProps);
    }

    static void cleanImageCache() {
        // Clean temp folder.
        String dir = System.getProperty("weasis.tmp.dir"); //$NON-NLS-1$
        if (dir != null) {
            FileUtil.deleteDirectoryContents(new File(dir), 3, 0);
        }
    }

    private static File findLocalCodebase() {
        // Determine where the configuration directory is by figuring
        // out where weasis-launcher.jar is located on the system class path.
        String jarLocation = null;
        String classpath = System.getProperty("java.class.path"); //$NON-NLS-1$
        String[] vals = classpath.split(File.pathSeparator);
        for (String cp : vals) {
            if (cp.endsWith("weasis-launcher.jar")) { //$NON-NLS-1$
                jarLocation = cp;
            }
        }
        if (jarLocation == null) {
            throw new IllegalStateException(
                "Cannot find the local repository path, weasis-launcher.jar is not the classpath!"); //$NON-NLS-1$
        } else {
            return new File(new File(jarLocation).getAbsolutePath()).getParentFile();
        }
    }

    private static String applyCodebase(File localCodebase) {
        File baseDir = localCodebase == null ? findLocalCodebase() : localCodebase;
        String baseURI = baseDir.toURI().toString();
        try {
            System.setProperty("weasis.codebase.local", baseDir.getAbsolutePath()); //$NON-NLS-1$
            System.setProperty(P_WEASIS_CODEBASE_URL, baseURI);
            baseURI += "/" + CONFIG_DIRECTORY + "/"; //$NON-NLS-1$ //$NON-NLS-2$
            if (System.getProperty(CONFIG_PROPERTIES_PROP) == null) {
                System.setProperty(CONFIG_PROPERTIES_PROP, baseURI + CONFIG_PROPERTIES_FILE_VALUE);
            }
            if (System.getProperty(EXTENDED_PROPERTIES_PROP) == null) {
                System.setProperty(EXTENDED_PROPERTIES_PROP, baseURI + EXTENDED_PROPERTIES_FILE_VALUE);
            }
            // Allow export feature for portable version
            System.setProperty("weasis.export.dicom", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            System.setProperty("weasis.export.dicom.send", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            System.setProperty("weasis.import.dicom", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            System.setProperty("weasis.import.dicom.qr", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Apply Codebase", e); //$NON-NLS-1$
        }
        return baseURI;
    }

}
