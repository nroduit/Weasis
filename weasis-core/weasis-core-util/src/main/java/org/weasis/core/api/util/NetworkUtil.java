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
package org.weasis.core.api.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

    private static final String UTF_8 = "UTF-8";
    private static final int MAX_REDIRECTS = 3;

    private NetworkUtil() {
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

    public static InputStream getUrlInputStream(URLConnection urlConnection) throws StreamIOException {
        return getUrlInputStream(urlConnection, null);
    }

    public static InputStream getUrlInputStream(URLConnection urlConnection, Map<String, String> headers)
        throws StreamIOException {
        return getUrlInputStream(urlConnection, headers,
            StringUtil.getInt(System.getProperty("UrlConnectionTimeout"), 5000), //$NON-NLS-1$
            StringUtil.getInt(System.getProperty("UrlReadTimeout"), 15000)); //$NON-NLS-1$
    }

    public static InputStream getUrlInputStream(URLConnection urlConnection, Map<String, String> headers,
        int connectTimeout, int readTimeout) throws StreamIOException {
        prepareConnection(urlConnection, headers, connectTimeout, readTimeout, false);
        try {
            return urlConnection.getInputStream();
        } catch (IOException e) {
            throw new StreamIOException(e);
        }

    }

    public static OutputStream getUrlOutputStream(URLConnection urlConnection, Map<String, String> headers)
        throws StreamIOException {
        return getUrlOutputStream(urlConnection, headers,
            StringUtil.getInt(System.getProperty("UrlConnectionTimeout"), 5000), //$NON-NLS-1$
            StringUtil.getInt(System.getProperty("UrlReadTimeout"), 15000)); //$NON-NLS-1$
    }

    public static OutputStream getUrlOutputStream(URLConnection urlConnection, Map<String, String> headers,
        int connectTimeout, int readTimeout) throws StreamIOException {
        prepareConnection(urlConnection, headers, connectTimeout, readTimeout, true);
        try {
            return urlConnection.getOutputStream();
        } catch (IOException e) {
            throw new StreamIOException(e);
        }
    }

    private static void prepareConnection(URLConnection urlConnection, Map<String, String> headers, int connectTimeout,
        int readTimeout, boolean post) throws StreamIOException {
        if (headers != null && headers.size() > 0) {
            for (Iterator<Entry<String, String>> iter = headers.entrySet().iterator(); iter.hasNext();) {
                Entry<String, String> element = iter.next();
                urlConnection.addRequestProperty(element.getKey(), element.getValue());
            }
        }
        urlConnection.setConnectTimeout(connectTimeout);
        urlConnection.setReadTimeout(readTimeout);
        urlConnection.setDoInput(true);
        if (post) {
            urlConnection.setDoOutput(true);
        }
        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            try {
                if (post) {
                    httpURLConnection.setRequestMethod("POST"); //$NON-NLS-1$
                } else {
                    int code = httpURLConnection.getResponseCode();
                    if (code < HttpURLConnection.HTTP_OK || code >= HttpURLConnection.HTTP_MULT_CHOICE) {
                        if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM
                            || code == HttpURLConnection.HTTP_SEE_OTHER) {
                            applyRedirectionStream(httpURLConnection, headers);
                            return;
                        }

                        LOGGER.warn("http Status {} - {}", code, httpURLConnection.getResponseMessage());// $NON-NLS-1$ //$NON-NLS-1$

                        // Following is only intended LOG more info about Http Server Error
                        if (LOGGER.isTraceEnabled()) {
                            writeErrorResponse(httpURLConnection);
                        }
                        throw new StreamIOException(httpURLConnection.getResponseMessage());
                    }
                }
            } catch (StreamIOException e) {
                throw e;
            } catch (IOException e) {
                throw new StreamIOException(e);
            }
        }
    }

    public static void applyRedirectionStream(URLConnection urlConnection, Map<String, String> headers)
        throws IOException {
        String redirect = urlConnection.getHeaderField("Location"); //$NON-NLS-1$
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            if (redirect != null) {
                String cookies = urlConnection.getHeaderField("Set-Cookie"); //$NON-NLS-1$
                urlConnection = new URL(redirect).openConnection();
                urlConnection.setRequestProperty("Cookie", cookies); //$NON-NLS-1$
                if (headers != null && headers.size() > 0) {
                    for (Iterator<Entry<String, String>> iter = headers.entrySet().iterator(); iter.hasNext();) {
                        Entry<String, String> element = iter.next();
                        urlConnection.addRequestProperty(element.getKey(), element.getValue());
                    }
                }
                redirect = urlConnection.getHeaderField("Location"); //$NON-NLS-1$
            } else {
                break;
            }
        }
    }

    private static void writeErrorResponse(HttpURLConnection httpURLConnection) throws IOException {
        InputStream errorStream = httpURLConnection.getErrorStream();
        if (errorStream != null) {
            try (InputStreamReader inputStream = new InputStreamReader(errorStream, StandardCharsets.UTF_8); //$NON-NLS-1$
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
                return URLEncoder.encode(e.getKey(), UTF_8) + "=" + URLEncoder.encode(e.getValue(), UTF_8); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            } catch (UnsupportedEncodingException e1) {
                throw new IllegalArgumentException(e1);
            }
        }).collect(Collectors.joining("&")); //$NON-NLS-1$
    }
}
