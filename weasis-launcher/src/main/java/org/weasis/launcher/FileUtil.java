package org.weasis.launcher;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import javax.imageio.stream.ImageInputStream;

public class FileUtil {

    public static void safeClose(final Closeable object) {
        try {
            if (object != null) {
                object.close();
            }
        } catch (IOException e) {
            // Do nothing
        }
    }

    public static void safeClose(ImageInputStream stream) {
        try {
            if (stream != null) {
                stream.flush();
                stream.close();
            }
        } catch (IOException e) {
            // Do nothing
        }
    }

    public final static void deleteDirectoryContents(final File dir) {
        if ((dir == null) || !dir.isDirectory()) {
            return;
        }
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File f : files) {
                if (f.isDirectory()) {
                    deleteDirectoryContents(f);
                } else {
                    try {
                        f.delete();
                    } catch (Exception e) {
                        // Do nothing, wait next start to delete it
                    }
                }
            }
        }
    }

    public static File getApplicationTempDir() {
        String tempDir = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
        File tdir;
        if (tempDir == null || tempDir.length() == 1) {
            String dir = System.getProperty("user.home"); //$NON-NLS-1$
            if (dir == null) {
                dir = ""; //$NON-NLS-1$
            }
            tdir = new File(dir);
        } else {
            tdir = new File(tempDir);
        }
        return new File(tdir, "weasis"); //$NON-NLS-1$
    }

}
