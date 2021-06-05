/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.internal;

public class ProxyUtils {

  public static final String HTTP_PROXY_HOST_PROPERTY = "http.proxyHost";
  public static final String HTTP_PROXY_PORT_PROPERTY = "http.proxyPort";
  public static final String FTP_PROXY_HOST_PROPERTY = "ftp.proxyHost";
  public static final String FTP_PROXY_PORT_PROPERTY = "ftp.proxyPort";
  public static final String HTTPS_PROXY_HOST_PROPERTY = "https.proxyHost";
  public static final String HTTPS_PROXY_PORT_PROPERTY = "https.proxyPort";
  public static final String SOCKS_PROXY_HOST_PROPERTY = "socksProxyHost";
  public static final String SOCKS_PROXY_PORT_PROPERTY = "socksProxyPort";
  public static final String DEPLOYMENT_PROXY_HTTP_HOST = "deployment.proxy.http.host";
  public static final String DEPLOYMENT_PROXY_HTTP_PORT = "deployment.proxy.http.port";
  public static final String DEPLOYMENT_PROXY_HTTPS_HOST = "deployment.proxy.https.host";
  public static final String DEPLOYMENT_PROXY_HTTPS_PORT = "deployment.proxy.https.port";
  public static final String DEPLOYMENT_PROXY_FTP_HOST = "deployment.proxy.ftp.host";
  public static final String DEPLOYMENT_PROXY_FTP_PORT = "deployment.proxy.ftp.port";
  public static final String DEPLOYMENT_PROXY_SOCKS_HOST = "deployment.proxy.socks.host";
  public static final String DEPLOYMENT_PROXY_SOCKS_PORT = "deployment.proxy.socks.port";
  public static final String DEPLOYMENT_PROXY_BYPASS_LIST = "deployment.proxy.bypass.list";
  public static final String DIRECT_CONNECTION_PROPERTY = "javaplugin.proxy.config.type";

  private ProxyUtils() {}

  public static void setProxyFromJavaWebStart() {
    setProperties(DEPLOYMENT_PROXY_HTTP_HOST, HTTP_PROXY_HOST_PROPERTY);
    setProperties(DEPLOYMENT_PROXY_HTTP_PORT, HTTP_PROXY_PORT_PROPERTY);
    setProperties(DEPLOYMENT_PROXY_HTTPS_HOST, HTTPS_PROXY_HOST_PROPERTY);
    setProperties(DEPLOYMENT_PROXY_HTTPS_PORT, HTTPS_PROXY_PORT_PROPERTY);
    setProperties(DEPLOYMENT_PROXY_FTP_HOST, FTP_PROXY_HOST_PROPERTY);
    setProperties(DEPLOYMENT_PROXY_FTP_PORT, FTP_PROXY_PORT_PROPERTY);
    setProperties(DEPLOYMENT_PROXY_SOCKS_HOST, SOCKS_PROXY_HOST_PROPERTY);
    setProperties(DEPLOYMENT_PROXY_SOCKS_PORT, SOCKS_PROXY_PORT_PROPERTY);

    String nonProxyHosts = System.getProperty("deployment.proxy.bypass.list");
    String nonProxyLocal = System.getProperty("deployment.proxy.bypass.local");
    if (nonProxyHosts != null) {
      nonProxyHosts = nonProxyHosts.replace(';', '|');
      if (Boolean.parseBoolean(nonProxyLocal)) {
        nonProxyHosts += "|localhost";
      }
      System.setProperty("http.nonProxyHosts", nonProxyHosts);
    }
  }

  private static void setProperties(String javawsProperties, String javaProperties) {
    String prop = System.getProperty(javawsProperties);
    if (prop != null) {
      System.setProperty(javaProperties, prop);
    }
  }
}
