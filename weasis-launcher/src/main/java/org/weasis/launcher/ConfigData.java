package org.weasis.launcher;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ConfigData {

    private static final Logger LOGGER = Logger.getLogger(ConfigData.class.getName());

    private final List<String> arguments;
    private final Properties properties;
    private String codebase;
    private String codebaseExt;
    private String authorization;

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

        applyConfigServiceParams();
    }

    private void applyConfigServiceParams() {
        List<String> cmdArgs = getConfigServiceParams();
        Pattern pattern = Pattern.compile("\\s+"); //$NON-NLS-1$
        for (int i = 0; i < cmdArgs.size(); i++) {
            String arg = cmdArgs.get(i);
            String[] vals = arg.split("=", 2);
            if (vals.length != 2) {
                continue;
            }

            String key = vals[0];
            String val = Utils.removeEnglobingQuotes(vals[1]);

            if (key.equals("wcfg")) { //$NON-NLS-1$
                // TODO implement
                // checkRemoteConfig(data);
            } else if (key.equals("cdb")) { //$NON-NLS-1$
                setCodebase(val);
            } else if (key.equals("cdb-ext")) { //$NON-NLS-1$
                setCodebaseExt(val);
            } else if (key.equals("auth")) { //$NON-NLS-1$
                setAuthorization(val);
            } else if (key.equals("arg")) { //$NON-NLS-1$
                addArgument(val);
            } else if (key.equals("pro")) { //$NON-NLS-1$
                String[] res = pattern.split(val, 2);
                if (res.length == 2) {
                    addProperty(res[0], res[1]);
                } else {
                    LOGGER.log(Level.WARNING, "Cannot parse property: {0}", val); //$NON-NLS-1$
                }
            }
        }
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

    public String getAuthorization() {
        return authorization;
    }

    public void setAuthorization(String authorization) {
        this.authorization = authorization;
    }

    public String getCodebase() {
        return codebase;
    }

    public void setCodebase(String codebase) {
        this.codebase = codebase;
    }

    public String getCodebaseExt() {
        return codebaseExt;
    }

    public void setCodebaseExt(String codebaseExt) {
        this.codebaseExt = codebaseExt;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public Properties getProperties() {
        return properties;
    }

    public void addArgument(String arg) {
        if (arg != null) {
            arguments.add(arg);
        }
    }

    public void addProperty(String key, String val) {
        if (key != null) {
            properties.put(key, val);
        }
    }

    public boolean isPropertyValueSimilar(ConfigData data, String prop) {
        String p = properties.getProperty(Objects.requireNonNull(prop));
        String p2 = Objects.requireNonNull(data).getProperties().getProperty(prop);
        return Objects.equals(p, p2);
    }

    public String getSourceID() {
        String cdb = codebase == null ? "local" : codebase; //$NON-NLS-1$
        cdb += properties.getProperty(WeasisLauncher.P_WEASIS_PROFILE, "default");
        return toHex(cdb.hashCode());
    }

    public void applyConfig() {
        String cdb = getCodebase();
        if (Utils.hasText(cdb)) {
            System.setProperty(WeasisLauncher.P_WEASIS_CODEBASE_URL, cdb);
            System.setProperty(WeasisLauncher.CONFIG_PROPERTIES_PROP, cdb + "/conf/config.properties"); //$NON-NLS-1$
        }

        String cdbExt = getCodebaseExt();
        if (Utils.hasText(cdbExt)) {
            System.setProperty(WeasisLauncher.EXTENDED_PROPERTIES_PROP, cdbExt + "/conf/ext-config.properties"); //$NON-NLS-1$
        }

        String auth = getAuthorization();
        if (Utils.hasText(auth)) {
            System.setProperty("http.authorization", auth); //$NON-NLS-1$
        }

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
                if(val.startsWith("file:")){
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

    private List<String> getConfigServiceParams() {
        String configCmd = "weasis:config"; //$NON-NLS-1$
        for (String cmd : arguments) {
            if (cmd.startsWith(configCmd) && cmd.length() > configCmd.length() + 2) {
                arguments.remove(cmd);
                return Utils.splitSpaceExceptInQuotes(cmd.substring(configCmd.length() + 1));
            }
        }
        return Collections.emptyList();
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
