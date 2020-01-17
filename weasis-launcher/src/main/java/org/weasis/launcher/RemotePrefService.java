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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RemotePrefService {

    public static final String P_REMOTE_PREF_USER = "user"; //$NON-NLS-1$
    public static final String P_REMOTE_PREF_PROFILE = "profile"; //$NON-NLS-1$

    private static final String TEXT_X_JAVA_PROP = "text/x-java-properties"; //$NON-NLS-1$

    private static final Logger LOGGER = Logger.getLogger(RemotePrefService.class.getName());

    private final String remotePrefURL;
    private final String user;
    private final String profile;
    private final boolean localSessionUser;
    private final boolean storeLocalSession;

    public RemotePrefService(String url, Map<String, String> serverProp, String user, String profile) {
        this.remotePrefURL = Objects.requireNonNull(url);
        this.user = Objects.requireNonNull(user);
        this.localSessionUser = Utils.getEmptytoFalse(serverProp.get("weasis.pref.local.session")); //$NON-NLS-1$
        this.storeLocalSession = Utils.getEmptytoFalse(serverProp.get("weasis.pref.store.local.session")); //$NON-NLS-1$
        this.profile = Objects.requireNonNull(profile);
    }

    public final String getUser() {
        return user;
    }

    public String getProfile() {
        return profile;
    }

    public boolean isLocalSessionUser() {
        return localSessionUser;
    }

    private String getEncodedValue(String val) throws UnsupportedEncodingException {
        return URLEncoder.encode(val, "UTF-8"); //$NON-NLS-1$
    }

    private String getRemoteLauncherUrl() throws UnsupportedEncodingException {
        return String.format("%s?%s=%s&%s=%s", remotePrefURL, P_REMOTE_PREF_USER, getEncodedValue(user), //$NON-NLS-1$
            P_REMOTE_PREF_PROFILE, getEncodedValue(profile));
    }

    public Properties readLauncherPref(Properties props) throws IOException {
        Properties p = props == null ? new Properties() : props;
        if (!localSessionUser || storeLocalSession) {
            readRemoteProperties(p);
        }
        return p;
    }

    private void readRemoteProperties(Properties props) throws IOException {
        String remoteURL = getRemoteLauncherUrl();
        URLConnection prefSv = FileUtil.getAdaptedConnection(new URL(remoteURL), false);
        prefSv.setRequestProperty("Accept", TEXT_X_JAVA_PROP); //$NON-NLS-1$
        prefSv.setConnectTimeout(7000);
        prefSv.setReadTimeout(10000);
        // Do not write if not content (HTTP_NO_CONTENT)
        if (prefSv instanceof HttpURLConnection
            && ((HttpURLConnection) prefSv).getResponseCode() == HttpURLConnection.HTTP_OK) {
            try (InputStream is = prefSv.getInputStream()) {
                props.load(is);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e, () -> String.format("Loading %s", remoteURL)); //$NON-NLS-1$
            }
        }
    }
}
