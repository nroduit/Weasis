package org.weasis.core.api.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkUtil.class);

    private NetworkUtil() {
    }
    public static InputStream getUrlInputStream(URLConnection urlConnection) throws StreamIOException {
        return getUrlInputStream(urlConnection, 5000, 10000);
    }
    public static InputStream getUrlInputStream(URLConnection urlConnection, int connectTimeout, int readTimeout) throws StreamIOException {
        urlConnection.setConnectTimeout(connectTimeout);
        urlConnection.setReadTimeout(readTimeout);
        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            try {
                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    LOGGER.warn("HttpURLConnection Status {} - {}", responseCode,
                        httpURLConnection.getResponseMessage());// $NON-NLS-1$

                    // Following is only intended LOG more info about Http Server Error
                    if (LOGGER.isTraceEnabled()) {
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
                }
            } catch (IOException e) {
                LOGGER.error("lOG http response:{}", e.getMessage()); //$NON-NLS-1$
                throw new StreamIOException(e);
            }
        }
        try {
            return urlConnection.getInputStream();
        } catch (IOException e) {
            throw new StreamIOException(e);
        }
    }
}
