/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.launcher;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ServiceLoader;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.plaf.basic.BasicColorChooserUI;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.Util;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.util.tracker.ServiceTracker;

public class WeasisLauncher {

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

    private static HostActivator m_activator = null;
    private static Felix m_felix = null;
    static ServiceTracker m_tracker = null;

    private static String APP_PROPERTY_FILE = "weasis.properties"; //$NON-NLS-1$
    public static final String P_WEASIS_VERSION = "weasis.version"; //$NON-NLS-1$
    public static final String P_WEASIS_PATH = "weasis.path"; //$NON-NLS-1$
    static Properties modulesi18n = null;
    private static String look = null;

    private static RemotePreferences REMOTE_PREFS;

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
     * @param args
     *            Accepts arguments to set the auto-deploy directory and/or the bundle cache directory.
     * @throws Exception
     *             If an error occurs.
     **/
    public static void main(String[] argv) throws Exception {
        launch(argv);
    }

    public static void launch(String[] argv) throws Exception {
        // Set system property for dynamically loading only native libraries corresponding of the current platform
        setSystemSpecification();

        // Getting VM arguments, workaround for having a fully trusted application with JWS,
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6653241
        for (int i = 0; i < argv.length; i++) {
            if (argv[i].startsWith("-VMP") && argv[i].length() > 4) { //$NON-NLS-1$
                String[] vmarg = argv[i].substring(4).split("=", 2);
                if (vmarg.length == 2) {
                    if (vmarg[1].startsWith("\"") && vmarg[1].endsWith("\"")) {
                        vmarg[1] = vmarg[1].substring(1, vmarg[1].length() - 1);
                    }
                    System.setProperty(vmarg[0], vmarg[1]);
                }
            }
        }

        final List<StringBuffer> commandList = splitCommand(argv);
        // Look for bundle directory and/or cache directory.
        // We support at most one argument, which is the bundle
        // cache directory.
        String bundleDir = null;
        String cacheDir = null;
        for (StringBuffer c : commandList) {
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
                System.setProperty(CONFIG_PROPERTIES_PROP, baseURL + CONFIG_PROPERTIES_FILE_VALUE); //$NON-NLS-1$
                System.setProperty(EXTENDED_PROPERTIES_PROP, baseURL + EXTENDED_PROPERTIES_FILE_VALUE); //$NON-NLS-1$
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Read configuration properties.
        Properties configProps = WeasisLauncher.loadConfigProperties();
        // If no configuration properties were found, then create
        // an empty properties object.
        if (configProps == null) {
            System.err.println("No " + CONFIG_PROPERTIES_FILE_VALUE + " found."); //$NON-NLS-1$ //$NON-NLS-2$
            configProps = new Properties();
        }

        // If there is a passed in bundle auto-deploy directory, then
        // that overwrites anything in the config file.
        if (bundleDir != null) {
            configProps.setProperty(AutoProcessor.AUTO_DEPLOY_DIR_PROPERY, bundleDir);
        }

        // If there is a passed in bundle cache directory, then
        // that overwrites anything in the config file.
        if (cacheDir != null) {
            configProps.setProperty(Constants.FRAMEWORK_STORAGE, cacheDir);
        }

        // Load local properties and clean if necessary the previous version
        WebStartLoader loader = loadProperties(configProps);

        // If enabled, register a shutdown hook to make sure the framework is
        // cleanly shutdown when the VM exits.
        Runtime.getRuntime().addShutdownHook(new Thread("Felix Shutdown Hook") { //$NON-NLS-1$

                @Override
                public void run() {
                    int exitStatus = 0;
                    try {
                        if (m_felix != null) {
                            m_felix.stop();
                            // wait asynchronous stop (max 20 seconds to stop all bundles)
                            m_felix.waitForStop(20000);
                        }
                    } catch (Exception ex) {
                        exitStatus = -1;
                        System.err.println("Error stopping framework: " + ex); //$NON-NLS-1$
                    } finally {
                        // After all bundles has been stopped, we can copy the preferences
                        if (REMOTE_PREFS != null) {
                            REMOTE_PREFS.store();
                        }
                        // Clean temp folder.
                        FileUtil.deleteDirectoryContents(FileUtil.getApplicationTempDir());
                        Runtime.getRuntime().halt(exitStatus);
                    }
                }
            });

        System.out.println("\nWeasis Starting..."); //$NON-NLS-1$
        System.out.println("========================\n"); //$NON-NLS-1$
        int exitStatus = 0;
        // Create host activator;
        m_activator = new HostActivator();

        List list = new ArrayList();
        list.add(m_activator);
        configProps.put(FelixConstants.SYSTEMBUNDLE_ACTIVATORS_PROP, list);

        try {
            // Now create an instance of the framework with our configuration properties.
            m_felix = new Felix(configProps);
            // Initialize the framework, but don't start it yet.
            m_felix.init();

            // Use the system bundle context to process the auto-deploy
            // and auto-install/auto-start properties.
            loader.setFelix(configProps, m_activator.getBundleContext());
            loader.writeLabel("Starting... Weasis"); //$NON-NLS-1$
            m_tracker =
                new ServiceTracker(m_activator.getBundleContext(), "org.apache.felix.service.command.CommandProcessor", //$NON-NLS-1$
                    null);
            m_tracker.open();

            // Start the framework.
            m_felix.start();

            // End of splash screen
            loader.close();
            loader = null;
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    Object commandSession = getCommandSession(m_tracker.getService());
                    if (commandSession != null) {
                        // execute the commands from main argv
                        for (StringBuffer command : commandList) {
                            commandSession_execute(commandSession, command);
                        }
                        commandSession_close(commandSession);
                    }

                    m_tracker.close();
                }
            });

