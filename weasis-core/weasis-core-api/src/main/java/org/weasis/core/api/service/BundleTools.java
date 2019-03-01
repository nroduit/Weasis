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
package org.weasis.core.api.service;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.StringUtil;

public class BundleTools {
    private static final Logger LOGGER = LoggerFactory.getLogger(BundleTools.class);

    public static final String P_FORMAT_CODE = "locale.format.code"; //$NON-NLS-1$

    public static final Map<String, String> SESSION_TAGS_MANIFEST = new HashMap<>(3);
    public static final Map<String, String> SESSION_TAGS_FILE = new HashMap<>(3);

    static {
        for (Iterator<Entry<Object, Object>> iter = System.getProperties().entrySet().iterator(); iter.hasNext();) {
            Entry<Object, Object> element = iter.next();
            String tag = element.getKey().toString();
            if (tag.startsWith("TGM-")) { //$NON-NLS-1$
                SESSION_TAGS_MANIFEST.put(tag.substring(4), element.getValue().toString());
            } else if (tag.startsWith("TGF-")) { //$NON-NLS-1$
                SESSION_TAGS_FILE.put(tag.substring(4), element.getValue().toString());
            }
        }
    }

    public static final String CONFIRM_CLOSE = "weasis.confirm.closing"; //$NON-NLS-1$
    public static final List<Codec> CODEC_PLUGINS = Collections.synchronizedList(new ArrayList<Codec>());

    public static final WProperties SYSTEM_PREFERENCES = new WProperties();
    public static final WProperties INIT_SYSTEM_PREFERENCES = new WProperties();
    private static final File propsFile;
    @Deprecated
    public static final WProperties LOCAL_PERSISTENCE = new WProperties();

    static {
        String prefPath = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.pref.dir"); //$NON-NLS-1$
        propsFile = new File(prefPath, "weasis.properties"); //$NON-NLS-1$
        readSystemPreferences();

        if (!propsFile.canRead()) {
            try {
                propsFile.createNewFile();
            } catch (IOException e) {
                LOGGER.error("", e); //$NON-NLS-1$
            }
        }
        String code = BundleTools.SYSTEM_PREFERENCES.getProperty(BundleTools.P_FORMAT_CODE);
        if (StringUtil.hasLength(code)) {
            Locale l = LocalUtil.textToLocale(code);
            if (!l.equals(Locale.getDefault())) {
                LocalUtil.setLocaleFormat(l);
            }
        }

        String path = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.resources.path"); //$NON-NLS-1$
        ResourceUtil.setResourcePath(path);
    }

    private BundleTools() {
    }

    public static Codec getCodec(String mimeType, String preferredCodec) {
        Codec codec = null;
        synchronized (BundleTools.CODEC_PLUGINS) {
            for (Codec c : BundleTools.CODEC_PLUGINS) {
                if (c.isMimeTypeSupported(mimeType)) {
                    if (c.getCodecName().equals(preferredCodec)) {
                        codec = c;
                        break;
                    }
                    // If the preferred codec cannot be find, the first-found codec is retained
                    if (codec == null) {
                        codec = c;
                    }
                }
            }
            return codec;
        }
    }

    private static void readSystemPreferences() {
        boolean notContent = false;
        SYSTEM_PREFERENCES.clear();
        String remotePrefURL = getServiceUrl();
        if (remotePrefURL != null) {
            readRemoteProperties(SYSTEM_PREFERENCES, remotePrefURL);
            if (SYSTEM_PREFERENCES.isEmpty()) {
                notContent = true;
            }
        }

        if (SYSTEM_PREFERENCES.isEmpty()) {
            FileUtil.readProperties(propsFile, SYSTEM_PREFERENCES);
        }
        resetInitProperties();
        if (notContent) {
            INIT_SYSTEM_PREFERENCES.setProperty("no.content", "true");
        }
    }

    public static void saveSystemPreferences() {
        String remotePrefURL = getServiceUrl();
        if (!SYSTEM_PREFERENCES.equals(INIT_SYSTEM_PREFERENCES)) {
            if (remotePrefURL == null) {
                FileUtil.storeProperties(propsFile, SYSTEM_PREFERENCES, null);
                resetInitProperties();
            } else {
                try {
                    Properties remoteProps = storeLauncherPref(SYSTEM_PREFERENCES, remotePrefURL);
                    if (remoteProps != null) {
                        FileUtil.storeProperties(propsFile, remoteProps, null);
                        SYSTEM_PREFERENCES.putAll(remoteProps);
                        resetInitProperties();
                    }
                } catch (Exception e) {
                    LOGGER.error("Cannot store Launcher preference for user: {}", AppProperties.WEASIS_USER, e); //$NON-NLS-1$
                }
            }
        }
    }

    public static String getServiceUrl() {
        String remotePrefURL = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.pref.url"); //$NON-NLS-1$
        if (remotePrefURL != null && !remotePrefURL.endsWith("/")) {
            remotePrefURL = remotePrefURL + "/";
        }
        return remotePrefURL;
    }

    public static boolean isLocalSession() {
        return BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.pref.local.session", true); //$NON-NLS-1$
    }

    public static boolean isStoreLocalSession() {
        return BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.pref.store.local.session", false); //$NON-NLS-1$
    }
    
    public static String getEncodedValue(String val) throws UnsupportedEncodingException {
        return URLEncoder.encode(val, "UTF-8");
    }

    private static void resetInitProperties() {
        INIT_SYSTEM_PREFERENCES.clear();
        INIT_SYSTEM_PREFERENCES.putAll(SYSTEM_PREFERENCES);
    }

    private static Properties storeLauncherPref(Properties props, String remotePrefURL) throws IOException {
        if (!isLocalSession() || isStoreLocalSession()) {
            String sURL = String.format("%spreferences?user=%s&profile=%s", remotePrefURL,
                getEncodedValue(AppProperties.WEASIS_USER), getEncodedValue(AppProperties.WEASIS_PROFILE));
            Properties remoteProps = new Properties();
            readRemoteProperties(remoteProps, remotePrefURL);
            if (!remoteProps.equals(props)) {
                remoteProps.putAll(props);
                OutputStream out = NetworkUtil.getUrlOutputStream(new URL(sURL).openConnection(), getHttpTags(true));
                remoteProps.store(new DataOutputStream(out), null);
                return remoteProps;
            }
        }
        return null;
    }

    private static void readRemoteProperties(Properties remoteProps, String remotePrefURL) {
        try {
            String sURL = String.format("%spreferences?user=%s&profile=%s", remotePrefURL,
                getEncodedValue(AppProperties.WEASIS_USER), getEncodedValue(AppProperties.WEASIS_PROFILE));
            URLConnection prefSv = new URL(sURL).openConnection();

            try (InputStream input = NetworkUtil.getUrlInputStream(prefSv, getHttpTags(false))) {
                // Do not write if not content (HTTP_NO_CONTENT)
                if (prefSv instanceof HttpURLConnection
                    && ((HttpURLConnection) prefSv).getResponseCode() == HttpURLConnection.HTTP_OK) {
                    remoteProps.load(input);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Cannot load Launcher preference for user: {}", AppProperties.WEASIS_USER, e); //$NON-NLS-1$
        }
    }

    private static Map<String, String> getHttpTags(boolean post) {
        HashMap<String, String> map = new HashMap<>(BundleTools.SESSION_TAGS_FILE);
        map.put(post ? "Content-Type" : "Accept", "text/x-java-properties");
        return map;
    }
}
