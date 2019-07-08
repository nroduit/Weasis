package org.weasis.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

public class ConfigData {

    private static final Logger LOGGER = Logger.getLogger(ConfigData.class.getName());

    // Params to be used in
    public static final String PARAM_CONFIG_URL = "wcfg"; // >> stands for configServicePath

    public static final String PARAM_ARGUMENT = "arg";
    public static final String PARAM_PROPERTY = "pro";

    public static final String PARAM_CODEBASE = "cdb"; // idem as property "pro=weasis.codebase.url+{value}"
    public static final String PARAM_CODEBASE_EXT = "cdb-ext"; // idem as property "pro=weasis.codebase.ext.url+{value}"
    public static final String PARAM_AUTHORIZATION = "auth"; // idem as property "pro=http.authorization+{value}"

    private final List<String> arguments;
    private final Properties properties;

    private String configServicePath;

    public ConfigData() {
        this.arguments = new ArrayList<>();
        this.properties = new Properties();
    }

    public ConfigData(String[] args) {
        this();
        init(args);
    }

    public void init(String[] args) {
        if (args == null) {
            return;
        }

        this.clear();

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

        applyConfigParams();
    }

    private void extractArgFromUri(String uri) {
        try {
            String url = URLDecoder.decode(uri, "UTF-8"); //$NON-NLS-1$
            String[] cmds = url.split("\\$"); //$NON-NLS-1$
            if (cmds.length > 0) {
                for (int i = 1; i < cmds.length; i++) {
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
     * Note that any command launch argument take priority over serviceConfig parameters. <br>
     * Calling this method will have no effect if called more than once per init(args) call
     */

    private void applyConfigParams() {

        List<String> configArgs = extractWeasisConfigArguments();
        applyConfigParams(getConfigParamsFromArgs(configArgs));

        applyConfigParams(getConfigParamsFromServicePath());

        // TODO ## codeBase/ext properties should be resolved here when not given in order to properly set sourceID
        // afterwards

        String codeBaseUrl = properties.getProperty(WeasisLauncher.P_WEASIS_CODEBASE_URL, "");

        if (!properties.containsKey(WeasisLauncher.CONFIG_PROPERTIES_PROP) && !codeBaseUrl.isEmpty()) {

            String configProp = String.format("%s/%s/%s", codeBaseUrl, WeasisLauncher.CONFIG_DIRECTORY,
                WeasisLauncher.CONFIG_PROPERTIES_FILE_VALUE);
            addProperty(WeasisLauncher.CONFIG_PROPERTIES_PROP, configProp);
        }

        String codeBaseExtUrl = properties.getProperty(WeasisLauncher.P_WEASIS_CODEBASE_EXT_URL, "");

        if (!properties.containsKey(WeasisLauncher.EXTENDED_PROPERTIES_PROP) && !codeBaseExtUrl.isEmpty()) {

            String extConfigProp = String.format("%s/%s/%s", codeBaseExtUrl, WeasisLauncher.CONFIG_DIRECTORY,
                WeasisLauncher.EXTENDED_PROPERTIES_FILE_VALUE);
            addProperty(WeasisLauncher.EXTENDED_PROPERTIES_PROP, extConfigProp);
        }

        // TODO ## shouldn't applyConfigToSystemProperties() be called here rather than from WeasisLauncher.launch()
    }

    private void applyConfigParams(Map<String, List<String>> configParams) {
        if (configParams == null)
            return;

        configParams.forEach((k, v) -> {
            switch (k) {
                case PARAM_CONFIG_URL:
                    setConfigServicePath(v.get(0));
                    break;
                case PARAM_CODEBASE:
                    addProperty(WeasisLauncher.P_WEASIS_CODEBASE_URL, v.get(0));
                    break;
                case PARAM_CODEBASE_EXT:
                    addProperty(WeasisLauncher.P_WEASIS_CODEBASE_EXT_URL, v.get(0));
                    break;
                case PARAM_AUTHORIZATION:
                    addProperty(WeasisLauncher.P_HTTP_AUTHORIZATION, v.get(0));
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

    public String getConfigServicePath() {
        return configServicePath;
    }

    private void setConfigServicePath(String configServicePath) {
        this.configServicePath = configServicePath;
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
        if (Utils.hasText(key)) {
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

    public boolean isPropertyValueSimilar(ConfigData data, String prop) {
        String p = properties.getProperty(Objects.requireNonNull(prop));
        String p2 = Objects.requireNonNull(data).getProperties().getProperty(prop);
        return Objects.equals(p, p2);
    }

    public String getSourceID() {

        // TODO ## sourceID should be based on CONFIG_PROPERTIES_PROP and rather than P_WEASIS_CODEBASE_URL
        // >> finally P_WEASIS_CODEBASE_URL represent a bundle repo more than a specific profile and version config
        // >> reference when launching weasis

        String codebase = properties.getProperty(WeasisLauncher.P_WEASIS_CODEBASE_URL, "");

        String cdb = codebase == null ? "local" : codebase; //$NON-NLS-1$
        cdb += properties.getProperty(WeasisLauncher.P_WEASIS_PROFILE, "default");
        return toHex(cdb.hashCode());
    }

    private void clear() {
        arguments.clear();
        properties.clear();

        this.configServicePath = null;
    }

    public void applyConfigToSystemProperties() {

        for (String key : properties.stringPropertyNames()) {
            System.setProperty(key, properties.getProperty(key));
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
                    if (args[i].indexOf(' ') != -1 && !args[i].startsWith("\"")) {
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
                if (val.startsWith("file:")) {
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
        Map<String, List<String>> configParams = new HashMap<String, List<String>>();
        Objects.requireNonNull(configArguments, "configArguments cannot be null")
            .forEach(a -> addConfigParam(configParams, a));

        return configParams;
    }

    private void addConfigParam(Map<String, List<String>> configParams, String argument) {
        if (argument == null)
            return;

        String[] vals = argument.split("=", 2);
        if (vals.length != 2)
            return;

        addConfigParam(configParams, vals[0], vals[1]);
    }

    private void addConfigParam(Map<String, List<String>> configParams, String name, String value) {
        Objects.requireNonNull(configParams, "configParams cannot be null");

        if (name == null || value == null)
            return;

        List<String> paramList = configParams.get(name);

        if (paramList == null)
            configParams.put(name, paramList = new LinkedList<String>());

        paramList.add(Utils.removeEnglobingQuotes(value));
    }

    private Map<String, List<String>> getConfigParamsFromServicePath() {

        if (!Utils.hasText(configServicePath))
            return null;

        InputStream stream = null;
        try {
            URI configServiceUri = new URI(configServicePath);

            if (configServiceUri.getScheme().startsWith("file"))
                stream = new FileInputStream(new File(configServiceUri));
            else {
                URLConnection urlConnection = FileUtil.getAdaptedConnection(new URI(configServicePath).toURL(), false);

                urlConnection.setRequestProperty("Accept", "application/xml");
                urlConnection.setConnectTimeout(Integer.valueOf(System.getProperty("UrlConnectionTimeout", "1000")));
                urlConnection.setReadTimeout(Integer.valueOf((System.getProperty("UrlReadTimeout", "2000"))));

                if (urlConnection instanceof HttpURLConnection) {
                    HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
                    if (httpURLConnection.getResponseCode() != HttpURLConnection.HTTP_OK)
                        throw new IOException(httpURLConnection.getResponseMessage());
                    // TODO ## redirection stream is not handled
                    // @see weasis.core.api.util.NetworkUtil.applyRedirectionStream()
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

        Map<String, List<String>> configParams = new HashMap<String, List<String>>();

        while (xmler.hasNext()) {
            switch (xmler.next()) {
                case XMLStreamConstants.START_ELEMENT:
                    switch (xmler.getLocalName()) {
                        case "property":
                            String name = xmler.getAttributeValue(null, "name");
                            String value = xmler.getAttributeValue(null, "value");
                            addConfigParam(configParams, PARAM_PROPERTY, String.format("%s %s", name, value));
                            break;
                        case "argument":
                            addConfigParam(configParams, PARAM_ARGUMENT, xmler.getElementText());
                            break;
                    }
            }
        }
        return configParams;
    }

    private static String toHex(int val) {
        final char[] hexDigit = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
        char[] ch8 = new char[8];
        for (int i = 8; --i >= 0; val >>= 4) {
            ch8[i] = hexDigit[val & 0xf];
        }
        return String.valueOf(ch8);
    }
}
