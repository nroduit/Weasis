package org.weasis.launcher;

public abstract class RemotePreferences {
    private String user = null;
    private String localPrefsDir = null;

    final void initialize(String user, String preferencesDirectory) {
        this.user = user;
        this.localPrefsDir = preferencesDirectory;
    }

    public abstract void read();

    public abstract void store();

    public final String getUser() {
        return user;
    }

    public final String getLocalPrefsDir() {
        return localPrefsDir;
    }

}
