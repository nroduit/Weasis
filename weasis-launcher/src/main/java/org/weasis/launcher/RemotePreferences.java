package org.weasis.launcher;

import java.io.File;

public interface RemotePreferences {
    void read(File baseDir);

    void store();
}
