/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.media;

import java.io.File;

import javax.swing.filechooser.FileSystemView;

public class FileSystemDrive extends MediaSource {

    public FileSystemDrive(String locationPath) {
        super(locationPath);

        File locationFile = new File(locationPath);
        if (!locationFile.isDirectory()) {
            throw new IllegalArgumentException(locationPath + "is not valid directory"); //$NON-NLS-1$
        }

        File sysRootFile = locationFile;
        while (sysRootFile.getParentFile() != null) {
            sysRootFile = sysRootFile.getParentFile();
        }

        FileSystemView fsv = FileSystemView.getFileSystemView();

        displayName = fsv.getSystemDisplayName(sysRootFile) + " - " + locationFile.getPath(); //$NON-NLS-1$
        description = fsv.getSystemTypeDescription(sysRootFile);
        icon = fsv.getSystemIcon(sysRootFile);
    }

}
