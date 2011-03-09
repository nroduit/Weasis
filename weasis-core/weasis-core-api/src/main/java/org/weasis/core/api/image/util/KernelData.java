package org.weasis.core.api.image.util;

import java.io.Serializable;

import javax.media.jai.KernelJAI;

import org.weasis.core.api.Messages;

public class KernelData implements Serializable {
    private static final long serialVersionUID = 5877650534432337573L;

    public final static KernelData NONE = new KernelData(Messages.getString("KernelData.0"), false, 1, 1, new float[] { 1.0F }); //$NON-NLS-1$
    public final static KernelData MEAN = new KernelData(Messages.getString("KernelData.1"), false, 3, 3, 1, 1, new float[] { 1.0F, 1.0F, //$NON-NLS-1$
        1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, }, 9);
    public final static KernelData BLUR = new KernelData(Messages.getString("KernelData.2"), false, 3, 3, 1, 1, new float[] { 0.0F, 1.0F, //$NON-NLS-1$
        0.0F, 1.0F, 4.0F, 1.0F, 0.0F, 1.0F, 0.0F }, 8);
    public final static KernelData BLURMORE = new KernelData(Messages.getString("KernelData.3"), false, 3, 3, 1, 1, new float[] { 1.0F, //$NON-NLS-1$
        2.0F, 1.0F, 2.0F, 2.0F, 2.0F, 1.0F, 2.0F, 1.0F }, 14);
    public final static KernelData SHARPEN = new KernelData(Messages.getString("KernelData.4"), false, 3, 3, 1, 1, new float[] { 0.0F, //$NON-NLS-1$
        -1.0F, 0.0F, -1.0F, 8.0F, -1.0F, 0.0F, -1.0F, 0.0F }, 4);
    public final static KernelData SHARPENMORE = new KernelData(Messages.getString("KernelData.5"), false, 3, 3, 1, 1, new float[] { //$NON-NLS-1$
        -1.0F, -1.0F, -1.0F, -1.0F, 12.0F, -1.0F, -1.0F, -1.0F, -1.0F }, 4);
    public final static KernelData DEFOCUS = new KernelData(Messages.getString("KernelData.6"), false, 3, 3, new float[] { 1.0F, 1.0F, //$NON-NLS-1$
        1.0F, 1.0F, -7.0F, 1.0F, 1.0F, 1.0F, 1.0F });
    public final static KernelData EDGE1 = new KernelData(Messages.getString("KernelData.7"), false, 3, 3, new float[] { 0.0F, //$NON-NLS-1$
        -1.0F, 0.0F, -1.0F, 4.0F, -1.0F, 0.0F, -1.0F, 0.0F });
    public final static KernelData EDGE2 = new KernelData(Messages.getString("KernelData.8"), false, 3, 3, new float[] { -1.0F, //$NON-NLS-1$
        -1.0F, -1.0F, -1.0F, 8.0F, -1.0F, -1.0F, -1.0F, -1.0F });
    public final static KernelData STRONGEDGE = new KernelData(Messages.getString("KernelData.9"), false, 5, 5, new float[] { -2.0F, //$NON-NLS-1$
        -2.0F, -2.0F, -2.0F, -2.0F, -2.0F, -3.0F, -3.0F, -3.0F, -2.0F, -2.0F, -3.0F, 53.0F, -3.0F, -2.0F, -2.0F, -3.0F,
        -3.0F, -3.0F, -2.0F, -2.0F, -2.0F, -2.0F, -2.0F, -2.0F });
    public final static KernelData OUTLINE = new KernelData(Messages.getString("KernelData.10"), false, 5, 5, new float[] { 1.0F, 1.0F, //$NON-NLS-1$
        1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, -16.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F,
        1.0F, 1.0F, 1.0F, 1.0F, 1.0F });

    public final static KernelData EMBOSS = new KernelData(Messages.getString("KernelData.11"), false, 3, 3, new float[] { -5.0F, 0.0F, //$NON-NLS-1$
        0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 5.0F });
    public final static KernelData GAUSSIAN3 = gaussianKernel(Messages.getString("KernelData.12"), 3, 3); //$NON-NLS-1$
    public final static KernelData GAUSSIAN5 = gaussianKernel(Messages.getString("KernelData.13"), 5, 5); //$NON-NLS-1$
    public final static KernelData GAUSSIAN7 = gaussianKernel(Messages.getString("KernelData.14"), 7, 7); //$NON-NLS-1$
    public final static KernelData GAUSSIAN9 = gaussianKernel(Messages.getString("KernelData.15"), 9, 9); //$NON-NLS-1$
    public final static KernelData GAUSSIAN23 = gaussianKernel2(Messages.getString("KernelData.16"), 3); //$NON-NLS-1$
    public final static KernelData GAUSSIAN25 = gaussianKernel2(Messages.getString("KernelData.17"), 5); //$NON-NLS-1$
    public final static KernelData GAUSSIAN27 = gaussianKernel2(Messages.getString("KernelData.18"), 7); //$NON-NLS-1$

    public static KernelData[] ALL_FILTERS = new KernelData[] { NONE, MEAN, BLUR, BLURMORE, SHARPEN, SHARPENMORE,
        DEFOCUS, EDGE1, EDGE2, STRONGEDGE, OUTLINE, EMBOSS, GAUSSIAN3, GAUSSIAN5, GAUSSIAN7, GAUSSIAN9, GAUSSIAN23,
        GAUSSIAN25, GAUSSIAN27 };
    /** The type of the kernel. */
    private boolean morphologicalFilter;

    /** The name of the kernel. */
    private String name;

    /** The width of the kernel. */
    private int width;

