package org.weasis.base.explorer;

import java.io.File;
import java.io.FilenameFilter;

public class FolderFilter implements FilenameFilter {

    public boolean accept(final File dir, final String fname) {
        if (fname.startsWith(".")) {
            return false;
        }
        if (new File(dir, fname).isDirectory()) {
            return true;
        }
        return false;
    }
}