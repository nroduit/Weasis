package org.weasis.launcher;


public abstract class RemotePreferences {
    private String user = null;
    private String server = null;
    private String prefsPath = null;

    final void initialize() {
        user = System.getProperty("weasis.user", null);
        server = System.getProperty("preferences.server.url", null);
        prefsPath = System.getProperty("weasis.path", null);
    }

    public abstract void read();

    public abstract void store();

    public final String getUser() {
        return user;
    }

    public final String getServer() {
        return server;
    }

    public final String getPrefsPath() {
        return prefsPath;
    }

}
