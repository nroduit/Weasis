package org.weasis.acquire.explorer.media;

import java.io.File;

import javax.swing.filechooser.FileSystemView;

public class FileSystemDrive extends MediaSource {

    public FileSystemDrive(String locationPath) {
        super(locationPath);

        File locationFile = new File(locationPath);
        if (!locationFile.isDirectory()) {
            throw new IllegalArgumentException(locationPath + "is not valid directory");
        }

        File sysRootFile = locationFile;
        while (sysRootFile.getParentFile() != null) {
            sysRootFile = sysRootFile.getParentFile();
        }

        FileSystemView fsv = FileSystemView.getFileSystemView();

        displayName = fsv.getSystemDisplayName(sysRootFile) + " - " + locationFile.getPath();
        description = fsv.getSystemTypeDescription(sysRootFile);
        icon = fsv.getSystemIcon(sysRootFile);
    }

}
