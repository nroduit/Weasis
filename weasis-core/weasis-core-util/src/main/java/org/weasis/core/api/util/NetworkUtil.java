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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkUtil.class);

    private NetworkUtil() {
    }

    public static InputStream getUrlInputStream(URLConnection urlConnection) throws StreamIOException {
        return getUrlInputStream(urlConnection, 5000, 7000);
    }

    public static InputStream getUrlInputStream(URLConnection urlConnection, int connectTimeout, int readTimeout)
        throws StreamIOException {
        urlConnection.setConnectTimeout(connectTimeout);
        urlConnection.setReadTimeout(readTimeout);
        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            try {
                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    LOGGER.warn("http Status {} - {}", responseCode, httpURLConnection.getResponseMessage());// $NON-NLS-1$ //$NON-NLS-1$

                    // Following is only intended LOG more info about Http Server Error
                    if (LOGGER.isTraceEnabled()) {
                        writeErrorResponse(httpURLConnection);
                    }
                    throw new StreamIOException(httpURLConnection.getResponseMessage());
                }
            } catch (StreamIOException e) {
                throw e;
            } catch (IOException e) {
                LOGGER.error("http response: {}", e.getMessage()); //$NON-NLS-1$
                throw new StreamIOException(e);
            }
        }
        try {
            return urlConnection.getInputStream();
        } catch (IOException e) {
            throw new StreamIOException(e);
        }
    }

    private static void writeErrorResponse(HttpURLConnection httpURLConnection) throws IOException {
        InputStream errorStream = httpURLConnection.getErrorStream();
        if (errorStream != null) {
            try (InputStreamReader inputStream = new InputStreamReader(errorStream, "UTF-8"); //$NON-NLS-1$
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
                return URLEncoder.encode(e.getKey(), "UTF-8") + "=" + URLEncoder.encode(e.getValue(), "UTF-8");
            } catch (UnsupportedEncodingException e1) {
                throw new IllegalArgumentException(e1);
            }
        }).collect(Collectors.joining("&"));
    }
}
