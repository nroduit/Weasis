/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec.display;

import java.io.File;
import java.util.List;
import java.util.Scanner;

import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.Messages;
import org.weasis.dicom.codec.internal.Activator;

public class LutManager {

    public static final String LUT_DIR = "luts"; //$NON-NLS-1$

    public static ByteLut[] getLutCollection() {
        List<ByteLut> luts = ByteLutCollection.getLutCollection();
        readLutFiles(luts);
        readLutFilesFromConfigDir(luts);
        return luts.toArray(new ByteLut[luts.size()]);
    }

    public static void readLutFiles(List<ByteLut> luts) {
        String[] files =
            { "BlackBody.txt", "Cardiac.txt", "Flow.txt", "GEcolor.txt", "GrayRainbow.txt", "Hue1.txt", "Hue2.txt", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
                "Stern.txt", "Ucla.txt", "VR Bones.txt" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        for (int i = 0; i < files.length; i++) {
            try {
                byte[][] lut =
                    readLutFile(new Scanner(LutManager.class.getResourceAsStream("/config/luts/" + files[i]), "UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
                luts.add(new ByteLut(FileUtil.nameWithoutExtension(files[i]), lut, ByteLutCollection.invert(lut)));
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public static void readLutFilesFromConfigDir(List<ByteLut> luts) {
        File lutFolder = new File(Activator.PREFERENCES.getDataFolder(), LUT_DIR);
        if (lutFolder.exists() && lutFolder.isDirectory()) {
            File[] files = lutFolder.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile() && files[i].canRead()) {
                    try {
                        byte[][] lut = readLutFile(new Scanner(files[i], "UTF-8")); //$NON-NLS-1$
                        luts.add(new ByteLut(FileUtil.nameWithoutExtension(files[i].getName()), lut, ByteLutCollection
                            .invert(lut)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static byte[][] readLutFile(Scanner scan) throws Exception {
        final byte lut[][] = new byte[3][256];
        int lineIndex = 0;

        while (scan.hasNext()) {
            if (lineIndex >= 256) {
                break;
            }

            String[] line = scan.nextLine().split("\t"); //$NON-NLS-1$
            if (line.length == 3) {
                lut[0][lineIndex] = (byte) Integer.parseInt(line[0]);
                lut[1][lineIndex] = (byte) Integer.parseInt(line[1]);
                lut[2][lineIndex] = (byte) Integer.parseInt(line[2]);
            }

            lineIndex++;
        }
        return lut;
    }
}
