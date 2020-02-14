/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
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
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLDecoder;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.felix.framework.util.Util;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

public class ConfigData {
    private static final Logger LOGGER = Logger.getLogger(ConfigData.class.getName());

    // Params, see https://nroduit.github.io/en/getting-started/weasis-protocol/#modify-the-launch-parameters
    public static final String PARAM_CONFIG_URL = "wcfg"; //$NON-NLS-1$
    public static final String PARAM_ARGUMENT = "arg"; //$NON-NLS-1$
    public static final String PARAM_PROPERTY = "pro"; //$NON-NLS-1$
    public static final String PARAM_CODEBASE = "cdb"; //$NON-NLS-1$
    public static final String PARAM_CODEBASE_EXT = "cdb-ext"; //$NON-NLS-1$
    public static final String PARAM_AUTHORIZATION = "auth"; //$NON-NLS-1$

    private final List<String> arguments = new ArrayList<>();
    private final Properties properties = new Properties();

    private final StringBuilder configOutput = new StringBuilder();
    private final Map<String, String> felixProps = new HashMap<>();

    protected ConfigData() {
    }

    public ConfigData(String[] args) {
        init(args);
    }

    public void init(String[] args) {
        this.clear();
        LOGGER.log(Level.INFO, "Starting Weasis..."); //$NON-NLS-1$
        LOGGER.log(Level.INFO, "Initialization of the launch configuration..."); //$NON-NLS-1$

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                LOGGER.log(Level.INFO, "Main arg {0} = {1}", new Object[] { Integer.toString(i), args[i] }); //$NON-NLS-1$
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
                    splitArgToCmd(otherArgs.toArray(new String[otherArgs.size()]));
                }
            }
        }

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
            if (propertyName.startsWith("weasis.")) { //$NON-NLS-1$
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
        // Set system property for dynamically loading only native libraries corresponding of the current platform
        setOsgiNativeLibSpecification();

        String profile = felixConfig.getProperty(P_WEASIS_PROFILE, "default"); //$NON-NLS-1$
        addProperty(P_WEASIS_PROFILE, profile);

        String name = felixConfig.getProperty(P_WEASIS_NAME, "Weasis"); //$NON-NLS-1$
        addProperty(P_WEASIS_NAME, name); // $NON-NLS-1$

        String version = felixConfig.getProperty(P_WEASIS_VERSION, "0.0.0"); //$NON-NLS-1$
        addProperty(P_WEASIS_VERSION, version);

        String codebase = properties.getProperty(P_WEASIS_CODEBASE_URL);
        addProperty(P_WEASIS_SOURCE_ID, toHex((codebase + profile).hashCode()));

        String user = properties.getProperty(P_WEASIS_USER);
        if (!Utils.hasText(user)) {
            user = System.getProperty("user.name", "unknown"); //$NON-NLS-1$ //$NON-NLS-2$
            addProperty(P_WEASIS_USER, user);
            addProperty("weasis.pref.local.session", Boolean.TRUE.toString()); //$NON-NLS-1$
        }

        // Define the http user agent
        addProperty("http.agent", //$NON-NLS-1$
            name + "/" + version + " (" + System.getProperty(P_OS_NAME) + "; " + System.getProperty("os.version") + "; " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                + System.getProperty("os.arch") + ")"); //$NON-NLS-1$ //$NON-NLS-2$

        String portable = properties.getProperty("weasis.portable.dir"); //$NON-NLS-1$
        if (portable != null) {
            LOGGER.log(Level.INFO, "Starting portable version"); //$NON-NLS-1$
            String pkey = "weasis.portable.dicom.directory"; //$NON-NLS-1$
            addProperty(pkey, felixConfig.getProperty(pkey, "dicom,DICOM,IMAGES,images")); //$NON-NLS-1$
        }

        // Set weasis properties to Java System Properties before variables substitution.
        applyConfigToSystemProperties();

        filterConfigProperties(felixConfig);
        if (LOGGER.isLoggable(Level.FINEST)) {
            felixProps.forEach((k, v) -> LOGGER.log(Level.FINEST, () -> String.format("Felix config: %s = %s", k, v))); //$NON-NLS-1$
        }

        File appFolder = new File(felixProps.get(Constants.FRAMEWORK_STORAGE)).getParentFile();
        appFolder.mkdirs();
        addProperty(P_WEASIS_PATH, appFolder.getPath());
        System.setProperty(P_WEASIS_PATH, appFolder.getPath());
    }

    private void filterConfigProperties(Properties felixConfig) {
        // Only required for dev purposes (running the app in IDE)
        String mvnRepo = System.getProperty("maven.localRepository", felixConfig.getProperty("maven.local.repo")); //$NON-NLS-1$ //$NON-NLS-2$
        if (mvnRepo != null) {
            System.setProperty("maven.localRepository", Utils.adaptPathToUri(mvnRepo)); //$NON-NLS-1$
        }

        // Perform variable substitution for system properties and convert to dictionary.

        for (Enumeration<?> e = felixConfig.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            felixProps.put(name, Util.substVars(felixConfig.getProperty(name), name, null, felixConfig));
        }
    }

    private void extractArgFromUri(String uri) {
        try {
            String url = URLDecoder.decode(uri, "UTF-8"); //$NON-NLS-1$
            String[] cmds = url.split("\\$"); //$NON-NLS-1$
            if (cmds.length > 0) {
                for (int i = 1; i < cmds.length; i++) {
                    // Fix Windows issue (add a trailing slash)
                    if (i == cmds.length - 1 && cmds[i].endsWith("/")) { //$NON-NLS-1$
                        cmds[i] = cmds[i].substring(0, cmds[i].length() - 1);
                    }
                    arguments.add(cmds[i]);
                }
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.SEVERE, "Decoding weasis URI", e); //$NON-NLS-1$
        }
    }

    /**
     * First extract $weasis:config arguments from command launch. If serviceConfigUrl is given then config parameters
     * will be read from this URl. <br>
     * If none codebase/or weasis properties URLs are given from arguments, then default values will be set.<br>
     * Local path is considered when codebaseUrl is not defined.
     * 
     * @note Any command launch argument takes priority over serviceConfig parameters. <br>
     * 
     */

    private void applyConfigFromArguments() {
        List<String> configArgs = extractWeasisConfigArguments();
        applyConfigParams(getConfigParamsFromArgs(configArgs));
        applyConfigParams(getConfigParamsFromServicePath());

        String codeBaseUrl = properties.getProperty(P_WEASIS_CODEBASE_URL, ""); //$NON-NLS-1$
        if (!Utils.hasText(codeBaseUrl)) {
            applyLocalCodebase();
        }

        if (!properties.containsKey(CONFIG_PROPERTIES_PROP) && Utils.hasText(codeBaseUrl)) {
            String configProp = String.format("%s/%s/%s", codeBaseUrl, CONFIG_DIRECTORY, CONFIG_PROPERTIES_FILE_VALUE); //$NON-NLS-1$
            addProperty(CONFIG_PROPERTIES_PROP, configProp);
        }

        String codeBaseExtUrl = properties.getProperty(P_WEASIS_CODEBASE_EXT_URL, ""); //$NON-NLS-1$
        if (!properties.containsKey(EXTENDED_PROPERTIES_PROP) && Utils.hasText(codeBaseExtUrl)) {
            String extConfigProp =
                String.format("%s/%s/%s", codeBaseExtUrl, CONFIG_DIRECTORY, EXTENDED_PROPERTIES_FILE_VALUE); //$NON-NLS-1$
            addProperty(EXTENDED_PROPERTIES_PROP, extConfigProp);
        }

        configOutput.append("\n  Application local codebase = "); //$NON-NLS-1$
        configOutput.append(properties.getProperty(P_WEASIS_CODEBASE_LOCAL));
        configOutput.append("\n  Application codebase URL = "); //$NON-NLS-1$
        configOutput.append(properties.getProperty(P_WEASIS_CODEBASE_URL));
    }

    private String applyLocalCodebase() {
        File localCodebase = findLocalCodebase();
        String baseURI = localCodebase.toURI().toString();
        if (baseURI.endsWith("/")) { //$NON-NLS-1$
            baseURI = baseURI.substring(0, baseURI.length() - 1);
        }
        try {
            addProperty(P_WEASIS_CODEBASE_LOCAL, localCodebase.getAbsolutePath());
            addProperty(P_WEASIS_CODEBASE_URL, baseURI);
            baseURI += "/" + CONFIG_DIRECTORY + "/"; //$NON-NLS-1$ //$NON-NLS-2$
            addProperty(CONFIG_PROPERTIES_PROP, baseURI + CONFIG_PROPERTIES_FILE_VALUE);
            addProperty(EXTENDED_PROPERTIES_PROP, baseURI + EXTENDED_PROPERTIES_FILE_VALUE);

            // Allow export feature for local/portable version
            addProperty("weasis.export.dicom", Boolean.TRUE.toString()); //$NON-NLS-1$
            addProperty("weasis.export.dicom.send", Boolean.TRUE.toString()); //$NON-NLS-1$
            addProperty("weasis.import.dicom", Boolean.TRUE.toString()); //$NON-NLS-1$
            addProperty("weasis.import.dicom.qr", Boolean.TRUE.toString()); //$NON-NLS-1$
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Apply Codebase", e); //$NON-NLS-1$
        }
        return baseURI;
    }

    private void applyConfigParams(Map<String, List<String>> configParams) {
        if (configParams == null) {
            return;
        }

        configParams.forEach((k, v) -> {
            switch (k) {
                case PARAM_CONFIG_URL:
                    addProperty(P_WEASIS_CONFIG_URL, v.get(0));
                    break;
                case PARAM_CODEBASE:
                    addProperty(P_WEASIS_CODEBASE_URL, v.get(0));
                    break;
                case PARAM_CODEBASE_EXT:
                    addProperty(P_WEASIS_CODEBASE_EXT_URL, v.get(0));
                    break;
                case PARAM_AUTHORIZATION:
                    addProperty(P_HTTP_AUTHORIZATION, v.get(0));
                    break;
                case PARAM_PROPERTY:
                    addProperties(v);
                    break;
                case PARAM_ARGUMENT:
                    addArguments(v);
                    break;
                default:
                    break;
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
        Pattern pattern = Pattern.compile("\\s+"); //$NON-NLS-1$

        properties.forEach(value -> {
            String[] result = pattern.split(value, 2);
            if (result.length == 2) {
                addProperty(result[0], result[1]);
            } else {
                LOGGER.log(Level.WARNING, "Cannot parse property: {0}", value); //$NON-NLS-1$
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
        File file = new File(dir, "persitence.properties"); //$NON-NLS-1$
        if (!file.canRead()) {
            return;
        }
        Properties p = new Properties();
        FileUtil.readProperties(file, p);

        boolean mproxy = Utils.getEmptytoFalse(p.getProperty("proxy.manual")); //$NON-NLS-1$

        if (mproxy) {
            String exceptions = p.getProperty("proxy.exceptions"); //$NON-NLS-1$
            String val = p.getProperty("proxy.http.host"); //$NON-NLS-1$
            applyProxyProperty("http.proxyHost", val, mproxy); //$NON-NLS-1$
            if (Utils.hasText(val)) {
                applyProxyProperty("http.proxyPort", p.getProperty("proxy.http.port"), mproxy); //$NON-NLS-1$ //$NON-NLS-2$
                applyProxyProperty("http.nonProxyHosts", exceptions, mproxy); //$NON-NLS-1$
            }

            val = p.getProperty("proxy.https.host"); //$NON-NLS-1$
            applyProxyProperty("https.proxyHost", val, mproxy); //$NON-NLS-1$
            if (Utils.hasText(val)) {
                applyProxyProperty("https.proxyPort", p.getProperty("proxy.https.port"), mproxy); //$NON-NLS-1$ //$NON-NLS-2$
                applyProxyProperty("http.nonProxyHosts", exceptions, mproxy); //$NON-NLS-1$
            }

            val = p.getProperty("proxy.ftp.host"); //$NON-NLS-1$
            applyProxyProperty("ftp.proxyHost", val, mproxy); //$NON-NLS-1$
            if (Utils.hasText(val)) {
                applyProxyProperty("ftp.proxyPort", p.getProperty("proxy.ftp.port"), mproxy); //$NON-NLS-1$ //$NON-NLS-2$
                applyProxyProperty("ftp.nonProxyHosts", exceptions, mproxy); //$NON-NLS-1$
            }

            val = p.getProperty("proxy.socks.host"); //$NON-NLS-1$
            applyProxyProperty("socksProxyHost", val, mproxy); //$NON-NLS-1$
            if (Utils.hasText(val)) {
                applyProxyProperty("socksProxyPort", p.getProperty("proxy.socks.port"), mproxy); //$NON-NLS-1$ //$NON-NLS-2$
            }

            boolean auth = Utils.getEmptytoFalse(p.getProperty("proxy.auth")); //$NON-NLS-1$
            if (auth) {
                String authUser = p.getProperty("proxy.auth.user"); //$NON-NLS-1$
                String authPassword;
                try {
                    byte[] pwd = Utils.getByteArrayProperty(p, "proxy.auth.pwd", null); //$NON-NLS-1$
                    if (pwd != null) {
                        pwd = Utils.decrypt(pwd, "proxy.auth"); //$NON-NLS-1$
                        if (pwd != null && pwd.length > 0) {
                            authPassword = new String(pwd);
                            applyPasswordAuthentication(authUser, authPassword);
                            applyProxyProperty("http.proxyUser", authUser, mproxy); //$NON-NLS-1$
                            applyProxyProperty("http.proxyPassword", authPassword, mproxy); //$NON-NLS-1$
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Cannot store the proxy password", e); //$NON-NLS-1$
                }
            }
        }
    }

    private static void applyPasswordAuthentication(final String authUser, final String authPassword) {
        Authenticator.setDefault(new Authenticator() {
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
            if (args[i].startsWith("$") && args[i].length() > 1) { //$NON-NLS-1$
                files = false;
                StringBuilder command = new StringBuilder(args[i].substring(1));
                // look for parameters
                while (i + 1 < length && !args[i + 1].startsWith("$")) { //$NON-NLS-1$
                    i++;
                    command.append(' ');
                    if (args[i].indexOf(' ') != -1 && !args[i].startsWith("\"")) { //$NON-NLS-1$
                        command.append("\""); //$NON-NLS-1$
                        command.append(args[i]);
                        command.append("\""); //$NON-NLS-1$
                    } else {
                        command.append(args[i]);
                    }
                }
                arguments.add(command.toString());
            }
        }

        if (files) {
            for (int i = 0; i < args.length; i++) {
                String val = args[i];
                // DICOM files
                if (val.startsWith("file:")) { //$NON-NLS-1$
                    try {
                        val = new File(new URI(args[i])).getPath();
                    } catch (URISyntaxException e) {
                        LOGGER.log(Level.SEVERE, "Convert URI to file", e); //$NON-NLS-1$
                    }

                }
                arguments.add("dicom:get -l \"" + val + "\""); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    private List<String> extractWeasisConfigArguments() {
        String configCmd = "weasis:config"; //$NON-NLS-1$
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

        String[] vals = argument.split("=", 2); //$NON-NLS-1$
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

            if (configServiceUri.getScheme().startsWith("file")) { //$NON-NLS-1$
                stream = new FileInputStream(new File(configServiceUri));
            } else {
                URLConnection urlConnection = FileUtil.getAdaptedConnection(new URI(configServicePath).toURL(), false);

                urlConnection.setRequestProperty("Accept", "application/xml"); //$NON-NLS-1$ //$NON-NLS-2$
                urlConnection.setConnectTimeout(Integer.valueOf(System.getProperty("UrlConnectionTimeout", "1000"))); //$NON-NLS-1$ //$NON-NLS-2$
                urlConnection.setReadTimeout(Integer.valueOf((System.getProperty("UrlReadTimeout", "2000")))); //$NON-NLS-1$ //$NON-NLS-2$

                if (urlConnection instanceof HttpURLConnection) {
                    HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
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
            LOGGER.log(Level.SEVERE, e, () -> String.format("Error Loading config service %s", configServicePath)); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(stream);
        }

        return null;

    }

    private Map<String, List<String>> readServiceConfigStream(XMLStreamReader xmler) throws XMLStreamException {

        Map<String, List<String>> configParams = new HashMap<>();

        while (xmler.hasNext()) {
            switch (xmler.next()) {
                case XMLStreamConstants.START_ELEMENT:
                    switch (xmler.getLocalName()) {
                        case "property": //$NON-NLS-1$
                            String name = xmler.getAttributeValue(null, "name"); //$NON-NLS-1$
                            String value = xmler.getAttributeValue(null, "value"); //$NON-NLS-1$
                            addConfigParam(configParams, PARAM_PROPERTY, String.format("%s %s", name, value)); //$NON-NLS-1$
                            break;
                        case "argument": //$NON-NLS-1$
                            addConfigParam(configParams, PARAM_ARGUMENT, xmler.getElementText());
                            break;
                    }
            }
        }
        return configParams;
    }

    /**
     * Reads application config files and compute WEASIS_CONFIG_HASH to check if those would have been updated.
     */

    public Properties loadConfigProperties() {
        URI propURI = getPropertiesURI(CONFIG_PROPERTIES_PROP, CONFIG_PROPERTIES_FILE_VALUE);
        Properties felixConfig = new Properties();
        // Read the properties file
        if (propURI != null) {
            configOutput.append("\n  Application configuration file = "); //$NON-NLS-1$
            configOutput.append(propURI);
            WeasisLauncher.readProperties(propURI, felixConfig);

        } else {
            LOGGER.log(Level.SEVERE, "No config.properties path found, Weasis cannot start!"); //$NON-NLS-1$
        }

        propURI = getPropertiesURI(EXTENDED_PROPERTIES_PROP, EXTENDED_PROPERTIES_FILE_VALUE);
        if (propURI != null) {
            configOutput.append("\n  Application extension configuration file = "); //$NON-NLS-1$
            configOutput.append(propURI);
            // Extended properties, add or override existing properties
            WeasisLauncher.readProperties(propURI, felixConfig);
        }
        checkMinimalVersion(felixConfig);

        if (felixConfig.isEmpty()) {
            throw new IllegalStateException("Cannot load weasis config!"); //$NON-NLS-1$
        }

        // Build a hash the properties just after reading. It will allow to compare with a new app instance.
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
                Version loc = new Version(localProps.getProperty(WeasisLauncher.P_WEASIS_VERSION).replaceFirst("-", ".")); //$NON-NLS-1$ //$NON-NLS-2$
                Version min = new Version(val.replaceFirst("-", ".")); //$NON-NLS-1$ //$NON-NLS-2$
                if (loc.compareTo(min) < 0) {
                    felixConfig.clear();
                    felixConfig.putAll(localProps);
                    propURI = getLocalPropertiesURI(EXTENDED_PROPERTIES_PROP, EXTENDED_PROPERTIES_FILE_VALUE);
                    WeasisLauncher.readProperties(propURI, felixConfig);
                    System.setProperty(WeasisLauncher.P_WEASIS_MIN_NATIVE_VERSION, val); 
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Cannot check compatibility with remote package", e); //$NON-NLS-1$
            }
        }
    }

    public URI getPropertiesURI(String configProp, String configFile) {
        // See if the property URL was specified as a property.
        URI propURL;
        String custom = properties.getProperty(configProp);
        if (Utils.hasText(custom)) {
            try {
                if (custom.startsWith("file:conf/")) { //$NON-NLS-1$
                    propURL = new File(findLocalCodebase(), custom.substring(5)).toURI();
                } else {
                    propURL = new URI(custom);
                }
            } catch (URISyntaxException e) {
                LOGGER.log(Level.SEVERE, configProp, e);
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
            LOGGER.log(Level.SEVERE, configFile, ex);
            return null;
        }
    }

    private static String toHex(int val) {
        final char[] hexDigit = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
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
        String classpath = System.getProperty("java.class.path"); //$NON-NLS-1$
        String[] vals = classpath.split(File.pathSeparator);
        for (String cp : vals) {
            if (cp.endsWith("weasis-launcher.jar")) { //$NON-NLS-1$
                jarLocation = cp;
            }
        }
        if (jarLocation == null) {
            return new File(ConfigData.class.getProtectionDomain().getCodeSource().getLocation().getPath())
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
        String osArch = System.getProperty("os.arch"); //$NON-NLS-1$
        if (Utils.hasText(osName) && Utils.hasText(osArch)) {
            if (osName.toLowerCase().startsWith("win")) { //$NON-NLS-1$
                // All Windows versions with a specific processor architecture (x86 or x86-64) are grouped under
                // windows. If you need to make different native libraries for the Windows versions, define it in the
                // Bundle-NativeCode tag of the bundle fragment.
                osName = "windows"; //$NON-NLS-1$
            } else if (osName.equals(WeasisLauncher.MAC_OS_X)) { // $NON-NLS-1$
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
            System.setProperty(WeasisLauncher.P_NATIVE_LIB_SPEC, osName + "-" + osArch); //$NON-NLS-1$
        }
    }

}
