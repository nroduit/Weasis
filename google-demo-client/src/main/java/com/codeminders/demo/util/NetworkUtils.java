package com.codeminders.demo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;

public class NetworkUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkUtils.class);

    public static InputStream getUrlInputStream(URLConnection urlConnection) {
        return getUrlInputStream(urlConnection, 5000, 7000);
    }

    public static InputStream getUrlInputStream(URLConnection urlConnection, int connectTimeout, int readTimeout) {
        urlConnection.setConnectTimeout(connectTimeout);
        urlConnection.setReadTimeout(readTimeout);
        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            try {
                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    LOGGER.warn("http Status {} - {}", responseCode, httpURLConnection.getResponseMessage());// $NON-NLS-1$ //$NON-NLS-1$

                    throw new IllegalStateException(httpURLConnection.getResponseMessage());
                }
            } catch (IOException e) {
                LOGGER.error("http response: {}", e.getMessage()); //$NON-NLS-1$
                throw new IllegalStateException(e);
            }
        }
        try {
            return urlConnection.getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
