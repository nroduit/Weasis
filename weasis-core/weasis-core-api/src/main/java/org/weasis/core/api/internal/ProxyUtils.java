/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.internal;


public class ProxyUtils {

    public static final String HTTP_PROXY_HOST_PROPERTY = "http.proxyHost"; //$NON-NLS-1$
    public static final String HTTP_PROXY_PORT_PROPERTY = "http.proxyPort"; //$NON-NLS-1$
    public static final String FTP_PROXY_HOST_PROPERTY = "ftp.proxyHost"; //$NON-NLS-1$
    public static final String FTP_PROXY_PORT_PROPERTY = "ftp.proxyPort"; //$NON-NLS-1$
    public static final String HTTPS_PROXY_HOST_PROPERTY = "https.proxyHost"; //$NON-NLS-1$
    public static final String HTTPS_PROXY_PORT_PROPERTY = "https.proxyPort"; //$NON-NLS-1$
    public static final String SOCKS_PROXY_HOST_PROPERTY = "socksProxyHost"; //$NON-NLS-1$
    public static final String SOCKS_PROXY_PORT_PROPERTY = "socksProxyPort"; //$NON-NLS-1$
    public static final String DEPLOYMENT_PROXY_HTTP_HOST = "deployment.proxy.http.host"; //$NON-NLS-1$
    public static final String DEPLOYMENT_PROXY_HTTP_PORT = "deployment.proxy.http.port"; //$NON-NLS-1$
    public static final String DEPLOYMENT_PROXY_HTTPS_HOST = "deployment.proxy.https.host"; //$NON-NLS-1$
    public static final String DEPLOYMENT_PROXY_HTTPS_PORT = "deployment.proxy.https.port"; //$NON-NLS-1$
    public static final String DEPLOYMENT_PROXY_FTP_HOST = "deployment.proxy.ftp.host"; //$NON-NLS-1$
    public static final String DEPLOYMENT_PROXY_FTP_PORT = "deployment.proxy.ftp.port"; //$NON-NLS-1$
    public static final String DEPLOYMENT_PROXY_SOCKS_HOST = "deployment.proxy.socks.host"; //$NON-NLS-1$
    public static final String DEPLOYMENT_PROXY_SOCKS_PORT = "deployment.proxy.socks.port"; //$NON-NLS-1$
    public static final String DEPLOYMENT_PROXY_BYPASS_LIST = "deployment.proxy.bypass.list"; //$NON-NLS-1$
    public static final String DIRECT_CONNECTION_PROPERTY = "javaplugin.proxy.config.type"; //$NON-NLS-1$

    private ProxyUtils() {
    }

    public static void setProxyFromJavaWebStart() {
        setProperties(DEPLOYMENT_PROXY_HTTP_HOST, HTTP_PROXY_HOST_PROPERTY);
        setProperties(DEPLOYMENT_PROXY_HTTP_PORT, HTTP_PROXY_PORT_PROPERTY);
        setProperties(DEPLOYMENT_PROXY_HTTPS_HOST, HTTPS_PROXY_HOST_PROPERTY);
        setProperties(DEPLOYMENT_PROXY_HTTPS_PORT, HTTPS_PROXY_PORT_PROPERTY);
        setProperties(DEPLOYMENT_PROXY_FTP_HOST, FTP_PROXY_HOST_PROPERTY);
        setProperties(DEPLOYMENT_PROXY_FTP_PORT, FTP_PROXY_PORT_PROPERTY);
        setProperties(DEPLOYMENT_PROXY_SOCKS_HOST, SOCKS_PROXY_HOST_PROPERTY);
        setProperties(DEPLOYMENT_PROXY_SOCKS_PORT, SOCKS_PROXY_PORT_PROPERTY);

        String nonProxyHosts = System.getProperty("deployment.proxy.bypass.list"); //$NON-NLS-1$
        String nonProxyLocal = System.getProperty("deployment.proxy.bypass.local"); //$NON-NLS-1$
        if (nonProxyHosts != null) {
            nonProxyHosts = nonProxyHosts.replace(';', '|');
            if (Boolean.parseBoolean(nonProxyLocal)) {
                nonProxyHosts += "|localhost"; //$NON-NLS-1$
            }
            System.setProperty("http.nonProxyHosts", nonProxyHosts); //$NON-NLS-1$
        }
    }

    private static void setProperties(String javawsProperties, String javaProperties) {
        String prop = System.getProperty(javawsProperties);
        if (prop != null) {
            System.setProperty(javaProperties, prop);
        }
    }
}
