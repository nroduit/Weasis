/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.weasis.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Unpacker;
import java.util.zip.GZIPInputStream;

import org.apache.felix.framework.util.Util;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.service.startlevel.StartLevel;

public class AutoProcessor {

    /**
     * The property name used for the bundle directory.
     **/
    public static final String AUTO_DEPLOY_DIR_PROPERY = "felix.auto.deploy.dir"; //$NON-NLS-1$
    /**
     * The default name used for the bundle directory.
     **/
    public static final String AUTO_DEPLOY_DIR_VALUE = "bundle"; //$NON-NLS-1$
    /**
     * The property name used to specify auto-deploy actions.
     **/
    public static final String AUTO_DEPLOY_ACTION_PROPERY = "felix.auto.deploy.action"; //$NON-NLS-1$
    /**
     * The property name used to specify auto-deploy start level.
     **/
    public static final String AUTO_DEPLOY_STARTLEVEL_PROPERY = "felix.auto.deploy.startlevel"; //$NON-NLS-1$
    /**
     * The name used for the auto-deploy install action.
     **/
    public static final String AUTO_DEPLOY_INSTALL_VALUE = "install"; //$NON-NLS-1$
    /**
     * The name used for the auto-deploy start action.
     **/
    public static final String AUTO_DEPLOY_START_VALUE = "start"; //$NON-NLS-1$
    /**
     * The name used for the auto-deploy update action.
     **/
    public static final String AUTO_DEPLOY_UPDATE_VALUE = "update"; //$NON-NLS-1$
    /**
     * The name used for the auto-deploy uninstall action.
     **/
    public static final String AUTO_DEPLOY_UNINSTALL_VALUE = "uninstall"; //$NON-NLS-1$
    /**
     * The property name prefix for the launcher's auto-install property.
     **/
    public static final String AUTO_INSTALL_PROP = "felix.auto.install"; //$NON-NLS-1$
    /**
     * The property name prefix for the launcher's auto-start property.
     **/
    public static final String AUTO_START_PROP = "felix.auto.start"; //$NON-NLS-1$

    public static final String PACK200_COMPRESSION = ".pack.gz"; //$NON-NLS-1$

    /**
     * Used to instigate auto-deploy directory process and auto-install/auto-start configuration property processing
     * during.
     * 
     * @param configMap
     *            Map of configuration properties.
     * @param context
     *            The system bundle context.
     * @param webStartLoader
     **/
    public static void process(Map configMap, BundleContext context, WebStartLoader webStartLoader) {
        configMap = (configMap == null) ? new HashMap() : configMap;
        processAutoDeploy(configMap, context, webStartLoader);
        processAutoProperties(configMap, context, webStartLoader);
    }

