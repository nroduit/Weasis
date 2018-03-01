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
package org.weasis.core.api.image.op;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import org.weasis.core.api.util.FileUtil;

/**
 * <code>ByteLutCollection</code> contains a collection of lookup tables (LUT).
 * <p>
 * Example of a lookup table applied to an image with JAI:<br>
 * <blockquote><code>LookupTableJAI lookup = new LookupTableJAI(ByteLutCollection.redToYellow);<br>
 * ParameterBlock pb = new ParameterBlock();<br>
 *    pb.addSource(image);<br>
 *    pb.add(lookup);<br>
 *    PlanarImage dst = JAI.create("lookup", pb, null);</code></blockquote>
 * <p>
 *
 */

public class ByteLutCollection {

    private ByteLutCollection() {
    }

    /**
     * the Flag LUT
     */
    public static final byte[][] flag = new byte[3][256];

    static {
        int[] r = { 255, 255, 0, 0 };
        int[] g = { 0, 255, 0, 0 };
        int[] b = { 0, 255, 255, 0 };
        for (int i = 0; i < 256; i++) {
            flag[0][i] = (byte) r[i % 4];
            flag[1][i] = (byte) g[i % 4];
            flag[2][i] = (byte) b[i % 4];
        }
    }

    /**
     * the Multi color LUT
     */
    public static final byte[][] multiColor = new byte[3][256];

    static {
        int[] r = { 255, 0, 255, 0, 255, 128, 64, 255, 0, 128, 236, 189, 250, 154, 221, 255, 128, 255, 0, 128, 228, 131,
            189, 0, 36, 66, 40, 132, 156, 135, 98, 194, 217, 251, 255, 0 };
        int[] g = { 3, 255, 245, 0, 0, 0, 128, 128, 0, 0, 83, 228, 202, 172, 160, 128, 128, 200, 187, 88, 93, 209, 89,
            255, 137, 114, 202, 106, 235, 85, 216, 226, 182, 247, 195, 173 };
        int[] b = { 0, 0, 55, 255, 255, 0, 64, 0, 128, 128, 153, 170, 87, 216, 246, 128, 64, 188, 236, 189, 39, 96, 212,
            255, 176, 215, 204, 221, 255, 70, 182, 84, 172, 176, 142, 95 };
        for (int i = 0; i < 256; i++) {
            int p = i % 36;
            multiColor[0][i] = (byte) r[p];
            multiColor[1][i] = (byte) g[p];
            multiColor[2][i] = (byte) b[p];
        }
    }

    /**
     * the IHS LUT
     */
    public static final byte[][] ihs = new byte[3][256];

    static {
        Color c;
        for (int i = 0; i < 256; i++) {
            c = Color.getHSBColor(i / 255f, 1f, 1f);
            ihs[0][i] = (byte) c.getRed();
            ihs[1][i] = (byte) c.getGreen();
            ihs[2][i] = (byte) c.getBlue();
        }
    }

    /**
     * the Gray levels LUT
     */
    public static final byte[][] grays = new byte[3][256];

    static {
        for (int i = 0; i < 256; i++) {
            grays[0][i] = (byte) i;
            grays[1][i] = (byte) i;
            grays[2][i] = (byte) i;
        }
    }

    /**
     * the Red LUT
     */
    public static final byte[][] red = new byte[3][256];

    static {
        for (int i = 0; i < 256; i++) {
            red[0][i] = (byte) i;
            red[1][i] = 0;
            red[2][i] = 0;
        }
    }

    /**
     * the Green LUT
     */
    public static final byte[][] green = new byte[3][256];

    static {
        for (int i = 0; i < 256; i++) {
            green[0][i] = 0;
            green[1][i] = (byte) i;
            green[2][i] = 0;
        }
    }

    /**
     * the Blue LUT
     */
    public static final byte[][] blue = new byte[3][256];

    static {
        for (int i = 0; i < 256; i++) {
            blue[0][i] = 0;
            blue[1][i] = 0;
            blue[2][i] = (byte) i;
        }
    }

    /**
     * Returns a new instance of a gray levels LUT.
     *
     * @return the gray levels LUT
     */
    public static byte[][] grays() {
        byte lut[][] = new byte[3][256];
        for (int i = 0; i < 256; i++) {
            lut[0][i] = (byte) i;
            lut[1][i] = (byte) i;
            lut[2][i] = (byte) i;
        }
        return lut;
    }

    public static List<ByteLut> getLutCollection() {
        List<ByteLut> luts = new ArrayList<>();
        luts.add(ByteLut.defaultLUT);
        luts.add(ByteLut.grayLUT);
        return luts;
    }

    public static byte[][] invert(byte[][] lut) {
        int bands = lut.length;
        int dynamic = lut[0].length - 1;
        byte[][] invertlut = new byte[bands][dynamic + 1];
        for (int j = 0; j < bands; j++) {
            for (int i = 0; i <= dynamic; i++) {
                invertlut[j][i] = lut[j][dynamic - i];
            }
        }
        return invertlut;
    }

    public static void readLutFilesFromResourcesDir(List<ByteLut> luts, File lutFolder) {
        if (luts != null && lutFolder != null && lutFolder.exists() && lutFolder.isDirectory()) {
            File[] files = lutFolder.listFiles();
            Scanner scan = null;
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile() && files[i].canRead()) {
                    try {
                        scan = new Scanner(files[i], "UTF-8"); //$NON-NLS-1$
                        byte[][] lut = readLutFile(scan); // $NON-NLS-1$
                        luts.add(new ByteLut(FileUtil.nameWithoutExtension(files[i].getName()), lut,
                            ByteLutCollection.invert(lut)));
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
    // public static void main(String[] args) {
    // List<ByteLut> luts = getLutCollection();
    // for (ByteLut byteLut : luts) {
    // File file = new File("/home/nicolas/Documents/luts", byteLut.getName() + ".txt");
    // FileWriter write;
    // try {
    // write = new FileWriter(file);
    //
    // byte[][] lut = byteLut.getLutTable();
    // int bands = lut.length;
    // int dynamic = lut[0].length;
    // for (int i = 0; i < dynamic; i++) {
    // for (int j = 0; j < bands; j++) {
    // if (j == bands - 1) {
    // write.write((0xFF & lut[j][i]) + "\n");
    // } else {
    // write.write((0xFF & lut[j][i]) + "\t");
    // }
    // }
    // }
    // write.close();
    // } catch (IOException e) {
    // // TODO Auto-generated catch block
    // e.printStackTrace();
    // }
    //
    // }
    // }
}
