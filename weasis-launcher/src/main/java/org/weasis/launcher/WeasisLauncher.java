/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.Util;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.util.tracker.ServiceTracker;
import org.weasis.launcher.applet.WeasisFrame;

public class WeasisLauncher {
    public enum STATE {
        UNINSTALLED(0x00000001), INSTALLED(0x00000002), RESOLVED(0x00000004), STARTING(0x00000008),
        STOPPING(0x00000010), ACTIVE(0x00000020);

        private int state;

        private STATE(int state) {
            this.state = state;
        }

        public static String valueOf(int state) {
            for (STATE s : STATE.values()) {
                if (s.state == state) {
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

    static volatile Felix m_felix = null;
    static volatile ServiceTracker m_tracker = null;
    static volatile boolean frameworkLoaded = false;

    private static String APP_PROPERTY_FILE = "weasis.properties"; //$NON-NLS-1$
    public static final String P_WEASIS_VERSION = "weasis.version"; //$NON-NLS-1$
    public static final String P_WEASIS_PROFILE = "weasis.profile"; //$NON-NLS-1$
    public static final String P_WEASIS_NAME = "weasis.name"; //$NON-NLS-1$
    public static final String P_WEASIS_PATH = "weasis.path"; //$NON-NLS-1$
    private static final String P_WEASIS_RES_DATE = "weasis.resources.date"; //$NON-NLS-1$
    static Properties modulesi18n = null;
    private static String look = null;

    private static RemotePreferences REMOTE_PREFS;
    private static File prefDir;

    /**
     * <p>
     * This method performs the main task of constructing an framework instance and starting its execution. The
     * following functions are performed when invoked:
     * </p>
     * <ol>
     * <li><i><b>Examine and verify command-line arguments.</b></i> The launcher accepts a "<tt>-b</tt>" command line
     * switch to set the bundle auto-deploy directory and a single argument to set the bundle cache directory.</li>
     * <li><i><b>Read the system properties file.</b></i> This is a file containing properties to be pushed into
     * <tt>System.setProperty()</tt> before starting the framework. This mechanism is mainly shorthand for people
     * starting the framework from the command line to avoid having to specify a bunch of <tt>-D</tt> system property
     * definitions. The only properties defined in this file that will impact the framework's behavior are the those
     * concerning setting HTTP proxies, such as <tt>http.proxyHost</tt>, <tt>http.proxyPort</tt>, and
     * <tt>http.proxyAuth</tt>. Generally speaking, the framework does not use system properties at all.</li>
     * <li><i><b>Read the framework's configuration property file.</b></i> This is a file containing properties used to
     * configure the framework instance and to pass configuration information into bundles installed into the framework
     * instance. The configuration property file is called <tt>config.properties</tt> by default and is located in the
     * <tt>conf/</tt> directory of the Felix installation directory, which is the parent directory of the directory
     * containing the <tt>felix.jar</tt> file. It is possible to use a different location for the property file by
     * specifying the desired URL using the <tt>felix.config.properties</tt> system property; this should be set using
     * the <tt>-D</tt> syntax when executing the JVM. If the <tt>config.properties</tt> file cannot be found, then
     * default values are used for all configuration properties. Refer to the <a href="Felix.html#Felix(java.util.Map)">
     * <tt>Felix</tt></a> constructor documentation for more information on framework configuration properties.</li>
     * <li><i><b>Copy configuration properties specified as system properties into the set of configuration
     * properties.</b></i> Even though the Felix framework does not consult system properties for configuration
     * information, sometimes it is convenient to specify them on the command line when launching Felix. To make this
     * possible, the Felix launcher copies any configuration properties specified as system properties into the set of
     * configuration properties passed into Felix.</li>
     * <li><i><b>Add shutdown hook.</b></i> To make sure the framework shutdowns cleanly, the launcher installs a
     * shutdown hook; this can be disabled with the <tt>felix.shutdown.hook</tt> configuration property.</li>
     * <li><i><b>Create and initialize a framework instance.</b></i> The OSGi standard <tt>FrameworkFactory</tt> is
     * retrieved from <tt>META-INF/services</tt> and used to create a framework instance with the configuration
     * properties.</li>
     * <li><i><b>Auto-deploy bundles.</b></i> All bundles in the auto-deploy directory are deployed into the framework
     * instance.</li>
     * <li><i><b>Start the framework.</b></i> The framework is started and the launcher thread waits for the framework
     * to shutdown.</li>
     * </ol>
     * <p>
     * It should be noted that simply starting an instance of the framework is not enough to create an interactive
     * session with it. It is necessary to install and start bundles that provide a some means to interact with the
     * framework; this is generally done by bundles in the auto-deploy directory or specifying an "auto-start" property
     * in the configuration property file. If no bundles providing a means to interact with the framework are installed
     * or if the configuration property file cannot be found, the framework will appear to be hung or deadlocked. This
     * is not the case, it is executing correctly, there is just no way to interact with it.
     * </p>
     * <p>
     * The launcher provides two ways to deploy bundles into a framework at startup, which have associated configuration
     * properties:
     * </p>
     * <ul>
     * <li>Bundle auto-deploy - Automatically deploys all bundles from a specified directory, controlled by the
     * following configuration properties:
     * <ul>
     * <li><tt>felix.auto.deploy.dir</tt> - Specifies the auto-deploy directory from which bundles are automatically
     * deploy at framework startup. The default is the <tt>bundle/</tt> directory of the current directory.</li>
     * <li><tt>felix.auto.deploy.action</tt> - Specifies the auto-deploy actions to be found on bundle JAR files found
     * in the auto-deploy directory. The possible actions are <tt>install</tt>, <tt>update</tt>, <tt>start</tt>, and
     * <tt>uninstall</tt>. If no actions are specified, then the auto-deploy directory is not processed. There is no
     * default value for this property.</li>
     * </ul>
     * </li>
     * <li>Bundle auto-properties - Configuration properties which specify URLs to bundles to install/start:
     * <ul>
     * <li><tt>felix.auto.install.N</tt> - Space-delimited list of bundle URLs to automatically install when the
     * framework is started, where <tt>N</tt> is the start level into which the bundle will be installed (e.g.,
     * felix.auto.install.2).</li>
     * <li><tt>felix.auto.start.N</tt> - Space-delimited list of bundle URLs to automatically install and start when the
     * framework is started, where <tt>N</tt> is the start level into which the bundle will be installed (e.g.,
     * felix.auto.start.2).</li>
     * </ul>
     * </li>
     * </ul>
     * <p>
     * These properties should be specified in the <tt>config.properties</tt> so that they can be processed by the
     * launcher during the framework startup process.
     * </p>
     *
     * @param argv
     *            Accepts arguments to set the auto-deploy directory and/or the bundle cache directory.
     * @throws Exception
     *             If an error occurs.
     **/
    public static void main(String[] argv) throws Exception {
        launch(argv);
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

    public static void launch(String[] argv) throws Exception {
        // Set system property for dynamically loading only native libraries corresponding of the current platform
        setSystemSpecification();

        // Remove the prefix "jnlp.weasis" of JNLP Properties
        // Workaround for having a fully trusted application with JWS,
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6653241
        setJnlpSystemProperties();

        for (int i = 0; i < argv.length; i++) {
            // @Deprecated : use properties with the prefix "jnlp.weasis" instead
            if (argv[i].startsWith("-VMP") && argv[i].length() > 4) { //$NON-NLS-1$
                String[] vmarg = argv[i].substring(4).split("=", 2); //$NON-NLS-1$
                if (vmarg.length == 2) {
                    if (vmarg[1].startsWith("\"") && vmarg[1].endsWith("\"")) { //$NON-NLS-1$ //$NON-NLS-2$
                        vmarg[1] = vmarg[1].substring(1, vmarg[1].length() - 1);
                    }
                    System.setProperty(vmarg[0], Util.substVars(vmarg[1], vmarg[0], null, null));
                }
            }
        }

        final List<StringBuilder> commandList = splitCommand(argv);
        // Look for bundle directory and/or cache directory.
        // We support at most one argument, which is the bundle
        // cache directory.
        String bundleDir = null;
        String cacheDir = null;
        for (StringBuilder c : commandList) {
            String command = c.toString();
            if (command.startsWith("felix")) { //$NON-NLS-1$
                String[] params = command.split(" "); //$NON-NLS-1$
                if (params.length < 3 || params.length > 4) {
                    System.err.println("Usage: [$felix -b <bundle-deploy-dir>] [<bundle-cache-dir>]"); //$NON-NLS-1$
                } else {
                    bundleDir = params[2];
                    if (params.length > 3) {
                        cacheDir = params[3];
                    }
                }
                commandList.remove(c);
                break;
            }
        }

        String portable = System.getProperty("weasis.portable.dir"); //$NON-NLS-1$
        if (portable != null) {
            File basePortableDir = new File(portable);
            String baseURL = ""; //$NON-NLS-1$
            try {
                baseURL = basePortableDir.toURI().toURL().toString() + "weasis"; //$NON-NLS-1$
                System.setProperty("weasis.codebase.url", baseURL); //$NON-NLS-1$
                baseURL += "/" + CONFIG_DIRECTORY + "/"; //$NON-NLS-1$ //$NON-NLS-2$
                if (System.getProperty(CONFIG_PROPERTIES_PROP) == null) {
                    System.setProperty(CONFIG_PROPERTIES_PROP, baseURL + CONFIG_PROPERTIES_FILE_VALUE);
                }
                if (System.getProperty(EXTENDED_PROPERTIES_PROP) == null) {
                    System.setProperty(EXTENDED_PROPERTIES_PROP, baseURL + EXTENDED_PROPERTIES_FILE_VALUE);
                }
                // Allow export feature for portable version
                System.setProperty("weasis.export.dicom", "true"); //$NON-NLS-1$ //$NON-NLS-2$
                System.setProperty("weasis.export.dicom.send", "true"); //$NON-NLS-1$ //$NON-NLS-2$
                System.setProperty("weasis.import.dicom", "true"); //$NON-NLS-1$ //$NON-NLS-2$
                System.setProperty("weasis.import.dicom.qr", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println();
        System.out.println("***** Starting Configuration *****"); //$NON-NLS-1$
        // Read configuration properties.
        Map<String, String> serverProp = WeasisLauncher.loadConfigProperties();
        // If no configuration properties were found, then create
        // an empty properties object.
        if (serverProp == null) {
            System.err.println("Cannot start, no " + CONFIG_PROPERTIES_FILE_VALUE + " found."); //$NON-NLS-1$ //$NON-NLS-2$
            serverProp = new HashMap<>();
        }

        // If there is a passed in bundle auto-deploy directory, then
        // that overwrites anything in the config file.
        if (bundleDir != null) {
            serverProp.put(AutoProcessor.AUTO_DEPLOY_DIR_PROPERTY, bundleDir);
        }

        String profileName = serverProp.getOrDefault(P_WEASIS_PROFILE, "default"); //$NON-NLS-1$
        serverProp.put(P_WEASIS_PROFILE, profileName);

        // Define the sourceID for the temp and cache directory. The portable version will always have the same
        // sourceID.
        String sourceID =
            toHex((portable == null ? System.getProperty("weasis.codebase.url", "unknown") + profileName : "portable") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hashCode());
        System.setProperty("weasis.source.id", sourceID); //$NON-NLS-1$

        cacheDir = serverProp.get(Constants.FRAMEWORK_STORAGE) + "-" + sourceID; //$NON-NLS-1$
        // If there is a passed in bundle cache directory, then
        // that overwrites anything in the config file.
        serverProp.put(Constants.FRAMEWORK_STORAGE, cacheDir);

        // Load local properties and clean if necessary the previous version
        WeasisLoader loader = loadProperties(serverProp);
        final WeasisFrame mainFrame = loader.getMainFrame();
        final Properties localProp = loader.getLocalProperties();

        // If enabled, register a shutdown hook to make sure the framework is
        // cleanly shutdown when the VM exits.
        handleWebstartHookBug();
        JVMShutdownHook shutdownHook = new JVMShutdownHook();
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        registerAdditionalShutdownHook();

        System.out.println(""); //$NON-NLS-1$
        System.out.println("Starting..."); //$NON-NLS-1$
        System.out.println(""); //$NON-NLS-1$
        System.out.println("| | /| / /__ ___ ____ (_)__"); //$NON-NLS-1$
        System.out.println("| |/ |/ / -_) _ `(_-</ (_-<"); //$NON-NLS-1$
        System.out.println("|__/|__/\\__/\\_,_/___/_/___/"); //$NON-NLS-1$
        System.out.println(""); //$NON-NLS-1$

        int exitStatus = 0;
        try {
            final String goshArgs = System.getProperty("gosh.args", serverProp.get("gosh.args")); //$NON-NLS-1$ //$NON-NLS-2$
            serverProp.put("gosh.args", "--nointeractive --noshutdown"); //$NON-NLS-1$ //$NON-NLS-2$

            // Now create an instance of the framework with our configuration properties.
            m_felix = new Felix(serverProp);
            // Initialize the framework, but don't start it yet.
            m_felix.init();

            // Use the system bundle context to process the auto-deploy
            // and auto-install/auto-start properties.
            loader.setFelix(serverProp, m_felix.getBundleContext());
            loader.writeLabel(
                String.format(Messages.getString("WeasisLauncher.starting"), System.getProperty(P_WEASIS_NAME))); //$NON-NLS-1$
            m_tracker =
                new ServiceTracker(m_felix.getBundleContext(), "org.apache.felix.service.command.CommandProcessor", //$NON-NLS-1$
                    null);
            m_tracker.open();

            // Start the framework.
            m_felix.start();

            // End of splash screen
            loader.close();
            loader = null;

            SwingUtilities.invokeLater(() -> {
                m_tracker.open();
                Object commandSession = getCommandSession(m_tracker.getService());
                if (commandSession != null) {
                    // Start telnet after all other bundles. This will ensure that all the plugins commands are
                    // activated once telnet is available
                    initCommandSession(commandSession, goshArgs);

                    // execute the commands from main argv
                    for (StringBuilder command : commandList) {
                        commandSession_execute(commandSession, command);
                    }
                    commandSession_close(commandSession);
                }

                m_tracker.close();
            });

            String mainUI = serverProp.getOrDefault("weasis.main.ui", ""); //$NON-NLS-1$ //$NON-NLS-2$
            mainUI = mainUI.trim();
            if (!"".equals(mainUI)) { //$NON-NLS-1$
                boolean uiStarted = false;
                for (Bundle b : m_felix.getBundleContext().getBundles()) {
                    if (b.getSymbolicName().equals(mainUI) && b.getState() == Bundle.ACTIVE) {
                        uiStarted = true;
                        break;
                    }
                }
                if (!uiStarted) {
                    throw new IllegalStateException("Main User Interface bundle cannot be started"); //$NON-NLS-1$
                }
            }
            frameworkLoaded = true;

            showMessage(mainFrame, serverProp, localProp);

            // Wait for framework to stop to exit the VM.
            m_felix.waitForStop(0);
            System.exit(0);

        } catch (Throwable ex) {
            exitStatus = -1;
            System.err.println("Cannot not start framework: " + ex); //$NON-NLS-1$
            System.err.println("Weasis cache will be cleaned at next launch."); //$NON-NLS-1$
            System.err.println("State of the framework:"); //$NON-NLS-1$
            for (Bundle b : m_felix.getBundleContext().getBundles()) {
                System.err.println(" * " + b.getSymbolicName() + "-" + b.getVersion().toString() + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + STATE.valueOf(b.getState()));
            }
            resetBundleCache();
        } finally {
            Runtime.getRuntime().halt(exitStatus);
        }
    }

    private static void resetBundleCache() {
        // Set flag to clean cache at next launch
        File sourceIdProps =
            new File(System.getProperty(P_WEASIS_PATH, ""), System.getProperty("weasis.source.id") + ".properties"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        Properties localSourceProp = readProperties(sourceIdProps);
        localSourceProp.setProperty("weasis.clean.cache", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        FileUtil.storeProperties(sourceIdProps, localSourceProp, null);
    }

    private static void showMessage(final WeasisFrame mainFrame, Map<String, String> serverProp,
        final Properties l_prop) {
        String versionOld = serverProp.get("prev." + P_WEASIS_VERSION); //$NON-NLS-1$
        String versionNew = serverProp.get(P_WEASIS_VERSION);
        // First time launch
        if (versionOld == null) {
            String val = getGeneralProperty("weasis.show.disclaimer", "true", serverProp, l_prop, false, false); //$NON-NLS-1$ //$NON-NLS-2$
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
                        l_prop.setProperty("weasis.show.disclaimer", Boolean.FALSE.toString()); //$NON-NLS-1$
                        if (prefDir != null) {
                            FileUtil.storeProperties(new File(prefDir, APP_PROPERTY_FILE), l_prop, null);
                        }
                    } else {
                        File sourceID_props = new File(System.getProperty(P_WEASIS_PATH, ""), //$NON-NLS-1$
                            System.getProperty("weasis.source.id") + ".properties"); //$NON-NLS-1$ //$NON-NLS-2$
                        // delete the properties file to ask again
                        sourceID_props.delete();
                        System.err.println("Refusing the disclaimer"); //$NON-NLS-1$
                        System.exit(-1);
                    }
                });
            }
        } else if (versionNew != null && !versionNew.equals(versionOld)) {
            String val = getGeneralProperty("weasis.show.release", "true", serverProp, l_prop, false, false); //$NON-NLS-1$ //$NON-NLS-2$
            if (Boolean.valueOf(val)) {
                Version vOld = getVersion(versionOld);
                Version vNew = getVersion(versionNew);
                if (vNew.compareTo(vOld) > 0) {

                    String lastTag = l_prop.getProperty("weasis.version.release", null); //$NON-NLS-1$
                    if (lastTag != null) {
                        vOld = getVersion(lastTag);
                        if (vNew.compareTo(vOld) <= 0) {
                            // Message has been already displayed once.
                            return;
                        }
                    }

                    // Can be useful when preferences are store remotely.
                    // The user will see the release message only once.
                    l_prop.setProperty("weasis.version.release", vNew.toString()); //$NON-NLS-1$
                    if (prefDir != null) {
                        FileUtil.storeProperties(new File(prefDir, APP_PROPERTY_FILE), l_prop, null);
                    }

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
                    jTextPane1.addHyperlinkListener(new HyperlinkListener() {
                        @Override
                        public void hyperlinkUpdate(HyperlinkEvent e) {
                            JTextPane pane = (JTextPane) e.getSource();
                            if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
                                pane.setToolTipText(e.getDescription());
                            } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
                                pane.setToolTipText(null);
                            } else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                if (System.getProperty("os.name", "unknown").toLowerCase().startsWith("linux")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                    try {
                                        String cmd = String.format("xdg-open %s", e.getURL()); //$NON-NLS-1$
                                        Runtime.getRuntime().exec(cmd);
                                    } catch (IOException e1) {
                                        System.err.println("Unable to launch the WEB browser"); //$NON-NLS-1$
                                        e1.printStackTrace();
                                    }
                                } else if (Desktop.isDesktopSupported()) {
                                    final Desktop desktop = Desktop.getDesktop();
                                    if (desktop.isSupported(Desktop.Action.BROWSE)) {
                                        try {
                                            desktop.browse(e.getURL().toURI());

                                        } catch (Exception ex) {
                                            System.err.println("Unable to launch the WEB browser"); //$NON-NLS-1$
                                        }
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
            int index = version.indexOf("-"); //$NON-NLS-1$
            v = index > 0 ? version.substring(0, index) : version;
        }
        return new Version(v);
    }

    private static String toHex(int val) {
        final char[] HEX_DIGIT = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
        char[] ch8 = new char[8];
        for (int i = 8; --i >= 0; val >>= 4) {
            ch8[i] = HEX_DIGIT[val & 0xf];
        }
        return String.valueOf(ch8);
    }

    public static List<StringBuilder> splitCommand(String[] args) {
        int length = args.length;
        ArrayList<StringBuilder> list = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            if (args[i].startsWith("$") && args[i].length() > 1) { //$NON-NLS-1$
                StringBuilder command = new StringBuilder(args[i].substring(1));
                // look for parameters
                while (i + 1 < length && !args[i + 1].startsWith("$") && !args[i + 1].startsWith("-VMP")) { //$NON-NLS-1$ //$NON-NLS-2$
                    i++;
                    command.append(" "); //$NON-NLS-1$
                    if (args[i].indexOf(" ") != -1) { //$NON-NLS-1$
                        command.append("\""); //$NON-NLS-1$
                        command.append(args[i]);
                        command.append("\""); //$NON-NLS-1$
                    } else {
                        command.append(args[i]);
                    }
                }
                list.add(command);
            }
        }
        return list;
    }

    public static Object getCommandSession(Object commandProcessor) {
        if (commandProcessor == null) {
            return null;
        }
        Class<?>[] parameterTypes = new Class[] { InputStream.class, OutputStream.class, OutputStream.class };

        // Close stream is not handled but this is a workaround for stackoverflow issue when using System.in...
        Object[] arguments = new Object[] { new FileInputStream(FileDescriptor.in),
            new FileOutputStream(FileDescriptor.out), new FileOutputStream(FileDescriptor.err) };

        try {
            Method nameMethod = commandProcessor.getClass().getMethod("createSession", parameterTypes); //$NON-NLS-1$
            Object commandSession = nameMethod.invoke(commandProcessor, arguments);
            addCommandSessionListener(commandProcessor);
            return commandSession;
        } catch (Exception ex) {
            // Since the services returned by the tracker could become
            // invalid at any moment, we will catch all exceptions, log
            // a message, and then ignore faulty services.
            System.err.println(ex);
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
                            commandSession_execute(args[0], "gogo.option.noglob=on"); //$NON-NLS-1$
                        }
                    }
                    return null;
                }
            });
            nameMethod.invoke(commandProcessor, listener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean initCommandSession(Object commandSession, String args) {
        try {
            // wait for gosh command to be registered
            for (int i = 0; (i < 100) && commandSession_get(commandSession, "gogo:gosh") == null; ++i) { //$NON-NLS-1$
                TimeUnit.MILLISECONDS.sleep(10);
            }

            Class<?>[] parameterTypes = new Class[] { CharSequence.class };
            Object[] arguments = new Object[] { "gogo:gosh --login " + (args == null ? "" : args) }; //$NON-NLS-1$ //$NON-NLS-2$
            Method nameMethod = commandSession.getClass().getMethod("execute", parameterTypes); //$NON-NLS-1$
            nameMethod.invoke(commandSession, arguments);
        } catch (InterruptedException e) {
            // Do not print
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static Object commandSession_get(Object commandSession, String key) {
        if (commandSession == null || key == null) {
            return null;
        }

        Class<?>[] parameterTypes = new Class[] { String.class };
        Object[] arguments = new Object[] { key };

        try {
            Method nameMethod = commandSession.getClass().getMethod("get", parameterTypes); //$NON-NLS-1$
            return nameMethod.invoke(commandSession, arguments);
        } catch (Exception ex) {
            System.err.println(ex);
        }

        return null;
    }

    public static boolean commandSession_close(Object commandSession) {
        if (commandSession == null) {
            return false;
        }
        try {
            Method nameMethod = commandSession.getClass().getMethod("close"); //$NON-NLS-1$
            nameMethod.invoke(commandSession);
            return true;
        } catch (Exception ex) {
            // Since the services returned by the tracker could become
            // invalid at any moment, we will catch all exceptions, log
            // a message, and then ignore faulty services.
            System.err.println(ex);
        }

        return false;
    }

    public static Object commandSession_execute(Object commandSession, CharSequence charSequence) {
        if (commandSession == null) {
            return false;
        }
        Class<?>[] parameterTypes = new Class[] { CharSequence.class };

        Object[] arguments = new Object[] { charSequence };

        try {
            Method nameMethod = commandSession.getClass().getMethod("execute", parameterTypes); //$NON-NLS-1$
            return nameMethod.invoke(commandSession, arguments);
        } catch (Exception ex) {
            // Since the services returned by the tracker could become
            // invalid at any moment, we will catch all exceptions, log
            // a message, and then ignore faulty services.
            ex.printStackTrace();
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
    public static Map<String, String> loadConfigProperties() {
        URI propURI = getPropertiesURI(CONFIG_PROPERTIES_PROP, CONFIG_PROPERTIES_FILE_VALUE);
        // Read the properties file
        Properties props = null;
        if (propURI != null) {
            System.out.println(CONFIG_PROPERTIES_PROP + ": " + propURI); //$NON-NLS-1$
            props = readProperties(propURI, null);
        } else {
            System.err.println("No config.properties path found, Weasis cannot start!"); //$NON-NLS-1$
        }

        propURI = getPropertiesURI(EXTENDED_PROPERTIES_PROP, EXTENDED_PROPERTIES_FILE_VALUE);
        if (propURI != null) {
            System.out.println(EXTENDED_PROPERTIES_PROP + ": " + propURI); //$NON-NLS-1$
            // Extended properties, add or override existing properties
            props = readProperties(propURI, props);
        }

        // Only required for dev purposes (running the app in IDE)
        String mvnRepo = System.getProperty("maven.localRepository", props.getProperty("maven.local.repo")); //$NON-NLS-1$ //$NON-NLS-2$
        if (mvnRepo != null) {
            System.setProperty("maven.localRepository", mvnRepo.replace("\\", "/")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        // Perform variable substitution for system properties and
        // convert to dictionary.
        Map<String, String> map = new HashMap<>();
        if (props != null) {
            for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                map.put(name, Util.substVars(props.getProperty(name), name, null, props));
            }
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
                System.err.print(configProp + ": " + e); //$NON-NLS-1$
                return null;
            }
        } else {
            // Development folder only
            File confDir = new File(System.getProperty("user.dir") + File.separator + "target", CONFIG_DIRECTORY); //$NON-NLS-1$ //$NON-NLS-2$
            if (!confDir.canRead()) {
                confDir = null;
            }

            if (confDir == null) {
                // Determine where the configuration directory is by figuring
                // out where felix.jar is located on the system class path.
                String classpath = System.getProperty("java.class.path"); //$NON-NLS-1$
                int index = classpath.toLowerCase().indexOf("felix.jar"); //$NON-NLS-1$
                int start = classpath.lastIndexOf(File.pathSeparator, index) + 1;
                if (index >= start) {
                    // Get the path of the felix.jar file.
                    String jarLocation = classpath.substring(start, index);
                    // Calculate the conf directory based on the parent
                    // directory of the felix.jar directory.
                    confDir = new File(new File(new File(jarLocation).getAbsolutePath()).getParent(), CONFIG_DIRECTORY);
                } else {
                    // Can't figure it out so use the current directory as default.
                    confDir = new File(System.getProperty("user.dir"), CONFIG_DIRECTORY); //$NON-NLS-1$
                }
            }

            try {
                propURL = new File(confDir, configFile).toURI();
            } catch (Exception ex) {
                System.err.print(configFile + ": " + ex); //$NON-NLS-1$
                return null;
            }
        }
        return propURL;
    }

    public static Properties readProperties(URI propURI, Properties props) {
        // Read the properties file.
        if (props == null) {
            props = new Properties();
        }
        InputStream is = null;
        try {
            // Try to load config.properties.
            is = FileUtil.getAdaptedConnection(propURI.toURL()).getInputStream();
            props.load(is);
            is.close();
        } catch (Exception ex) {
            System.err.println("Cannot read properties file: " + propURI); //$NON-NLS-1$
            FileUtil.safeClose(is);
            return props;
        }
        return props;
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
        System.out.println(key + ": " + value); //$NON-NLS-1$
        return value;
    }

    public static void setSystemSpecification() {
        // Follows the OSGI specification to use Bundle-NativeCode in the bundle fragment :
        // http://www.osgi.org/Specifications/Reference
        String osName = System.getProperty("os.name"); //$NON-NLS-1$
        String osArch = System.getProperty("os.arch"); //$NON-NLS-1$
        if (osName != null && !osName.trim().equals("") && osArch != null && !osArch.trim().equals("")) { //$NON-NLS-1$ //$NON-NLS-2$
            if (osName.toLowerCase().startsWith("win")) { //$NON-NLS-1$
                // All Windows versions with a specific processor architecture (x86 or x86-64) are grouped under
                // windows. If you need to make different native libraries for the Windows versions, define it in the
                // Bundle-NativeCode tag of the bundle fragment.
                osName = "windows"; //$NON-NLS-1$
            } else if (osName.equals("Mac OS X")) { //$NON-NLS-1$
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
            System.setProperty("native.library.spec", osName + "-" + osArch); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    public static WeasisLoader loadProperties(Map<String, String> serverProp) {
        System.out.println("Operating system: " + System.getProperty("native.library.spec")); //$NON-NLS-1$ //$NON-NLS-2$

        String dir = new File(serverProp.get(Constants.FRAMEWORK_STORAGE)).getParent();
        System.setProperty(P_WEASIS_PATH, dir);

        String weasisName = serverProp.getOrDefault(P_WEASIS_NAME, "Weasis");//$NON-NLS-1$
        System.setProperty(P_WEASIS_NAME, weasisName);

        String profileName = serverProp.getOrDefault(P_WEASIS_PROFILE, "default"); //$NON-NLS-1$
        System.setProperty(P_WEASIS_PROFILE, profileName);

        String user = System.getProperty("weasis.user", null); //$NON-NLS-1$
        boolean localSessionUser = user == null;
        if (user == null) {
            user = System.getProperty("user.name", "local"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        System.setProperty("weasis.user", user); //$NON-NLS-1$
        StringBuilder bufDir = new StringBuilder(dir);
        bufDir.append(File.separator);
        bufDir.append("preferences"); //$NON-NLS-1$
        bufDir.append(File.separator);
        bufDir.append(user);
        bufDir.append(File.separator);
        bufDir.append(profileName);
        prefDir = new File(bufDir.toString());
        try {
            prefDir.mkdirs();
        } catch (Exception e) {
            prefDir = new File(dir);
            e.printStackTrace();
        }
        System.out.println("Preferences directory: " + prefDir.getPath()); //$NON-NLS-1$

        if (REMOTE_PREFS == null && user != null) {
            ServiceLoader<RemotePreferences> prefs = ServiceLoader.load(RemotePreferences.class);
            Iterator<RemotePreferences> commandsIterator = prefs.iterator();
            while (commandsIterator.hasNext()) {
                REMOTE_PREFS = commandsIterator.next();
                REMOTE_PREFS.initialize(user, localSessionUser, profileName, bufDir.toString());
                System.out.println("Loading remote preferences for : " + user); //$NON-NLS-1$
                break;
            }
        }
        if (REMOTE_PREFS != null) {
            try {
                REMOTE_PREFS.read();
            } catch (Exception e) {
                System.out.println("Cannot read preferences remotely: " + e.getMessage()); //$NON-NLS-1$
            }
        }

        String portable = System.getProperty("weasis.portable.dir"); //$NON-NLS-1$
        if (portable != null) {
            System.out.println("Starting portable version"); //$NON-NLS-1$
            System.setProperty("weasis.portable.dicom.directory", //$NON-NLS-1$
                serverProp.get("weasis.portable.dicom.directory")); //$NON-NLS-1$
        }

        File profileProps = new File(prefDir, APP_PROPERTY_FILE);
        Properties lProp = readProperties(profileProps);
        // General Preferences priority order:
        // 1) Last value (does not exist for first launch of Weasis in an operating system session).
        // 2) Java System property
        // 3) Property defined in weasis/conf/config.properties or in ext-config.properties (extension of config)
        // 4) default value

        final String lang = getGeneralProperty("locale.lang.code", "en", serverProp, lProp, true, false); //$NON-NLS-1$ //$NON-NLS-2$
        getGeneralProperty("locale.format.code", "system", serverProp, lProp, true, false); //$NON-NLS-1$ //$NON-NLS-2$

        // Set value back to the bundle context properties, sling logger uses bundleContext.getProperty(prop)
        getGeneralProperty("org.apache.sling.commons.log.level", "INFO", serverProp, lProp, true, true); //$NON-NLS-1$ //$NON-NLS-2$
        // Empty string make the file log writer disable
        String logActivatation =
            getGeneralProperty("org.apache.sling.commons.log.file.activate", "false", serverProp, lProp, true, true); //$NON-NLS-1$ //$NON-NLS-2$
        if ("true".equalsIgnoreCase(logActivatation)) { //$NON-NLS-1$
            String logFile = dir + File.separator + "log" + File.separator + "default.log"; //$NON-NLS-1$ //$NON-NLS-2$
            serverProp.put("org.apache.sling.commons.log.file", logFile); //$NON-NLS-1$
            lProp.remove("org.apache.sling.commons.log.file"); //$NON-NLS-1$
        }

        getGeneralProperty("org.apache.sling.commons.log.file.number", "5", serverProp, lProp, true, true); //$NON-NLS-1$ //$NON-NLS-2$
        getGeneralProperty("org.apache.sling.commons.log.file.size", "10MB", serverProp, lProp, true, true); //$NON-NLS-1$ //$NON-NLS-2$
        getGeneralProperty("org.apache.sling.commons.log.stack.limit", "3", serverProp, lProp, true, true); //$NON-NLS-1$ //$NON-NLS-2$
        getGeneralProperty("org.apache.sling.commons.log.pattern", //$NON-NLS-1$
            "{0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4}* [{2}] {3}: {5}", serverProp, lProp, false, true); //$NON-NLS-1$

        URI translationModules = null;
        if (portable != null) {
            File file = new File(portable, "weasis/bundle-i18n/buildNumber.properties"); //$NON-NLS-1$
            if (file.canRead()) {
                translationModules = file.toURI();
                String path = file.getParentFile().toURI().toString();
                System.setProperty("weasis.i18n", path); //$NON-NLS-1$
                System.out.println("i18n path: " + path); //$NON-NLS-1$
            }
        } else {
            String path = System.getProperty("weasis.i18n", null); //$NON-NLS-1$
            if (path != null) {
                path += path.endsWith("/") ? "buildNumber.properties" : "/buildNumber.properties"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                try {
                    translationModules = new URI(path);
                } catch (URISyntaxException e) {
                    System.err.println("Cannot find translation modules: " + e); //$NON-NLS-1$
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
        String sysSpec = System.getProperty("native.library.spec", "unknown"); //$NON-NLS-1$ //$NON-NLS-2$
        int index = sysSpec.indexOf("-"); //$NON-NLS-1$
        if (index > 0) {
            nativeLook = "weasis.look." + sysSpec.substring(0, index); //$NON-NLS-1$
            look = System.getProperty(nativeLook, null);
            if (look == null) {
                look = serverProp.get(nativeLook);
            }

        }
        if (look == null) {
            look = System.getProperty("weasis.look", null); //$NON-NLS-1$
            if (look == null) {
                look = serverProp.get("weasis.look"); //$NON-NLS-1$
            }
        }

        String localLook = lProp.getProperty("weasis.look", null); //$NON-NLS-1$
        // installSubstanceLookAndFeels must be the first condition to install substance if necessary
        if (LookAndFeels.installSubstanceLookAndFeels() && look == null) {
            if ("Mac OS X".equals(System.getProperty("os.name"))) { //$NON-NLS-1$ //$NON-NLS-2$
                look = "com.apple.laf.AquaLookAndFeel"; //$NON-NLS-1$
            } else {
                look = "org.pushingpixels.substance.api.skin.SubstanceTwilightLookAndFeel"; //$NON-NLS-1$
            }
        }
        // Set the default value for L&F
        if (look == null) {
            look = getAvailableLookAndFeel(look);
        }
        serverProp.put("weasis.look", look); //$NON-NLS-1$

        // If look is in local prefs, use it
        if (localLook != null) {
            look = localLook;
        }

        /*
         * Build a Frame or catch it from JApplet
         *
         * This will ensure the popup message or other dialogs to have frame parent. When the parent is null the dialog
         * can be hidden under the main frame
         */
        final WeasisFrame mainFrame = new WeasisFrame();

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

                Object instance = null;
                MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                try {
                    ObjectName objectName1 = ObjectName.getInstance("weasis:name=MainWindow"); //$NON-NLS-1$
                    // Try to get frame from an Applet
                    instance = server.getAttribute(objectName1, "RootPaneContainer"); //$NON-NLS-1$
                    if (instance instanceof RootPaneContainer) {
                        mainFrame.setRootPaneContainer((RootPaneContainer) instance);
                    }
                } catch (InstanceNotFoundException e2) {

                } catch (Exception e3) {
                    // ignored
                } finally {
                    try {
                        if (instance == null) {
                            // Build a JFrame which will be used later in base.ui module
                            ObjectName objectName2 = new ObjectName("weasis:name=MainWindow"); //$NON-NLS-1$
                            mainFrame.setRootPaneContainer(new JFrame());
                            server.registerMBean(mainFrame, objectName2);
                        }
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("WARNING : Unable to set the Look&Feel " + look); //$NON-NLS-1$
            e.printStackTrace();
        }
        lProp.put("weasis.look", look); //$NON-NLS-1$
        System.out.println("weasis.look: " + look); //$NON-NLS-1$

        File sourceID_props = new File(dir, System.getProperty("weasis.source.id") + ".properties"); //$NON-NLS-1$ //$NON-NLS-2$
        Properties localSourceProp = readProperties(sourceID_props);

        String versionOld = localSourceProp.getProperty(P_WEASIS_VERSION);
        System.out.println("Last running version: " + versionOld); //$NON-NLS-1$
        if (versionOld != null) {
            serverProp.put("prev." + P_WEASIS_VERSION, versionOld); //$NON-NLS-1$
        }
        String versionNew = serverProp.get(P_WEASIS_VERSION);
        System.out.println("Current version: " + versionNew); //$NON-NLS-1$
        String cleanCacheAfterCrash = localSourceProp.getProperty("weasis.clean.cache"); //$NON-NLS-1$

        boolean update = false;
        // Loads the resource files
        String resPath = serverProp.getOrDefault("weasis.resources.url", //$NON-NLS-1$
            System.getProperty("weasis.codebase.url", "") + "/resources.zip"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        File cacheDir = null;
        try {
            if (resPath.endsWith(".zip")) { //$NON-NLS-1$
                cacheDir =
                    new File(dir + File.separator + "data" + File.separator + System.getProperty("weasis.source.id"), //$NON-NLS-1$ //$NON-NLS-2$
                        "resources"); //$NON-NLS-1$
                String date =
                    FileUtil.writeResources(resPath, cacheDir, localSourceProp.getProperty(P_WEASIS_RES_DATE));
                if (date != null) {
                    update = true;
                    localSourceProp.put(P_WEASIS_RES_DATE, date);
                }
            }
        } catch (Exception e) {
            cacheDir = null;
            System.err.println(e.getMessage() + "\n"); //$NON-NLS-1$
        }
        if (cacheDir == null) {
            if (portable != null) {
                cacheDir = new File(portable, "weasis" + File.separator + "resources"); //$NON-NLS-1$ //$NON-NLS-2$
            } else {
                File f = new File(System.getProperty("user.dir")); //$NON-NLS-1$
                cacheDir = new File(f.getParent(), "weasis-distributions" + File.separator + "resources"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        System.out.println("weasis.resources.path: " + cacheDir.getPath()); //$NON-NLS-1$
        serverProp.put("weasis.resources.path", cacheDir.getPath()); //$NON-NLS-1$

        // Splash screen that shows bundles loading
        final WeasisLoader loader = new WeasisLoader(cacheDir, mainFrame, lProp);
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
        FileUtil.storeProperties(profileProps, lProp, null);

        // Clean cache if Weasis has crashed during the previous launch
        boolean cleanCache = Boolean.parseBoolean(serverProp.get("weasis.clean.previous.version")); //$NON-NLS-1$
        if (cleanCacheAfterCrash != null && "true".equals(cleanCacheAfterCrash)) { //$NON-NLS-1$
            serverProp.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
            localSourceProp.remove("weasis.clean.cache"); //$NON-NLS-1$
            update = true;
            System.out.println("Clean plug-in cache because Weasis has crashed during the previous launch"); //$NON-NLS-1$
        }
        // Clean cache when version has changed
        else if (cleanCache && versionNew != null) {
            if (!versionNew.equals(versionOld)) {
                System.out.println(String.format("Clean previous Weasis version: %s", versionOld)); //$NON-NLS-1$
                serverProp.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
                System.out.println("Clean plug-in cache because the version has changed"); //$NON-NLS-1$
            }
        }

        if (update) {
            FileUtil.storeProperties(sourceID_props, localSourceProp, null);
        }
        System.out.println("***** End of Configuration *****"); //$NON-NLS-1$
        return loader;
    }

    private static Properties readProperties(File propsFile) {
        Properties properties = new Properties();

        if (propsFile.canRead()) {
            try (FileInputStream fis = new FileInputStream(propsFile)) {
                properties.load(fis);
            } catch (Exception e) {
            }
        } else {
            File appFoler = new File(System.getProperty(P_WEASIS_PATH, "")); //$NON-NLS-1$
            appFoler.mkdirs();
        }
        return properties;
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
            System.err.println("WARNING : Unable to set the Look&Feel"); //$NON-NLS-1$
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
            if ("Mac OS X".equals(System.getProperty("os.name"))) { //$NON-NLS-1$ //$NON-NLS-2$
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
        if (value == null || value.trim().equals("")) { //$NON-NLS-1$
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

    private static void registerAdditionalShutdownHook() {
        try {
            Class.forName("sun.misc.Signal"); //$NON-NLS-1$
            Class.forName("sun.misc.SignalHandler"); //$NON-NLS-1$
            sun.misc.Signal.handle(new sun.misc.Signal("TERM"), signal -> shutdownHook()); //$NON-NLS-1$
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("Cannot find sun.misc.Signal for shutdown hook exstension"); //$NON-NLS-1$
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
        int major = getJavaMajorVersion();
        if (major < 9) {
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
                e.printStackTrace();
            }
        }
    }

    private static class JVMShutdownHook extends Thread {
        @Override
        public void run() {
            shutdownHook();
        }
    }

    private static void shutdownHook() {
        try {
            if (m_felix != null) {
                m_felix.stop();
                // wait asynchronous stop (max 30 seconds to stop all bundles)
                m_felix.waitForStop(30000);
            }
        } catch (Exception ex) {
            System.err.println("Error stopping framework: " + ex); //$NON-NLS-1$
        } finally {
            storeRemotePreferences();
            cleanImageCache();

            // If System.exit() hangs call Runtime.getRuntime().halt(1) to kill the application
            Timer timer = new Timer();
            timer.schedule(new HaltTask(), 15000);
        }
    }

    static void storeRemotePreferences() {
        // After all bundles has been stopped, we can copy the preferences
        if (REMOTE_PREFS != null) {
            try {
                REMOTE_PREFS.store();
                System.out.println("End of storing remote preferences."); //$NON-NLS-1$
            } catch (Throwable e) { // should garanty the clean cache after
                System.out.println("Cannot store preferences remotely: " + e.getMessage()); //$NON-NLS-1$
            }
        }
    }

    static void cleanImageCache() {
        // Clean temp folder.
        String dir = System.getProperty("weasis.tmp.dir"); //$NON-NLS-1$
        if (dir != null) {
            FileUtil.deleteDirectoryContents(new File(dir), 3, 0);
        }
    }
}
