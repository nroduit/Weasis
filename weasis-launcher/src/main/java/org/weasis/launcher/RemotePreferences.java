package org.weasis.launcher;

public abstract class RemotePreferences {
    private String user = null;
    private String profile = null;
    private String localPrefsDir = null;
    private boolean localSessionUser;

    final void initialize(String user, boolean localSessionUser, String profile, String preferencesDirectory) {
        this.user = user;
        this.localSessionUser = localSessionUser;
        this.profile = profile;
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

    public String getProfile() {
        return profile;
    }

    public boolean isLocalSessionUser() {
        return localSessionUser;
    }

}
