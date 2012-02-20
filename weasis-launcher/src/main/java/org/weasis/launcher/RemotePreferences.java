package org.weasis.launcher;

public abstract class RemotePreferences {
    private String user = null;
    private String localPrefsDir = null;

    final void initialize(String user, String preferencesDirectory) {
        this.user = user;
        this.localPrefsDir = preferencesDirectory;
    }

    public abstract void read() throws Exception;

    public abstract void store() throws Exception;

    public final String getUser() {
        return user;
    }

    public final String getLocalPrefsDir() {
        return localPrefsDir;
    }

}
