package org.weasis.dicom.viewer2d;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import org.weasis.core.api.util.FileUtil;

public class RawImage {
    private File file;
    private FileOutputStream outputStream;

    public RawImage(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public FileOutputStream getOutputStream() throws FileNotFoundException {
        if (outputStream == null) {
            outputStream = new FileOutputStream(file);
        }
        return outputStream;
    }

    public void disposeOutputStream() {
        if (outputStream != null) {
            FileUtil.safeClose(outputStream);
            outputStream = null;
        }
    }
}