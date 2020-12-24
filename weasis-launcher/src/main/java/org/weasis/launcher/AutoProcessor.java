/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 *  Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 *  License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package org.weasis.launcher;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.service.startlevel.StartLevel;
import org.tukaani.xz.XZInputStream;

/**
 * 
 * @author Richard S. Hall
 * @author Nicolas Roduit
 */
public class AutoProcessor {

    private static final Logger LOGGER = Logger.getLogger(FileUtil.class.getName());

    /**
     * The property name used for the bundle directory.
     **/
    public static final String AUTO_DEPLOY_DIR_PROPERTY = "felix.auto.deploy.dir"; //$NON-NLS-1$
    /**
     * The default name used for the bundle directory.
     **/
    public static final String AUTO_DEPLOY_DIR_VALUE = "bundle"; //$NON-NLS-1$
    /**
     * The property name used to specify auto-deploy actions.
     **/
    public static final String AUTO_DEPLOY_ACTION_PROPERTY = "felix.auto.deploy.action"; //$NON-NLS-1$
    /**
     * The property name used to specify auto-deploy start level.
     **/
    public static final String AUTO_DEPLOY_STARTLEVEL_PROPERTY = "felix.auto.deploy.startlevel"; //$NON-NLS-1$
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

    public static final String XZ_COMPRESSION = ".xz"; //$NON-NLS-1$

    private AutoProcessor() {
    }

    /**
     * Used to instigate auto-deploy directory process and auto-install/auto-start configuration property processing
     * during.
     *
     * @param configMap
     *            Map of configuration properties.
     * @param context
     *            The system bundle context.
     * @param weasisLoader
     **/
    public static void process(Map<String, String> configMap, Properties modulesi18n, BundleContext context,
        WeasisLoader weasisLoader) {
        Map<String, String> map = (configMap == null) ? new HashMap<>() : configMap;
        processAutoDeploy(map, context, weasisLoader);
        processAutoProperties(map, modulesi18n, context, weasisLoader);
    }

