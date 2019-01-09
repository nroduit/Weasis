package com.codeminders.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class GoogleAuthStub {

    private static Logger LOGGER = LoggerFactory.getLogger(GoogleAuthStub.class);

    private static volatile String authToken;

    public static String getAuthToken() {
        return authToken;
    }

    public static void setAuthToken(String newToken) {
        authToken = newToken;
    }

    public static URLConnection googleApiConnection(String url) {
        try {
            return googleApiConnection(new URL(url));
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Failed parse url " + url, ex);
        }
    }

    public static URLConnection googleApiConnection(URL url) {
        try {
            URLConnection connection = url.openConnection();
            connection.addRequestProperty("Authorization", "Bearer " + GoogleAuthStub.getAuthToken());
            return connection;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create connection to " + url, ex);
        }
    }

}