    /** The height of the kernel. */
    private int height;

    /** The X coordinate of the key element. */
    private int xOrigin;

    /** The Y coordinate of the key element. */
    private int yOrigin;

    /** The divisior of the kernel values. */
    private int divisor;

    /** The kernel data. */
    private float[] data = null;

    public KernelData(String name, boolean morphologicalFilter, int width, int height, int xOrigin, int yOrigin,
        float[] data, int divisor) {
        this.name = name;
        this.morphologicalFilter = morphologicalFilter;
        this.width = width;
        this.height = height;
        setXOrigin(xOrigin);
        setYOrigin(yOrigin);
        this.divisor = divisor;
        this.data = divideKernel(data);
    }

    public KernelData(String name, boolean morphologicalFilter, int width, int height, float[] data) {
        this(name, morphologicalFilter, width, height, width / 2, height / 2, data, 1);
    }

    public float[] getData() {
        return data;
    }

    private float[] divideKernel(float[] data) {
        if (data == null) {
            return new float[width * height];
        }
        if (divisor == 0) {
            divisor = 1;
        }
        if (divisor == 1) {
            return data;
        }
        float div = divisor;
        for (int i = 0; i < data.length; i++) {
            data[i] /= div;
        }
        return data;
    }

    public int getDivisor() {
        return divisor;
    }

    public int getHeight() {
        return height;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public int getXOrigin() {
        return xOrigin;
    }

    public int getYOrigin() {
        return yOrigin;
    }

    public boolean isMorphologicalFilter() {
        return morphologicalFilter;
    }

    public boolean setYOrigin(int yOrigin) {
        if (yOrigin >= height || yOrigin < 0) {
            this.yOrigin = height / 2;
            return false;
        }
        this.yOrigin = yOrigin;
        return true;
    }

    public boolean setXOrigin(int xOrigin) {
        if (xOrigin >= width || xOrigin < 0) {
            this.xOrigin = width / 2;
            return false;
        }
        this.xOrigin = xOrigin;
        return true;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setDivisor(int divisor) {
        this.divisor = divisor;
    }

    public KernelJAI getKernelJAI() {
        if (data == null) {
            return new KernelJAI(1, 1, 0, 0, new float[] { 1.0F });
        } else {
            return new KernelJAI(width, height, xOrigin, yOrigin, data);
        }
    }

    public final static KernelData makeGaussianKernel(String name, int radius) {
        int diameter = 2 * radius + 1;
        float invrsq = 1.0F / (radius * radius);
        float[] gaussianData = new float[diameter * diameter];

        float sum = 0.0F;
        for (int i = 0; i < diameter; i++) {
            float d = i - radius;
            float val = (float) Math.exp(-d * d * invrsq);
            gaussianData[i] = val;
            sum += val;
        }

        // Normalize
        float invsum = 1.0F / sum;
        for (int i = 0; i < diameter; i++) {
            gaussianData[i] *= invsum;
        }
        for (int i = diameter; i < gaussianData.length; i++) {
            gaussianData[i] = invsum;
        }
        return new KernelData(name, false, diameter, diameter, gaussianData);
    }

    public static int sign(float x) {
        int isign = 1;
        if (x < 0.0F) {
            isign = -1;
            x = -x;
        }
        return isign * (int) (x + 0.5F);
    }

    public final static KernelData gaussianKernel(String name, int nx, int ny) {
        if (nx % 2 == 0) {
            nx++;
        }
        if (ny % 2 == 0) {
            ny++;
        }
        float sigmax = (nx - 1) / 6F;
        float sigmay = (ny - 1) / 6F;
        return gaussianKernel(name, sigmax, sigmay);
    }

    public final static KernelData gaussianKernel(String name, float sigmax, float sigmay) {
        int nx = sign(6F * sigmax);
        int ny = sign(6F * sigmay);
        if (nx % 2 == 0) {
            nx++;
        }
        if (ny % 2 == 0) {
            ny++;
        }
        float gauss_kernel[] = new float[nx * ny];
        float scale = 0.0F;
        if (sigmax == 0.0F) {
            sigmax = 1E-005F;
        }
        if (sigmay == 0.0F) {
            sigmay = 1E-005F;
        }
        for (int j = 0; j < ny; j++) {
            float locy = j - (ny - 1) / 2;
            for (int i = 0; i < nx; i++) {
                float locx = i - (nx - 1) / 2;
                gauss_kernel[j * nx + i] =
                    (float) Math.exp(-0.5F * ((locx * locx) / (sigmax * sigmax) + (locy * locy) / (sigmay * sigmay)));
                scale += gauss_kernel[j * nx + i];
            }

        }
        for (int i = 0; i < gauss_kernel.length; i++) {
            gauss_kernel[i] /= scale;
        }
        return new KernelData(name, false, nx, ny, gauss_kernel);
    }

    public final static KernelData gaussianKernel2(String name, int n) {
        float gauss_kernel[] = new float[n * n];
        float sigma = (n - 1) / 6F;
        float scale = 0.0F;
        for (int i = 0; i < n; i++) {
            float locy = i - (n - 1) / 2;
            for (int j = 0; j < n; j++) {
                float locx = j - (n - 1) / 2;
                float dist = (float) Math.sqrt(locy * locy + locx * locx);
                gauss_kernel[j * n + i] =
                    (-dist / (sigma * sigma)) * (float) Math.exp((-dist * dist) / (2.0F * sigma * sigma));
                scale += gauss_kernel[j * n + i];
            }

        }

        for (int i = 0; i < gauss_kernel.length; i++) {
            gauss_kernel[i] /= scale;
        }
        return new KernelData(name, false, n, n, gauss_kernel);
    }
}
