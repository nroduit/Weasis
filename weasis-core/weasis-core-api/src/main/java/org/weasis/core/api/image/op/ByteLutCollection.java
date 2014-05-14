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
package org.weasis.core.api.image.op;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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
 * 
 * @author Nicolas Roduit
 * @version 1.2.0
 * @since 1.0.0
 */

public class ByteLutCollection {

    private ByteLutCollection() {
    }

    // /**
    // * the Red to yellow LUT
    // */
    // public static final byte redToYellow[][] = new byte[3][256];
    // static {
    // int[] g =
    // { 0, 0, 2, 3, 4, 4, 6, 6, 7, 9, 9, 11, 12, 12, 14, 14, 15, 17, 17, 19, 20, 20, 22, 22, 23, 25, 25, 26, 27,
    // 28, 30, 31, 32, 32, 33, 34, 35, 37, 38, 39, 40, 40, 41, 42, 44, 45, 46, 47, 47, 48, 49, 51, 52, 53, 54,
    // 54, 55, 56, 57, 59, 60, 61, 61, 62, 63, 64, 66, 67, 68, 69, 69, 70, 71, 73, 74, 75, 76, 76, 77, 78, 79,
    // 81, 82, 83, 83, 84, 85, 86, 88, 89, 90, 91, 91, 92, 93, 95, 96, 97, 98, 98, 99, 100, 102, 103, 104,
    // 105, 105, 106, 107, 108, 110, 111, 112, 112, 113, 114, 115, 117, 118, 119, 120, 120, 121, 122, 124,
    // 125, 126, 127, 127, 128, 129, 130, 132, 133, 134, 134, 135, 136, 137, 139, 140, 141, 142, 142, 143,
    // 144, 146, 147, 148, 149, 149, 150, 151, 153, 154, 155, 156, 156, 157, 158, 159, 161, 162, 163, 163,
    // 164, 165, 166, 168, 169, 170, 171, 171, 172, 173, 175, 176, 177, 178, 178, 179, 180, 181, 183, 184,
    // 185, 185, 186, 187, 188, 190, 191, 192, 193, 193, 194, 195, 197, 198, 199, 200, 200, 201, 202, 204,
    // 205, 206, 207, 207, 208, 209, 210, 212, 213, 214, 214, 215, 216, 217, 219, 220, 221, 222, 222, 223,
    // 224, 226, 227, 228, 229, 229, 230, 231, 232, 234, 235, 236, 236, 237, 238, 239, 241, 242, 243, 244,
    // 244, 245, 246, 248, 249, 250, 251, 251, 252, 253, 255 };
    //
    // for (int i = 0; i < 256; i++) {
    // redToYellow[0][i] = (byte) 255;
    // redToYellow[1][i] = (byte) g[i];
    // redToYellow[2][i] = (byte) 0;
    // }
    //
    // }

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
        int[] r =
            { 255, 0, 255, 0, 255, 128, 64, 255, 0, 128, 236, 189, 250, 154, 221, 255, 128, 255, 0, 128, 228, 131, 189,
                0, 36, 66, 40, 132, 156, 135, 98, 194, 217, 251, 255, 0 };
        int[] g =
            { 3, 255, 245, 0, 0, 0, 128, 128, 0, 0, 83, 228, 202, 172, 160, 128, 128, 200, 187, 88, 93, 209, 89, 255,
                137, 114, 202, 106, 235, 85, 216, 226, 182, 247, 195, 173 };
        int[] b =
            { 0, 0, 55, 255, 255, 0, 64, 0, 128, 128, 153, 170, 87, 216, 246, 128, 64, 188, 236, 189, 39, 96, 212, 255,
                176, 215, 204, 221, 255, 70, 182, 84, 172, 176, 142, 95 };
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
        List<ByteLut> luts = new ArrayList<ByteLut>();
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
