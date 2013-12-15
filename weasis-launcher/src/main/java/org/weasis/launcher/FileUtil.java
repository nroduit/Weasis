/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.launcher;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Properties;

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

    public static final void deleteDirectoryContents(final File dir, int deleteDirLevel, int level) {
        if ((dir == null) || !dir.isDirectory()) {
            return;
        }
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File f : files) {
                if (f.isDirectory()) {
                    deleteDirectoryContents(f, deleteDirLevel, level + 1);
                } else {
                    try {
                        f.delete();
                    } catch (Exception e) {
                        // Do nothing, wait next start to delete it
                    }
                }
            }
        }
        if (level >= deleteDirLevel) {
            try {
                dir.delete();
            } catch (Exception e) {
                // Do nothing, wait next start to delete it
            }
        }
    }

    public static File getApplicationTempDir() {
        String tempDir = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
        File tdir;
        if (tempDir == null || tempDir.length() == 1) {
            String dir = System.getProperty("user.home", ""); //$NON-NLS-1$ //$NON-NLS-2$
            tdir = new File(dir);
        } else {
            tdir = new File(tempDir);
        }
        return new File(tdir, "weasis-" + System.getProperty("user.name", "tmp")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public static void storeProperties(File propsFile, Properties props, String comments) {
        if (props != null && propsFile != null) {
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(propsFile);
                props.store(fout, comments);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                FileUtil.safeClose(fout);
            }
        }
    }

    public static void writeFile(InputStream inputStream, OutputStream out) {
        if (inputStream == null || out == null) {
            return;
        }
        try {
            byte[] buf = new byte[4096];
            int offset;
            while ((offset = inputStream.read(buf)) > 0) {
                out.write(buf, 0, offset);
            }
            out.flush();
        } catch (IOException e) {
            System.err.println("Error when writing file"); //$NON-NLS-1$
        }

        finally {
            FileUtil.safeClose(inputStream);
            FileUtil.safeClose(out);
        }
    }

    public static void writeLogoFiles(String srcPath, String outputDir) {
        String[] files = { "about.png", "logo-button.png" };
        for (String lf : files) {
            try {
                URL url = new URL(srcPath + "/" + lf);
                writeFile(url.openStream(), new FileOutputStream(new File(outputDir, lf)));
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }
}
