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

import java.io.DataOutputStream;
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

    private static final String TEXT_X_JAVA_PROP = "text/x-java-properties";

    private static final Logger LOGGER = Logger.getLogger(RemotePrefService.class.getName());

    private final String remotePrefURL;
    private final String user;
    private final String profile;
    private final boolean localSessionUser;
    private final boolean storeLocalSession;

    public RemotePrefService(Map<String, String> serverProp, String user, String profile) {
        String url = serverProp.get(WeasisLauncher.P_WEASIS_PREFS_URL);
        this.remotePrefURL = Objects.requireNonNull(url).endsWith("/") ? url : url + "/";
        this.user = Objects.requireNonNull(user);
        this.localSessionUser = Utils.geEmptytoTrue(serverProp.get("weasis.pref.local.session"));
        this.storeLocalSession = Utils.getEmptytoFalse(serverProp.get("weasis.pref.store.local.session"));
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
        return URLEncoder.encode(val, "UTF-8");
    }

    private String getRemoteLauncherUrl() throws UnsupportedEncodingException {
        return String.format("%spreferences?user=%s&profile=%s", remotePrefURL, getEncodedValue(user),
            getEncodedValue(profile));
    }

    public void readLauncherPref(Properties props) throws IOException {
        if (!localSessionUser || storeLocalSession) {
            readRemoteProperties(props);
        }
    }

    private void readRemoteProperties(Properties props) throws IOException {
        String remoteURL = getRemoteLauncherUrl();
        URLConnection prefSv = FileUtil.getAdaptedConnection(new URL(remoteURL), false);
        prefSv.setRequestProperty("Accept", TEXT_X_JAVA_PROP);
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

    public Properties storeLauncherPref(Properties props) throws IOException {
        if (!localSessionUser || storeLocalSession) {
            Properties remoteProps = new Properties();
            readRemoteProperties(remoteProps);
            if (!remoteProps.equals(props)) {
                remoteProps.putAll(props);
                HttpURLConnection prefSv =
                    (HttpURLConnection) FileUtil.getAdaptedConnection(new URL(getRemoteLauncherUrl()), false);
                prefSv.setRequestProperty("Content-Type", TEXT_X_JAVA_PROP);
                prefSv.setConnectTimeout(7000);
                prefSv.setReadTimeout(10000);
                prefSv.setDoOutput(true);
                prefSv.setDoInput(true);
                prefSv.setRequestMethod("POST"); //$NON-NLS-1$
                DataOutputStream out = new DataOutputStream(prefSv.getOutputStream());
                remoteProps.store(out, null);
                return remoteProps;
            }
        }
        return null;
    }
}
