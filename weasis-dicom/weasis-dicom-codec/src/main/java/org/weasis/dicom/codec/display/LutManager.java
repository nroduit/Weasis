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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;

public class LutManager {

    public static final String LUT_DIR = "luts"; //$NON-NLS-1$

    public static ByteLut[] getLutCollection() {
        List<ByteLut> luts = new ArrayList<ByteLut>();
        luts.add(ByteLut.grayLUT);
        readLutFilesFromResourcesDir(luts);
        luts.add(0, ByteLut.defaultLUT);
        return luts.toArray(new ByteLut[luts.size()]);
    }

    public static void readLutFilesFromResourcesDir(List<ByteLut> luts) {
        File lutFolder = ResourceUtil.getResource(LUT_DIR);
        if (lutFolder.exists() && lutFolder.isDirectory()) {
            File[] files = lutFolder.listFiles();
            Scanner scan = null;
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile() && files[i].canRead()) {
                    try {
                        scan = new Scanner(files[i], "UTF-8");
                        byte[][] lut = readLutFile(new Scanner(files[i], "UTF-8")); //$NON-NLS-1$
                        luts.add(new ByteLut(FileUtil.nameWithoutExtension(files[i].getName()), lut, ByteLutCollection
                            .invert(lut)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (scan != null) {
                            scan.close();
                        }
                    }
                }
            }
            Collections.sort(luts, new Comparator<ByteLut>() {

                @Override
                public int compare(ByteLut o1, ByteLut o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
        }
    }

    public static byte[][] readLutFile(Scanner scan) throws Exception {
        final byte lut[][] = new byte[3][256];
        int lineIndex = 0;

        while (scan.hasNext()) {
            if (lineIndex >= 256) {
                break;
            }

            String[] line = scan.nextLine().split("\\s+"); //$NON-NLS-1$
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
