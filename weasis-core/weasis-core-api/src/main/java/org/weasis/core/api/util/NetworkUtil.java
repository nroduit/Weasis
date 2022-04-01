/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.util.StreamIOException;
import org.weasis.core.util.StringUtil;

public class NetworkUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(NetworkUtil.class);

  private static final int MAX_REDIRECTS = 3;

  private NetworkUtil() {}

  public static int getUrlConnectionTimeout() {
    return StringUtil.getInt(System.getProperty("UrlConnectionTimeout"), 5000);
  }

  public static int getUrlReadTimeout() {
    return StringUtil.getInt(System.getProperty("UrlReadTimeout"), 15000);
  }

  public static URI getURI(String pathOrUri) throws MalformedURLException, URISyntaxException {
    URI uri = null;
    if (!pathOrUri.startsWith("http")) { // NON-NLS
      try {
        File file = new File(pathOrUri);
        if (file.canRead()) {
          uri = file.toURI();
        }
      } catch (Exception e) {
        // Do nothing
      }
    }
    if (uri == null) {
      uri = new URL(pathOrUri).toURI();
    }
    return uri;
  }

  public static HttpResponse getHttpResponse(
      String url, URLParameters urlParameters, AuthMethod authMethod) throws IOException {
    return getHttpResponse(url, urlParameters, authMethod, null);
  }

  public static HttpResponse getHttpResponse(
      String url, URLParameters urlParameters, AuthMethod authMethod, OAuthRequest authRequest)
      throws IOException {
    if (authMethod == null || OAuth2ServiceFactory.noAuth.equals(authMethod)) {
      return prepareConnection(new URL(url).openConnection(), urlParameters);
    }
    OAuthRequest request;
    request =
        Objects.requireNonNullElseGet(
            authRequest,
            () -> new OAuthRequest(urlParameters.isHttpPost() ? Verb.POST : Verb.GET, url));
    return prepareAuthConnection(request, urlParameters, authMethod);
  }

  public static ClosableURLConnection getUrlConnection(String url, URLParameters urlParameters)
      throws IOException {
    return prepareConnection(new URL(url).openConnection(), urlParameters);
  }

  public static ClosableURLConnection getUrlConnection(URL url, URLParameters urlParameters)
      throws IOException {
    return prepareConnection(url.openConnection(), urlParameters);
  }

  private static void updateHeadersWithAppProperties(URLConnection urlConnection) {
    urlConnection.setRequestProperty("User-Agent", AppProperties.WEASIS_USER_AGENT);
    urlConnection.setRequestProperty("Weasis-User", AppProperties.WEASIS_USER.trim().toUpperCase());
  }

  private static AuthResponse prepareAuthConnection(
      OAuthRequest request, URLParameters urlParameters, AuthMethod authMethod) throws IOException {
    Map<String, String> headers = urlParameters.getUnmodifiableHeaders();
    if (!headers.isEmpty()) {
      for (Entry<String, String> element : headers.entrySet()) {
        request.addHeader(element.getKey(), element.getValue());
      }
    }
    request.addHeader("User-Agent", AppProperties.WEASIS_USER_AGENT); // NON-NLS
    request.addHeader("Weasis-User", AppProperties.WEASIS_USER.trim().toUpperCase()); // NON-NLS

    try {
      OAuth20Service service = OAuth2ServiceFactory.getService(authMethod);
      if (service == null) {
        throw new IllegalStateException("Not a valid authentication method: " + authMethod);
      }
      service.signRequest(authMethod.getToken(), request);
      return new AuthResponse(service.execute(request));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StreamIOException(e);
    } catch (Exception e) {
      throw new StreamIOException(e);
    }
  }

  private static ClosableURLConnection prepareConnection(
      URLConnection urlConnection, URLParameters urlParameters) throws StreamIOException {
    Map<String, String> headers = urlParameters.getUnmodifiableHeaders();

    if (!headers.isEmpty()) {
      for (Entry<String, String> element : headers.entrySet()) {
        urlConnection.setRequestProperty(element.getKey(), element.getValue());
      }
    }

    updateHeadersWithAppProperties(urlConnection);

    urlConnection.setConnectTimeout(urlParameters.getConnectTimeout());
    urlConnection.setReadTimeout(urlParameters.getReadTimeout());
    urlConnection.setAllowUserInteraction(urlParameters.isAllowUserInteraction());
    urlConnection.setUseCaches(urlParameters.isUseCaches());
    urlConnection.setIfModifiedSince(urlParameters.getIfModifiedSince());
    urlConnection.setDoInput(true);
    if (urlParameters.isHttpPost()) {
      urlConnection.setDoOutput(true);
    }
    if (urlConnection instanceof HttpURLConnection httpURLConnection) {
      try {
        if (urlParameters.isHttpPost()) {
          httpURLConnection.setRequestMethod("POST");
        } else {
          return new ClosableURLConnection(readResponse(httpURLConnection, headers));
        }
      } catch (StreamIOException e) {
        throw e;
      } catch (IOException e) {
        throw new StreamIOException(e);
      }
    }
    return new ClosableURLConnection(urlConnection);
  }

  public static URLConnection readResponse(
      HttpURLConnection httpURLConnection, Map<String, String> headers) throws IOException {
    int code = httpURLConnection.getResponseCode();
    if (code < HttpURLConnection.HTTP_OK || code >= HttpURLConnection.HTTP_MULT_CHOICE) {
      if (code == HttpURLConnection.HTTP_MOVED_TEMP
          || code == HttpURLConnection.HTTP_MOVED_PERM
          || code == HttpURLConnection.HTTP_SEE_OTHER) {
        return applyRedirectionStream(httpURLConnection, headers);
      }

      LOGGER.warn("http Status {} - {}", code, httpURLConnection.getResponseMessage());

      // Following is only intended LOG more info about Http Server Error
      if (LOGGER.isTraceEnabled()) {
        writeErrorResponse(httpURLConnection);
      }
      throw new StreamIOException(httpURLConnection.getResponseMessage());
    }
    return httpURLConnection;
  }

  public static String read(URLConnection urlConnection) throws IOException {
    urlConnection.connect();
    try (BufferedReader in =
        new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
      StringBuilder body = new StringBuilder();
      String inputLine;

      while ((inputLine = in.readLine()) != null) {
        body.append(inputLine);
      }
      return body.toString();
    }
  }

  public static URLConnection applyRedirectionStream(
      URLConnection urlConnection, Map<String, String> headers) throws IOException {
    URLConnection c = urlConnection;
    String redirect = c.getHeaderField("Location");
    for (int i = 0; i < MAX_REDIRECTS; i++) {
      if (redirect != null) {
        String cookies = c.getHeaderField("Set-Cookie");
        if (c instanceof HttpURLConnection httpURLConnection) {
          httpURLConnection.disconnect();
        }
        c = new URL(redirect).openConnection();
        c.setRequestProperty("Cookie", cookies);
        if (headers != null && headers.size() > 0) {
          for (Entry<String, String> element : headers.entrySet()) {
            c.addRequestProperty(element.getKey(), element.getValue());
          }
        }
        redirect = c.getHeaderField("Location");
      } else {
        break;
      }
    }
    return c;
  }

  public static boolean urlValidator(String url) {
    try {
      new URL(url).toURI();
      return true;
    } catch (URISyntaxException | MalformedURLException exception) {
      return false;
    }
  }

  private static void writeErrorResponse(HttpURLConnection httpURLConnection) throws IOException {
    InputStream errorStream = httpURLConnection.getErrorStream();
    if (errorStream != null) {
      try (InputStreamReader inputStream =
              new InputStreamReader(errorStream, StandardCharsets.UTF_8);
          BufferedReader reader = new BufferedReader(inputStream)) {
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          stringBuilder.append(line);
        }
        String errorDescription = stringBuilder.toString();
        if (StringUtil.hasText(errorDescription)) {
          LOGGER.trace("HttpURLConnection ERROR, server response: {}", errorDescription);
        }
      }
    }
  }
}
