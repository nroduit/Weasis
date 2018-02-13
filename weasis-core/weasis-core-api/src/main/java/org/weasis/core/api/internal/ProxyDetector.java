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
package org.weasis.core.api.internal;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Detecting and selecting a proxy
 *
 *
 */

public class ProxyDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyDetector.class);

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

    private static final String PROXY_PROPERTY = "java.net.useSystemProxies"; //$NON-NLS-1$
    private static final ProxyDetector INSTANCE = new ProxyDetector();

    private final List<Proxy> proxies;
    private final Proxy proxyToUse;

    private ProxyDetector() {
        this.proxies = initProxies();
        this.proxyToUse = determineProxy();
    }

    /**
     *
     * @return the instance
     */

    public static ProxyDetector getInstance() {
        return INSTANCE;
    }

    /**
     *
     * Find the proxy, use the property <code>java.net.useSystemProxies</code> to force the usage of the system proxy.
     * The value of this setting is restored afterwards.
     *
     *
     *
     * @return a list of found proxies
     */

    private static List<Proxy> initProxies() {

        final String valuePropertyBefore = System.getProperty(PROXY_PROPERTY);
        System.setProperty(PROXY_PROPERTY, "true"); //$NON-NLS-1$

        try {
            return ProxySelector.getDefault().select(new java.net.URI("http://www.google.com")); //$NON-NLS-1$
        } catch (Exception e) {
            LOGGER.error("Cannot find proxy configuration", e); //$NON-NLS-1$
        } finally {
            if (valuePropertyBefore != null) {
                System.setProperty(PROXY_PROPERTY, valuePropertyBefore);
            }
        }
        return Collections.emptyList();
    }

    /**
     *
     * Is there a direct connection available? If I return <tt>true</tt> it is not necessary to detect a proxy address.
     *
     *
     *
     * @return <tt>true</tt> if the is a direct connection to the internet
     */

    public boolean directConnectionAvailable() {
        for (Proxy proxy : this.proxies) {
            if (Proxy.NO_PROXY.equals(proxy)) {
                return true;
            }
        }

        return false;

    }

    /**
     *
     * @return did we detect a proxy?
     */

    public boolean proxyDetected() {
        return this.proxyToUse != null;
    }

    /**
     *
     * I will determine the right proxy, there might be several proxies available, but some might not support the HTTP
     * protocol.
     *
     *
     *
     * @return a proxy which can be used to access the given url, <tt>null</tt> if there is no proxy which supports
     *         HTTP.
     */

    private Proxy determineProxy() {
        if (!directConnectionAvailable()) {
            for (Proxy proxy : this.proxies) {
                if (proxy.type().equals(Proxy.Type.HTTP)) {
                    return proxy;
                }
            }
        }
        return null;

    }

    public Proxy getHttpProxy() {
        return this.proxyToUse == null ? Proxy.NO_PROXY : proxyToUse;
    }

    /**
     *
     * @return a String representing the hostname of the proxy, <tt>null</tt> if there is no proxy
     */

    public String getHostname() {
        if (this.proxyToUse != null) {
            final SocketAddress socketAddress = this.proxyToUse.address();
            if (socketAddress instanceof InetSocketAddress) {
                InetSocketAddress address = (InetSocketAddress) socketAddress;
                return address.getHostName();
            }
        }
        return null;
    }

    /**
     *
     * @return the port of the proxy, <tt>-1</tt> if there is no proxy
     */

    public int getPort() {
        if (this.proxyToUse != null) {
            final SocketAddress socketAddress = this.proxyToUse.address();
            if (socketAddress instanceof InetSocketAddress) {
                InetSocketAddress address = (InetSocketAddress) socketAddress;
                return address.getPort();
            }
        }
        return -1;

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