    /**
     * <p>
     * Processes bundles in the auto-deploy directory, performing the specified deploy actions.
     * </p>
     */
    private static void processAutoDeploy(Map<String, String> configMap, BundleContext context,
        WeasisLoader weasisLoader) {
        // Determine if auto deploy actions to perform.
        String action = configMap.get(AUTO_DEPLOY_ACTION_PROPERTY);
        action = (action == null) ? "" : action; //$NON-NLS-1$
        List<String> actionList = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(action, ","); //$NON-NLS-1$
        while (st.hasMoreTokens()) {
            String s = st.nextToken().trim().toLowerCase();
            if (s.equals(AUTO_DEPLOY_INSTALL_VALUE) || s.equals(AUTO_DEPLOY_START_VALUE)
                || s.equals(AUTO_DEPLOY_UPDATE_VALUE) || s.equals(AUTO_DEPLOY_UNINSTALL_VALUE)) {
                actionList.add(s);
            }
        }

        // Perform auto-deploy actions.
        if (!actionList.isEmpty()) {
            // Retrieve the Start Level service, since it will be needed
            // to set the start level of the installed bundles.
            StartLevel sl = (StartLevel) context
                .getService(context.getServiceReference(org.osgi.service.startlevel.StartLevel.class.getName()));

            // Get start level for auto-deploy bundles.
            int startLevel = sl.getInitialBundleStartLevel();
            if (configMap.get(AUTO_DEPLOY_STARTLEVEL_PROPERTY) != null) {
                try {
                    startLevel = Integer.parseInt(configMap.get(AUTO_DEPLOY_STARTLEVEL_PROPERTY));
                } catch (NumberFormatException ex) {
                    // Ignore and keep default level.
                }
            }

            // Get list of already installed bundles as a map.
            Map<String, Bundle> installedBundleMap = new HashMap<>();
            Bundle[] bundles = context.getBundles();
            for (int i = 0; i < bundles.length; i++) {
                installedBundleMap.put(bundles[i].getLocation(), bundles[i]);
            }

            // Get the auto deploy directory.
            String autoDir = configMap.get(AUTO_DEPLOY_DIR_PROPERTY);
            autoDir = (autoDir == null) ? AUTO_DEPLOY_DIR_VALUE : autoDir;
            // Look in the specified bundle directory to create a list
            // of all JAR files to install.
            File[] files = new File(autoDir).listFiles();
            List<File> jarList = new ArrayList<>();
            if (files != null) {
                Arrays.sort(files);
                for (int i = 0; i < files.length; i++) {
                    if (files[i].getName().endsWith(".jar")) { //$NON-NLS-1$
                        jarList.add(files[i]);
                    }
                }
            }
            weasisLoader.setMax(jarList.size());

            boolean cache =
                Boolean.TRUE.toString().equals(System.getProperty("http.bundle.cache", Boolean.TRUE.toString())); //$NON-NLS-1$
            // Install bundle JAR files and remember the bundle objects.
            final List<Bundle> startBundleList = new ArrayList<>();
            for (int i = 0; i < jarList.size(); i++) {
                // Look up the bundle by location, removing it from
                // the map of installed bundles so the remaining bundles
                // indicate which bundles may need to be uninstalled.
                File jar = jarList.get(i);
                Bundle b = installedBundleMap.remove((jar).toURI().toString());
                try {
                    weasisLoader.writeLabel(WeasisLoader.LBL_DOWNLOADING + " " + jar.getName()); //$NON-NLS-1$

                    // If the bundle is not already installed, then install it
                    // if the 'install' action is present.
                    if ((b == null) && actionList.contains(AUTO_DEPLOY_INSTALL_VALUE)) {
                        b = installBundle(context, jarList.get(i).toURI().toString(), cache);
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
                        weasisLoader.setValue(i + 1);
                        if (!isFragment(b)) {
                            startBundleList.add(b);
                            sl.setBundleStartLevel(b, startLevel);
                        }
                    }

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, ex, () -> String.format("Auto-deploy install %s", jar.getName())); //$NON-NLS-1$
                    if (!Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT
                        .equals(configMap.get(Constants.FRAMEWORK_STORAGE_CLEAN))) {
                        // Reset all the old cache
                        throw new IllegalStateException("A bundle cannot be started"); //$NON-NLS-1$
                    }
                }
            }

            // Uninstall all bundles not in the auto-deploy directory if
            // the 'uninstall' action is present.
            if (actionList.contains(AUTO_DEPLOY_UNINSTALL_VALUE)) {
                for (Iterator<Entry<String, Bundle>> it = installedBundleMap.entrySet().iterator(); it.hasNext();) {
                    Entry<String, Bundle> entry = it.next();
                    Bundle b = entry.getValue();
                    if (b.getBundleId() != 0) {
                        try {
                            b.uninstall();
                        } catch (BundleException ex) {
                            LOGGER.log(Level.SEVERE, ex,
                                () -> String.format("Auto-deploy uninstall bundle %s", b.getSymbolicName())); //$NON-NLS-1$
                        }
                    }
                }
            }

            // Start all installed and/or updated bundles if the 'start'
            // action is present.
            if (actionList.contains(AUTO_DEPLOY_START_VALUE)) {
                for (int i = 0; i < startBundleList.size(); i++) {
                    Bundle b = startBundleList.get(i);
                    try {
                        b.start();
                    } catch (BundleException ex) {
                        LOGGER.log(Level.SEVERE, ex,
                            () -> String.format("Auto-deploy install bundle %s", b.getSymbolicName())); //$NON-NLS-1$
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
    private static void processAutoProperties(Map<String, String> configMap, Properties modulesi18n,
        BundleContext context, WeasisLoader weasisLoader) {
        // Retrieve the Start Level service, since it will be needed
        // to set the start level of the installed bundles.
        StartLevel sl = (StartLevel) context
            .getService(context.getServiceReference(org.osgi.service.startlevel.StartLevel.class.getName()));

        // Retrieve all auto-install and auto-start properties and install
        // their associated bundles. The auto-install property specifies a
        // space-delimited list of bundle URLs to be automatically installed
        // into each new profile, while the auto-start property specifies
        // bundles to be installed and started. The start level to which the
        // bundles are assigned is specified by appending a ".n" to the
        // property name, where "n" is the desired start level for the list
        // of bundles. If no start level is specified, the default start
        // level is assumed.
        Map<String, BundleElement> bundleList = new HashMap<>();

        Set set = configMap.keySet();
        for (Iterator item = set.iterator(); item.hasNext();) {
            String key = ((String) item.next()).toLowerCase();

            // Ignore all keys that are not an auto property.
            if (!key.startsWith(AUTO_INSTALL_PROP) && !key.startsWith(AUTO_START_PROP)) {
                continue;
            }
            // If the auto property does not have a start level,
            // then assume it is the default bundle start level, otherwise
            // parse the specified start level.
            int startLevel = sl.getInitialBundleStartLevel();
            try {
                startLevel = Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
            } catch (NumberFormatException ex) {
                LOGGER.log(Level.SEVERE, ex, () -> String.format("Invalid start level %s", key)); //$NON-NLS-1$
            }
            boolean canBeStarted = key.startsWith(AUTO_START_PROP);
            StringTokenizer st = new StringTokenizer(configMap.get(key), "\" ", true); //$NON-NLS-1$
            for (String location = nextLocation(st); location != null; location = nextLocation(st)) {
                String bundleName = getBundleNameFromLocation(location);
                if (!"System Bundle".equals(bundleName)) { //$NON-NLS-1$
                    BundleElement b = new BundleElement(startLevel, location, canBeStarted);
                    bundleList.put(bundleName, b);
                }
            }
        }
        weasisLoader.setMax(bundleList.size());

        final Map<String, Bundle> installedBundleMap = new HashMap<>();
        Bundle[] bundles = context.getBundles();
        for (int i = 0; i < bundles.length; i++) {
            String bundleName = getBundleNameFromLocation(bundles[i].getLocation());
            if (bundleName == null) {
                // Should never happen
                continue;
            }
            try {
                BundleElement b = bundleList.get(bundleName);
                // Remove the bundles in cache when they are not in the config.properties list
                if (b == null) {
                    if (!"System Bundle".equals(bundleName)) {//$NON-NLS-1$
                        bundles[i].uninstall();
                        LOGGER.log(Level.INFO, "Uninstall unused bundle: {0}", bundleName); //$NON-NLS-1$
                    }
                    continue;
                }
                // Remove snapshot version to install it every time
                if (bundles[i].getVersion().getQualifier().endsWith("SNAPSHOT")) { //$NON-NLS-1$
                    bundles[i].uninstall();
                    LOGGER.log(Level.INFO, "Uninstall SNAPSHOT bundle: {0}", bundleName); //$NON-NLS-1$
                    continue;
                }
                installedBundleMap.put(bundleName, bundles[i]);

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e,
                    () -> String.format("Cannot remove from OSGI cache the bundle %s", bundleName)); //$NON-NLS-1$
            }
        }

        boolean cache =
            Boolean.TRUE.toString().equals(System.getProperty("http.bundle.cache", Boolean.TRUE.toString())); //$NON-NLS-1$
        int bundleIter = 0;

        // Parse and install the bundles associated with the key.
        for (Iterator<Entry<String, BundleElement>> iter = bundleList.entrySet().iterator(); iter.hasNext();) {
            Entry<String, BundleElement> element = iter.next();
            String bundleName = element.getKey();
            BundleElement bundle = element.getValue();
            if (bundle == null) {
                // Should never happen
                continue;
            }
            try {
                weasisLoader.writeLabel(WeasisLoader.LBL_DOWNLOADING + " " + bundleName); //$NON-NLS-1$
                // Do not download again the same bundle version but with different location or already in installed
                // in cache from a previous version of Weasis
                Bundle b = installedBundleMap.get(bundleName);
                if (b == null) {
                    b = installBundle(context, bundle.getLocation(), cache);
                    installedBundleMap.put(bundleName, b);
                }
                sl.setBundleStartLevel(b, bundle.getStartLevel());
                loadTranslationBundle(context, b, installedBundleMap, modulesi18n, cache);
            } catch (Exception ex) {
                if (bundleName.contains(System.getProperty("native.library.spec"))) { //$NON-NLS-1$
                    LOGGER.log(Level.SEVERE, ex, () -> String.format("Cannot install a native bundle %s", bundleName)); //$NON-NLS-1$
                } else {
                    LOGGER.log(Level.SEVERE, ex, () -> String.format("Cannot install bundle %s", bundleName)); //$NON-NLS-1$
                    if (!Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT
                        .equals(configMap.get(Constants.FRAMEWORK_STORAGE_CLEAN))) {
                        // Reset all the old cache
                        throw new IllegalStateException("A bundle cannot be started"); //$NON-NLS-1$
                    }
                }
            } finally {
                bundleIter++;
                weasisLoader.setValue(bundleIter);
            }

        }

        weasisLoader.writeLabel(Messages.getString("AutoProcessor.start")); //$NON-NLS-1$
        // Now loop through the auto-start bundles and start them.
        for (Iterator<Entry<String, BundleElement>> iter = bundleList.entrySet().iterator(); iter.hasNext();) {
            Entry<String, BundleElement> element = iter.next();
            String bundleName = element.getKey();
            BundleElement bundle = element.getValue();
            if (bundle == null) {
                // Should never happen
                continue;
            }
            if (bundle.isCanBeStarted()) {
                try {
                    Bundle b = installedBundleMap.get(bundleName);
                    if (b == null) {
                        // Try to reinstall
                        b = installBundle(context, bundle.getLocation(), cache);
                    }
                    if (b != null) {
                        b.start();
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, ex, () -> String.format("Cannot start bundle %s", bundleName)); //$NON-NLS-1$
                }
            }
        }
    }

    static String getBundleNameFromLocation(String location) {
        if (location != null) {
            int index = location.lastIndexOf('/');
            String name = index >= 0 ? location.substring(index + 1) : location;
            index = name.lastIndexOf(".jar"); //$NON-NLS-1$
            return index >= 0 ? name.substring(0, index) : name;
        }
        return null;
    }

    private static void loadTranslationBundle(BundleContext context, Bundle b,
        final Map<String, Bundle> installedBundleMap, Properties modulesi18n, boolean cache) {
        if (!modulesi18n.isEmpty()) {
            if (b != null) {
                StringBuilder p = new StringBuilder(b.getSymbolicName());
                p.append("-i18n.jar.xz"); //$NON-NLS-1$
                String filename = p.toString();
                String value = modulesi18n.getProperty(filename);
                if (value != null) {
                    String baseURL = System.getProperty(WeasisLauncher.P_WEASIS_I18N);
                    if (baseURL != null) {
                        String uri = baseURL + (baseURL.endsWith("/") ? filename : "/" + filename); //$NON-NLS-1$ //$NON-NLS-2$
                        String bundleName = getBundleNameFromLocation(filename);
                        try {
                            Bundle b2 = installedBundleMap.get(bundleName);
                            if (b2 == null) {
                                b2 = installBundle(context, uri, cache);
                                installedBundleMap.put(bundleName, b);
                            }
                            if (b2 != null && !value.equals(b2.getVersion().getQualifier())) {
                                if (b2.getLocation().startsWith(baseURL)) {
                                    b2.update();
                                } else {
                                    // Handle same bundle version with different location
                                    try {
                                        b2.uninstall();
                                        context.installBundle(uri, FileUtil
                                            .getAdaptedConnection(new URI(uri).toURL(), false).getInputStream());
                                        installedBundleMap.put(bundleName, b);
                                    } catch (Exception exc) {
                                        LOGGER.log(Level.SEVERE, exc,
                                            () -> String.format("Cannot install a translation bundle %s", uri)); //$NON-NLS-1$
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, e,
                                () -> String.format("Cannot install a translation bundle %s", uri)); //$NON-NLS-1$
                        }
                    }
                }
            }
        }
    }

    private static String nextLocation(StringTokenizer st) {
        String retVal = null;

        if (st.countTokens() > 0) {
            String tokenList = "\" "; //$NON-NLS-1$
            StringBuilder tokBuf = new StringBuilder(10);
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
                        tokBuf = new StringBuilder(10);
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

            if (Utils.hasText(retVal)) {
                retVal = Utils.adaptPathToUri(retVal);
            }
        }

        return retVal;
    }

    private static boolean isFragment(Bundle bundle) {
        return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
    }

    private static Bundle installBundle(BundleContext context, String location, boolean httpCache) throws Exception {
        boolean pack = location.endsWith(XZ_COMPRESSION);
        if (pack) {
            // Remove the pack classifier from the location path
            location = location.substring(0, location.length() - 3);
            pack = context.getBundle(location) == null;
        }

        if (pack) {
            final URL url = new URL(location + XZ_COMPRESSION);
            try (XZInputStream xzStream =
                new XZInputStream(FileUtil.getAdaptedConnection(url, httpCache).getInputStream())) {
                return context.installBundle(location, xzStream);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e, () -> String.format("Cannot install xz compressed bundle %s", url)); //$NON-NLS-1$
            }
        }
        return context.installBundle(location,
            FileUtil.getAdaptedConnection(new URI(location).toURL(), httpCache).getInputStream());
    }

    static class BundleElement {
        private final int startLevel;
        private final String location;
        private final boolean canBeStarted;

        public BundleElement(int startLevel, String location, boolean canBeStarted) {

            this.startLevel = startLevel;
            this.location = location;
            this.canBeStarted = canBeStarted;
        }

        public int getStartLevel() {
            return startLevel;
        }

        public String getLocation() {
            return location;
        }

        public boolean isCanBeStarted() {
            return canBeStarted;
        }

    }
}