    /**
     * <p>
     * Processes bundles in the auto-deploy directory, performing the specified deploy actions.
     * </p>
     */
    private static void processAutoDeploy(Map configMap, BundleContext context, WebStartLoader webStartLoader) {
        // Determine if auto deploy actions to perform.
        String action = (String) configMap.get(AUTO_DEPLOY_ACTION_PROPERY);
        action = (action == null) ? "" : action; //$NON-NLS-1$
        List actionList = new ArrayList();
        StringTokenizer st = new StringTokenizer(action, ","); //$NON-NLS-1$
        while (st.hasMoreTokens()) {
            String s = st.nextToken().trim().toLowerCase();
            if (s.equals(AUTO_DEPLOY_INSTALL_VALUE) || s.equals(AUTO_DEPLOY_START_VALUE)
                || s.equals(AUTO_DEPLOY_UPDATE_VALUE) || s.equals(AUTO_DEPLOY_UNINSTALL_VALUE)) {
                actionList.add(s);
            }
        }

        // Perform auto-deploy actions.
        if (actionList.size() > 0) {
            // Retrieve the Start Level service, since it will be needed
            // to set the start level of the installed bundles.
            StartLevel sl =
                (StartLevel) context.getService(context
                    .getServiceReference(org.osgi.service.startlevel.StartLevel.class.getName()));

            // Get start level for auto-deploy bundles.
            int startLevel = sl.getInitialBundleStartLevel();
            if (configMap.get(AUTO_DEPLOY_STARTLEVEL_PROPERY) != null) {
                try {
                    startLevel = Integer.parseInt(configMap.get(AUTO_DEPLOY_STARTLEVEL_PROPERY).toString());
                } catch (NumberFormatException ex) {
                    // Ignore and keep default level.
                }
            }

            // Get list of already installed bundles as a map.
            Map installedBundleMap = new HashMap();
            Bundle[] bundles = context.getBundles();
            for (int i = 0; i < bundles.length; i++) {
                installedBundleMap.put(bundles[i].getLocation(), bundles[i]);
            }

            // Get the auto deploy directory.
            String autoDir = (String) configMap.get(AUTO_DEPLOY_DIR_PROPERY);
            autoDir = (autoDir == null) ? AUTO_DEPLOY_DIR_VALUE : autoDir;
            // Look in the specified bundle directory to create a list
            // of all JAR files to install.
            File[] files = new File(autoDir).listFiles();
            List jarList = new ArrayList();
            if (files != null) {
                Arrays.sort(files);
                for (int i = 0; i < files.length; i++) {
                    if (files[i].getName().endsWith(".jar")) { //$NON-NLS-1$
                        jarList.add(files[i]);
                    }
                }
            }
            webStartLoader.setMax(jarList.size());
            // Install bundle JAR files and remember the bundle objects.
            final List startBundleList = new ArrayList();
            for (int i = 0; i < jarList.size(); i++) {
                // Look up the bundle by location, removing it from
                // the map of installed bundles so the remaining bundles
                // indicate which bundles may need to be uninstalled.
                File jar = (File) jarList.get(i);
                Bundle b = (Bundle) installedBundleMap.remove((jar).toURI().toString());
                try {
                    webStartLoader.writeLabel(WebStartLoader.LBL_DOWNLOADING + " " + jar.getName()); //$NON-NLS-1$

                    // If the bundle is not already installed, then install it
                    // if the 'install' action is present.
                    if ((b == null) && actionList.contains(AUTO_DEPLOY_INSTALL_VALUE)) {
                        b = installBundle(context, ((File) jarList.get(i)).toURI().toString());
                    }
                    // If the bundle is already installed, then update it
                    // if the 'update' action is present.
                    else if (b != null && actionList.contains(AUTO_DEPLOY_UPDATE_VALUE)) {
                        b.update();
                    }

                    // If we have found and/or successfully installed a bundle,
                    // then add it to the list of bundles to potentially start
                    // and also set its start level accordingly.
                    if (b != null) {
                        webStartLoader.setValue(i + 1);
                        if (!isFragment(b)) {
                            startBundleList.add(b);
                            sl.setBundleStartLevel(b, startLevel);
                        }
                    }

                } catch (Exception ex) {
                    System.err.println("Auto-deploy install: " + ex //$NON-NLS-1$
                        + ((ex.getCause() != null) ? " - " + ex.getCause() : "")); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }

            // Uninstall all bundles not in the auto-deploy directory if
            // the 'uninstall' action is present.
            if (actionList.contains(AUTO_DEPLOY_UNINSTALL_VALUE)) {
                for (Iterator it = installedBundleMap.entrySet().iterator(); it.hasNext();) {
                    Map.Entry entry = (Map.Entry) it.next();
                    Bundle b = (Bundle) entry.getValue();
                    if (b.getBundleId() != 0) {
                        try {
                            b.uninstall();
                        } catch (BundleException ex) {
                            System.err.println("Auto-deploy uninstall: " + ex //$NON-NLS-1$
                                + ((ex.getCause() != null) ? " - " + ex.getCause() : "")); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                    }
                }
            }

            // Start all installed and/or updated bundles if the 'start'
            // action is present.
            if (actionList.contains(AUTO_DEPLOY_START_VALUE)) {
                for (int i = 0; i < startBundleList.size(); i++) {
                    try {
                        ((Bundle) startBundleList.get(i)).start();

                    } catch (BundleException ex) {
                        System.err.println("Auto-deploy start: " + ex //$NON-NLS-1$
                            + ((ex.getCause() != null) ? " - " + ex.getCause() : "")); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
            }
        }
    }

    /**
     * <p>
     * Processes the auto-install and auto-start properties from the specified configuration properties.
     * </p>
     */
    private static void processAutoProperties(Map configMap, BundleContext context, WebStartLoader webStartLoader) {
        // Retrieve the Start Level service, since it will be needed
        // to set the start level of the installed bundles.
        StartLevel sl =
            (StartLevel) context.getService(context.getServiceReference(org.osgi.service.startlevel.StartLevel.class
                .getName()));

        // Retrieve all auto-install and auto-start properties and install
        // their associated bundles. The auto-install property specifies a
        // space-delimited list of bundle URLs to be automatically installed
        // into each new profile, while the auto-start property specifies
        // bundles to be installed and started. The start level to which the
        // bundles are assigned is specified by appending a ".n" to the
        // property name, where "n" is the desired start level for the list
        // of bundles. If no start level is specified, the default start
        // level is assumed.
        int nbBundles = 0;
        Set set = configMap.keySet();
        for (Iterator i = set.iterator(); i.hasNext();) {
            String key = ((String) i.next()).toLowerCase();

            // Ignore all keys that are not an auto property.
            if (!key.startsWith(AUTO_INSTALL_PROP) && !key.startsWith(AUTO_START_PROP)) {
                continue;
            }
            StringTokenizer st = new StringTokenizer((String) configMap.get(key), "\" ", true); //$NON-NLS-1$
            for (String location = nextLocation(st); location != null; location = nextLocation(st)) {
                nbBundles++;
            }
        }
        webStartLoader.setMax(nbBundles);

        int bundleIter = 0;
        for (Iterator i = set.iterator(); i.hasNext();) {
            String key = ((String) i.next()).toLowerCase();

            // Ignore all keys that are not an auto property.
            if (!key.startsWith(AUTO_INSTALL_PROP) && !key.startsWith(AUTO_START_PROP)) {
                continue;
            }

            // If the auto property does not have a start level,
            // then assume it is the default bundle start level, otherwise
            // parse the specified start level.
            int startLevel = sl.getInitialBundleStartLevel();
            if (!key.equals(AUTO_INSTALL_PROP) && !key.equals(AUTO_START_PROP)) {
                try {
                    startLevel = Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
                } catch (NumberFormatException ex) {
                    System.err.println("Invalid property: " + key); //$NON-NLS-1$
                }
            }

            // Parse and install the bundles associated with the key.
            StringTokenizer st = new StringTokenizer((String) configMap.get(key), "\" ", true); //$NON-NLS-1$
            for (String location = nextLocation(st); location != null; location = nextLocation(st)) {
                String bundleName = location.substring(location.lastIndexOf("/") + 1, location.length()); //$NON-NLS-1$
                try {
                    webStartLoader.writeLabel(WebStartLoader.LBL_DOWNLOADING + " " + bundleName); //$NON-NLS-1$
                    Bundle b = installBundle(context, location);

                    bundleIter++;
                    webStartLoader.setValue(bundleIter);
                    sl.setBundleStartLevel(b, startLevel);

                    if (WeasisLauncher.modulesi18n != null) {
                        // Version v = b.getVersion();
                        StringBuffer p = new StringBuffer(b.getSymbolicName());
                        p.append("-i18n-"); //$NON-NLS-1$
                        // From 1.1.0, i18n module can be plugged in any version. The SVN revision (the qualifier) will
                        // update the version.
                        p.append("1.1.0"); //$NON-NLS-1$
                        // p.append(v.getMajor());
                        // p.append("."); //$NON-NLS-1$
                        // p.append(v.getMinor());
                        // p.append("."); //$NON-NLS-1$
                        // p.append(v.getMicro());
                        p.append(".jar"); //$NON-NLS-1$
                        String prop = p.toString();
                        String value = WeasisLauncher.modulesi18n.getProperty(prop);
                        if (value != null) {
                            String translation_modules = System.getProperty("weasis.i18n", ""); //$NON-NLS-1$ //$NON-NLS-2$
                            translation_modules += translation_modules.endsWith("/") ? prop : "/" + prop; //$NON-NLS-1$ //$NON-NLS-2$
                            Bundle b2 = context.installBundle(translation_modules, null);
                            sl.setBundleStartLevel(b2, startLevel);
                            if (!value.equals(b2.getVersion().getQualifier())) {
                                b2.update();
                            }
                        }
                    }
                } catch (Exception ex) {
                    String arch = System.getProperty("native.library.spec"); //$NON-NLS-1$
                    if (bundleName.contains(arch)) {
                        System.err.println("Cannot install native plug-in: " + bundleName); //$NON-NLS-1$
                    } else {
                        System.err.println("Auto-properties install: " + location + " (" + ex //$NON-NLS-1$ //$NON-NLS-2$
                            + ((ex.getCause() != null) ? " - " + ex.getCause() : "") + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if (ex.getCause() != null) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        }
        webStartLoader.writeLabel(Messages.getString("AutoProcessor.start")); //$NON-NLS-1$
        // Now loop through the auto-start bundles and start them.
        for (Iterator i = configMap.keySet().iterator(); i.hasNext();) {
            String key = ((String) i.next()).toLowerCase();
            if (key.startsWith(AUTO_START_PROP)) {
                StringTokenizer st = new StringTokenizer((String) configMap.get(key), "\" ", true); //$NON-NLS-1$
                for (String location = nextLocation(st); location != null; location = nextLocation(st)) {
                    // Installing twice just returns the same bundle.
                    try {
                        Bundle b = installBundle(context, location);
                        if (b != null) {
                            b.start();
                        }
                    } catch (Exception ex) {
                        System.err.println("Auto-properties start: " + location + " (" + ex //$NON-NLS-1$ //$NON-NLS-2$
                            + ((ex.getCause() != null) ? " - " + ex.getCause() : "") + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    }
                }
            }
        }
    }

    private static String nextLocation(StringTokenizer st) {
        String retVal = null;

        if (st.countTokens() > 0) {
            String tokenList = "\" "; //$NON-NLS-1$
            StringBuffer tokBuf = new StringBuffer(10);
            String tok = null;
            boolean inQuote = false;
            boolean tokStarted = false;
            boolean exit = false;
            while ((st.hasMoreTokens()) && (!exit)) {
                tok = st.nextToken(tokenList);
                if (tok.equals("\"")) { //$NON-NLS-1$
                    inQuote = !inQuote;
                    if (inQuote) {
                        tokenList = "\""; //$NON-NLS-1$
                    } else {
                        tokenList = "\" "; //$NON-NLS-1$
                    }

                } else if (tok.equals(" ")) { //$NON-NLS-1$
                    if (tokStarted) {
                        retVal = tokBuf.toString();
                        tokStarted = false;
                        tokBuf = new StringBuffer(10);
                        exit = true;
                    }
                } else {
                    tokStarted = true;
                    tokBuf.append(tok.trim());
                }
            }

            // Handle case where end of token stream and
            // still got data
            if ((!exit) && (tokStarted)) {
                retVal = tokBuf.toString();
            }
        }

        return retVal;
    }

    private static boolean isFragment(Bundle bundle) {
        return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
    }

    private static Bundle installBundle(BundleContext context, String location) throws Exception {
        boolean pack = location.endsWith(PACK200_COMPRESSION);
        if (pack) {
            // Remove the pack classifier from the location path
            location = location.substring(0, location.length() - 8);
            pack = context.getBundle(location) == null;
        }

        if (pack) {

            final URL url = new URL((URL) null, location + PACK200_COMPRESSION, null);

            // URLConnection conn = url.openConnection();
            // InputStream is = conn.getInputStream();
            // Unpacker unpacker = Pack200.newUnpacker();
            // File tmpFile = File.createTempFile("tmpPack200", ".jar");
            // JarOutputStream origJarStream = new JarOutputStream(new FileOutputStream(tmpFile));
            // unpacker.unpack(new GZIPInputStream(is), origJarStream);
            // origJarStream.close();

            final PipedInputStream in = new PipedInputStream();
            final PipedOutputStream out = new PipedOutputStream(in);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    JarOutputStream jarStream = null;
                    try {
                        URLConnection conn = url.openConnection();
                        // Support for http proxy authentication.
                        String auth = System.getProperty("http.proxyAuth", null);
                        if ((auth != null) && (auth.length() > 0)) {
                            if ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol())) {
                                String base64 = Util.base64Encode(auth);
                                conn.setRequestProperty("Proxy-Authorization", "Basic " + base64);
                            }
                        }
                        InputStream is = conn.getInputStream();
                        Unpacker unpacker = Pack200.newUnpacker();
                        jarStream = new JarOutputStream(out);
                        unpacker.unpack(new GZIPInputStream(is), jarStream);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        FileUtil.safeClose(jarStream);
                    }
                }
            }).start();

            return context.installBundle(location, in);

        }
        return context.installBundle(location, null);
    }
}