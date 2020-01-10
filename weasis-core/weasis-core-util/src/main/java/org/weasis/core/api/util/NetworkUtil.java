/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkUtil.class);

    private static final String UTF_8 = "UTF-8"; //$NON-NLS-1$
    private static final int MAX_REDIRECTS = 3;

    private NetworkUtil() {
    }

    public static int getUrlConnectionTimeout() {
        return StringUtil.getInt(System.getProperty("UrlConnectionTimeout"), 5000); //$NON-NLS-1$
    }

    public static int getUrlReadTimeout() {
        return StringUtil.getInt(System.getProperty("UrlReadTimeout"), 15000); //$NON-NLS-1$
    }

    public static URI getURI(String pathOrUri) throws MalformedURLException, URISyntaxException {
        URI uri = null;
        if (!pathOrUri.startsWith("http")) { //$NON-NLS-1$
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

    public static ClosableURLConnection getUrlConnection(String url, URLParameters urlParameters) throws IOException {
        return prepareConnection(new URL(url).openConnection(), urlParameters);
    }

    public static ClosableURLConnection getUrlConnection(URL url, URLParameters urlParameters) throws IOException {
        return prepareConnection(url.openConnection(), urlParameters);
    }

    private static ClosableURLConnection prepareConnection(URLConnection urlConnection, URLParameters urlParameters)
        throws StreamIOException {
        Map<String, String> headers = urlParameters.getHeaders();
        if (headers != null && headers.size() > 0) {
            for (Iterator<Entry<String, String>> iter = headers.entrySet().iterator(); iter.hasNext();) {
                Entry<String, String> element = iter.next();
                urlConnection.setRequestProperty(element.getKey(), element.getValue());
            }
        }
        urlConnection.setConnectTimeout(urlParameters.getConnectTimeout());
        urlConnection.setReadTimeout(urlParameters.getReadTimeout());
        urlConnection.setAllowUserInteraction(urlParameters.isAllowUserInteraction());
        urlConnection.setUseCaches(urlParameters.isUseCaches());
        urlConnection.setIfModifiedSince(urlParameters.getIfModifiedSince());
        urlConnection.setDoInput(true);
        if (urlParameters.isHttpPost()) {
            urlConnection.setDoOutput(true);
        }
        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            try {
                if (urlParameters.isHttpPost()) {
                    httpURLConnection.setRequestMethod("POST"); //$NON-NLS-1$
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

    public static URLConnection readResponse(HttpURLConnection httpURLConnection, Map<String, String> headers)
        throws IOException {
        int code = httpURLConnection.getResponseCode();
        if (code < HttpURLConnection.HTTP_OK || code >= HttpURLConnection.HTTP_MULT_CHOICE) {
            if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_SEE_OTHER) {
                return applyRedirectionStream(httpURLConnection, headers);
            }

            LOGGER.warn("http Status {} - {}", code, httpURLConnection.getResponseMessage());// $NON-NLS-1$ //$NON-NLS-1$

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
        try (BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
            StringBuilder body = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                body.append(inputLine);
            }
            return body.toString();
        }
    }

    public static URLConnection applyRedirectionStream(URLConnection urlConnection, Map<String, String> headers)
        throws IOException {
        URLConnection c = urlConnection;
        String redirect = c.getHeaderField("Location"); //$NON-NLS-1$
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            if (redirect != null) {
                String cookies = c.getHeaderField("Set-Cookie"); //$NON-NLS-1$
                if (c instanceof HttpURLConnection) {
                    ((HttpURLConnection) c).disconnect();
                }
                c = new URL(redirect).openConnection();
                c.setRequestProperty("Cookie", cookies); //$NON-NLS-1$
                if (headers != null && headers.size() > 0) {
                    for (Iterator<Entry<String, String>> iter = headers.entrySet().iterator(); iter.hasNext();) {
                        Entry<String, String> element = iter.next();
                        c.addRequestProperty(element.getKey(), element.getValue());
                    }
                }
                redirect = c.getHeaderField("Location"); //$NON-NLS-1$
            } else {
                break;
            }
        }
        return c;
    }

    private static void writeErrorResponse(HttpURLConnection httpURLConnection) throws IOException {
        InputStream errorStream = httpURLConnection.getErrorStream();
        if (errorStream != null) {
            try (InputStreamReader inputStream = new InputStreamReader(errorStream, StandardCharsets.UTF_8); // $NON-NLS-1$
                            BufferedReader reader = new BufferedReader(inputStream)) {
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                String errorDescription = stringBuilder.toString();
                if (StringUtil.hasText(errorDescription)) {
                    LOGGER.trace("HttpURLConnection ERROR, server response: {}", //$NON-NLS-1$
                        errorDescription);
                }
            }
        }
    }

    public static String buildHttpParamsString(Map<String, String> params) {
        return params.entrySet().stream().map(e -> {
            try {
                return URLEncoder.encode(e.getKey(), UTF_8) + "=" + URLEncoder.encode(e.getValue(), UTF_8); //$NON-NLS-1$
            } catch (UnsupportedEncodingException e1) {
                throw new IllegalArgumentException(e1);
            }
        }).collect(Collectors.joining("&")); //$NON-NLS-1$
    }
}