            String mainUI = configProps.getProperty("weasis.main.ui", ""); //$NON-NLS-1$ //$NON-NLS-2$
            mainUI = mainUI.trim();
            if (!mainUI.equals("")) { //$NON-NLS-1$
                boolean uiStarted = false;
                for (Bundle b : m_felix.getBundleContext().getBundles()) {
                    if (b.getSymbolicName().equals(mainUI)) { //$NON-NLS-1$
                        uiStarted = true;
                        break;
                    }
                }
                if (!uiStarted) {
                    throw new Exception("Main User Interface bundle cannot be started"); //$NON-NLS-1$
                }
            }

            // Wait for framework to stop to exit the VM.
            m_felix.waitForStop(0);
            System.exit(0);

        } catch (Exception ex) {
            exitStatus = -1;
            System.err.println("Could not create framework: " + ex); //$NON-NLS-1$
            ex.printStackTrace();
        } finally {
            Runtime.getRuntime().halt(exitStatus);
        }
    }

    public Bundle[] getInstalledBundles() {
        // Use the system bundle activator to gain external
        // access to the set of installed bundles.
        return m_activator.getBundles();
    }

    public static List<StringBuffer> splitCommand(String[] args) {
        int length = args.length;
        ArrayList<StringBuffer> list = new ArrayList<StringBuffer>(5);
        for (int i = 0; i < length; i++) {
            if (args[i].startsWith("$") && args[i].length() > 1) { //$NON-NLS-1$
                StringBuffer command = new StringBuffer(args[i].substring(1));
                // look for parameters
                while (i + 1 < length && !args[i + 1].startsWith("$")) { //$NON-NLS-1$
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
        // for (StringBuffer stringBuffer : list) {
        // System.out.println("Command:" + stringBuffer);
        // }
        return list;
    }

    public static Object getCommandSession(Object commandProcessor) {
        if (commandProcessor == null) {
            return null;
        }
        Class[] parameterTypes = new Class[] { InputStream.class, PrintStream.class, PrintStream.class };

        Object[] arguments = new Object[] { System.in, System.out, System.err };

        try {
            Method nameMethod = commandProcessor.getClass().getMethod("createSession", parameterTypes); //$NON-NLS-1$
            Object commandSession = nameMethod.invoke(commandProcessor, arguments);
            return commandSession;
        } catch (Exception ex) {
            // Since the services returned by the tracker could become
            // invalid at any moment, we will catch all exceptions, log
            // a message, and then ignore faulty services.
            System.err.println(ex);
        }

        return null;
    }

    public static boolean commandSession_close(Object commandSession) {
        if (commandSession == null) {
            return false;
        }
        try {
            Method nameMethod = commandSession.getClass().getMethod("close", null); //$NON-NLS-1$
            nameMethod.invoke(commandSession, null);
            return true;
        } catch (Exception ex) {
            // Since the services returned by the tracker could become
            // invalid at any moment, we will catch all exceptions, log
            // a message, and then ignore faulty services.
            System.err.println(ex);
        }

        return false;
    }

    public static boolean commandSession_execute(Object commandSession, CharSequence charSequence) {
        if (commandSession == null) {
            return false;
        }
        Class[] parameterTypes = new Class[] { CharSequence.class };

        Object[] arguments = new Object[] { charSequence };

        try {
            Method nameMethod = commandSession.getClass().getMethod("execute", parameterTypes); //$NON-NLS-1$
            nameMethod.invoke(commandSession, arguments);
            return true;
        } catch (Exception ex) {
            // Since the services returned by the tracker could become
            // invalid at any moment, we will catch all exceptions, log
            // a message, and then ignore faulty services.
            System.err.println(ex);
            ex.printStackTrace();
        }

        return false;
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
    public static Properties loadConfigProperties() {
        URI propURI = getPropertiesURI(CONFIG_PROPERTIES_PROP, CONFIG_PROPERTIES_FILE_VALUE);
        // Read the properties file
        Properties props = null;
        if (propURI != null) {
            props = readProperties(propURI, null);
        }
        propURI = getPropertiesURI(EXTENDED_PROPERTIES_PROP, EXTENDED_PROPERTIES_FILE_VALUE);
        if (propURI != null) {
            // Extended properties, add or override existing properties
            props = readProperties(propURI, props);
        }
        if (props != null) {
            // Perform variable substitution for system properties.
            for (Enumeration e = props.propertyNames(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                props.setProperty(name, Util.substVars(props.getProperty(name), name, null, props));
            }
        }
        return props;
    }

    public static URI getPropertiesURI(String configProp, String configFile) {

        // The config properties file is either specified by a system
        // property or it is in the conf/ directory of the Felix
        // installation directory. Try to load it from one of these
        // places.

        // See if the property URL was specified as a property.
        URI propURL = null;
        String custom = System.getProperty(configProp);
        if (custom != null) {
            try {
                propURL = new URI(custom);
            } catch (URISyntaxException e) {
                System.err.print(configProp + ": " + e); //$NON-NLS-1$
                return null;
            }
        } else {
            // Determine where the configuration directory is by figuring
            // out where felix.jar is located on the system class path.
            File confDir = null;
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
            is = propURI.toURL().openConnection().getInputStream();
            props.load(is);
            is.close();
        } catch (Exception ex) {
            System.err.println("Cannot read properties file: " + propURI); //$NON-NLS-1$
            FileUtil.safeClose(is);
            return props;
        }
        return props;
    }

    private static String getGeneralProperty(String key, String defaultValue, Properties config, Properties local) {
        return getGeneralProperty(key, key, defaultValue, config, local);
    }

    private static String getGeneralProperty(String key, String localKey, String defaultValue, Properties config,
        Properties local) {
        String value = local.getProperty(localKey, null);
        if (value == null) {
            value = System.getProperty(key, null);
            if (value == null) {
                value = config.getProperty(key, defaultValue);
            }
            // When first launch, set property that can be writed later
            local.setProperty(localKey, value);
        }
        return value;
    }

    public static void setSystemSpecification() {
        // Follows the OSGI specification to use Bundle-NativeCode in the bundle fragment :
        // http://www.osgi.org/Specifications/Reference
        String osName = System.getProperty("os.name"); //$NON-NLS-1$
        String osArch = System.getProperty("os.arch"); //$NON-NLS-1$
        if (osName != null && !osName.trim().equals("") && osArch != null && !osArch.trim().equals("")) { //$NON-NLS-1$ //$NON-NLS-2$
            if (osName.startsWith("Win")) { //$NON-NLS-1$
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

    public static WebStartLoader loadProperties(Properties config) {
        String dir = new File(config.getProperty(Constants.FRAMEWORK_STORAGE)).getParent();
        System.setProperty(P_WEASIS_PATH, dir);
        String user = System.getProperty("weasis.user", null); //$NON-NLS-1$
        if (REMOTE_PREFS == null && user != null) {
            ServiceLoader<RemotePreferences> prefs = ServiceLoader.load(RemotePreferences.class);
            Iterator<RemotePreferences> commandsIterator = prefs.iterator();
            while (commandsIterator.hasNext()) {
                REMOTE_PREFS = commandsIterator.next();
                REMOTE_PREFS.initialize(user, dir + File.separator + "preferences" + File.separator + user); //$NON-NLS-1$
                break;
            }
        }
        if (REMOTE_PREFS != null) {
            REMOTE_PREFS.read();
        }

        String portable = System.getProperty("weasis.portable.dir"); //$NON-NLS-1$
        if (portable != null) {
            System
                .setProperty("weasis.portable.dicom.directory", config.getProperty("weasis.portable.dicom.directory")); //$NON-NLS-1$ //$NON-NLS-2$
        }

        File basdir;
        if (user == null) {
            basdir = new File(dir);
        } else {
            basdir = new File(dir + File.separator + "preferences" + File.separator //$NON-NLS-1$
                + user);
            try {
                basdir.mkdirs();
            } catch (Exception e) {
                basdir = new File(dir);
                e.printStackTrace();
            }
        }
        File common_file = new File(basdir, APP_PROPERTY_FILE);
        Properties s_prop = readProperties(common_file);
        // General Preferences priority order:
        // 1) Last value (does not exist for first launch of Weasis in an operating system session).
        // 2) Java System property
        // 3) Property defined in weasis/conf/config.properties or in weasis/conf/ext-config.properties (extension of
        // config)
        // 4) default value

        final String lang = getGeneralProperty("weasis.language", "locale.language", "en", config, s_prop); //$NON-NLS-1$ //$NON-NLS-2$
        final String country = getGeneralProperty("weasis.country", "locale.country", "US", config, s_prop); //$NON-NLS-1$ //$NON-NLS-2$
        final String variant = getGeneralProperty("weasis.variant", "locale.variant", "", config, s_prop); //$NON-NLS-1$ //$NON-NLS-2$

        getGeneralProperty("weasis.confirm.closing", "true", config, s_prop); //$NON-NLS-1$ //$NON-NLS-2$

        URI translation_modules = null;
        if (portable != null) {
            File file = new File(portable, "weasis/bundle-i18n/buildNumber.properties"); //$NON-NLS-1$
            if (file.canRead()) {
                translation_modules = file.toURI();
                String path = file.getParentFile().toURI().toString();
                System.setProperty("weasis.i18n", path); //$NON-NLS-1$
                System.out.println("i18n path: " + path); //$NON-NLS-1$
            }
        } else {
            String path = System.getProperty("weasis.i18n", null); //$NON-NLS-1$
            if (path != null) {
                path += path.endsWith("/") ? "buildNumber.properties" : "/buildNumber.properties"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                try {
                    translation_modules = new URI(path);
                } catch (URISyntaxException e) {
                    System.err.println("Cannot find translation modules: " + e); //$NON-NLS-1$
                }
            }
        }
        if (translation_modules != null) {
            modulesi18n = readProperties(translation_modules, null);
            if (modulesi18n != null) {
                System.setProperty("weasis.languages", modulesi18n.getProperty("languages", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }
        if (lang.equals("en")) { //$NON-NLS-1$
            // if English no need to load i18n bundle fragments
            modulesi18n = null;
        }
        Locale.setDefault(new Locale(lang, country, variant));

        look = s_prop.getProperty("weasis.look", null); //$NON-NLS-1$
        if (look == null) {
            String nativeLook = null;
            String sys_spec = System.getProperty("native.library.spec", "unknown"); //$NON-NLS-1$ //$NON-NLS-2$
            int index = sys_spec.indexOf("-"); //$NON-NLS-1$
            if (index > 0) {
                nativeLook = "weasis.look." + sys_spec.substring(0, index); //$NON-NLS-1$
                look = System.getProperty(nativeLook, null);
                if (look == null) {
                    look = config.getProperty(nativeLook, null);
                }

            }
            if (look == null) {
                look = System.getProperty("weasis.look", null);
                if (look == null) {
                    look = config.getProperty(nativeLook, null);
                }
            }
        }
        if (LookAndFeels.installSubstanceLookAndFeels() && look == null) {
            if ("Mac OS X".equals(System.getProperty("os.name"))) { //$NON-NLS-1$ //$NON-NLS-2$
                look = "com.apple.laf.AquaLookAndFeel"; //$NON-NLS-1$
            } else {
                look = "org.pushingpixels.substance.api.skin.SubstanceTwilightLookAndFeel"; //$NON-NLS-1$
            }
        }
        // Set look and feels
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    if (look.startsWith("org.pushingpixels")) { //$NON-NLS-1$
                        JFrame.setDefaultLookAndFeelDecorated(true);
                        JDialog.setDefaultLookAndFeelDecorated(true);
                    }
                    look = setLookAndFeel(look);
                }
            });
        } catch (Exception e) {
            System.err.println("WARNING : Unable to set the Look&Feel " + look); //$NON-NLS-1$
            e.printStackTrace();
        }
        s_prop.put("weasis.look", look);

        Properties common_prop;
        if (basdir.getPath().equals(dir)) {
            common_prop = s_prop;
        } else {
            FileUtil.storeProperties(common_file, s_prop, null);
            common_file = new File(dir, APP_PROPERTY_FILE);
            common_prop = readProperties(common_file);
        }

        String versionOld = common_prop.getProperty("weasis.version"); //$NON-NLS-1$
        String versionNew = config.getProperty("weasis.version"); //$NON-NLS-1$

        // Splash screen that shows bundles loading
        final WebStartLoader loader = new WebStartLoader();
        // Display splash screen
        loader.open();

        boolean update = false;
        if (versionNew != null) {
            // Add also to java properties for the about
            System.setProperty(P_WEASIS_VERSION, versionNew);
            common_prop.put(P_WEASIS_VERSION, versionNew);
            if (versionOld == null || !versionOld.equals(versionNew)) {
                update = true;
            }
        }
        if (update) {
            common_prop.put("weasis.look", look); //$NON-NLS-1$
            FileUtil.storeProperties(common_file, common_prop, null);
        }

        boolean cleanCache = Boolean.parseBoolean(config.getProperty("weasis.clean.previous.version")); //$NON-NLS-1$
        // Save if not exist or could not be read
        if (cleanCache && versionNew != null) {
            if (!versionNew.equals(versionOld)) {
                System.out.printf("Clean previous Weasis version: %s \n", versionOld); //$NON-NLS-1$
                config.setProperty(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
            }
        }
        final File file = common_file;
        // Test if it is the first time launch
        if (versionOld == null) {
            EventQueue.invokeLater(new Runnable() {

                @Override
                public void run() {
                    Object[] options =
                        { Messages.getString("WeasisLauncher.ok"), Messages.getString("WeasisLauncher.no") }; //$NON-NLS-1$ //$NON-NLS-2$

                    int response =
                        JOptionPane.showOptionDialog(
                            loader.getWindow(),
                            Messages.getString("WeasisLauncher.msg"), //$NON-NLS-1$
                            Messages.getString("WeasisLauncher.first"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, //$NON-NLS-1$
                            null, options, null);
                    if (response == 1) {
                        // delete the properties file to ask again
                        file.delete();
                        System.err.println("Refusing the disclaimer"); //$NON-NLS-1$
                        System.exit(-1);
                    }
                }
            });
        } else if (versionNew != null && !versionNew.equals(versionOld)) {
            final StringBuffer message = new StringBuffer("<P>"); //$NON-NLS-1$
            message.append(String.format(Messages.getString("WeasisLauncher.change.version"), versionOld, versionNew)); //$NON-NLS-1$

            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    JTextPane jTextPane1 = new JTextPane();
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
                                        e1.printStackTrace();
                                    }
                                } else if (Desktop.isDesktopSupported()) {
                                    final Desktop desktop = Desktop.getDesktop();
                                    if (desktop.isSupported(Desktop.Action.BROWSE)) {
                                        try {
                                            desktop.browse(e.getURL().toURI());

                                        } catch (IOException ex) {
                                            ex.printStackTrace();
                                        } catch (URISyntaxException ex2) {
                                            ex2.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }
                    });
                    jTextPane1.setBackground(Color.WHITE);
                    StyleSheet ss = ((HTMLEditorKit) jTextPane1.getEditorKit()).getStyleSheet();
                    ss.addRule("p {font-size:12}"); //$NON-NLS-1$
                    message.append("<BR>"); //$NON-NLS-1$
                    String rn = Messages.getString("WeasisLauncher.release"); //$NON-NLS-1$
                    message.append(String.format("<a href=\"%s\">" + rn + "</a>.", //$NON-NLS-1$ //$NON-NLS-2$
                        "http://www.dcm4che.org/jira/secure/ReleaseNote.jspa?projectId=10090&version=10431")); //$NON-NLS-1$
                    message.append("</P>"); //$NON-NLS-1$
                    jTextPane1.setText(message.toString());
                    JOptionPane.showMessageDialog(loader.getWindow(), jTextPane1,
                        Messages.getString("WeasisLauncher.News"), JOptionPane.PLAIN_MESSAGE); //$NON-NLS-1$
                }
            });
        }
        return loader;
    }

    private static Properties readProperties(File propsFile) {
        Properties properties = new Properties();

        if (propsFile.canRead()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(propsFile);
                properties.load(fis);

            } catch (Throwable t) {
            } finally {
                try {
                    if (fis != null) {
                        fis.close();
                    }
                } catch (IOException e) {
                }
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
        // Workaround in substance 6.3 to work with JAVA 7
        UIManager.put("ColorChooserUI", BasicColorChooserUI.class.getName()); //$NON-NLS-1$
        // Do not display metal LAF in bold, it is ugly
        UIManager.put("swing.boldMetal", Boolean.FALSE); //$NON-NLS-1$
        // Display slider value is set to false (already in all LAF by the panel title), used by GTK LAF
        UIManager.put("Slider.paintValue", Boolean.FALSE); //$NON-NLS-1$
        UIManager.LookAndFeelInfo lafs[] = UIManager.getInstalledLookAndFeels();
        laf_exist: if (look != null) {
            for (int i = 0, n = lafs.length; i < n; i++) {
                if (lafs[i].getClassName().equals(look)) {
                    break laf_exist;
                }
            }
            look = null;
        }
        if (look == null) {
            if ("Mac OS X".equals(System.getProperty("os.name"))) { //$NON-NLS-1$ //$NON-NLS-2$
                look = "com.apple.laf.AquaLookAndFeel"; //$NON-NLS-1$
            } else {
                // Try to set Nimbus, concurrent thread issue
                // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6785663
                for (int i = 0, n = lafs.length; i < n; i++) {
                    if (lafs[i].getName().equals("Nimbus")) { //$NON-NLS-1$
                        look = lafs[i].getClassName();
                        break;
                    }
                }
            }
            // Should never happen
            if (look == null) {
                look = UIManager.getSystemLookAndFeelClassName();
            }

        }

        if (look != null) {
            try {
                UIManager.setLookAndFeel(look);

            } catch (Exception e) {
                System.err.println("WARNING : Unable to set the Look&Feel"); //$NON-NLS-1$
                e.printStackTrace();
            }
        }
        return look;
    }
}
