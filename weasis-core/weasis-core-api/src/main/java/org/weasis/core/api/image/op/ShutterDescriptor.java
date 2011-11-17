package org.weasis.core.api.image.op;

import java.awt.RenderingHints;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;

import javax.media.jai.JAI;
import javax.media.jai.OperationDescriptorImpl;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.ROIShape;
import javax.media.jai.RenderedOp;
import javax.media.jai.TiledImage;
import javax.media.jai.registry.RenderedRegistryMode;

import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.LayoutUtil;

import com.sun.media.jai.util.ImageUtil;

public class ShutterDescriptor extends OperationDescriptorImpl implements RenderedImageFactory {
    /**
     * The resource strings that provide the general documentation and specify the parameter list for the "Sample"
     * operation.
     */
    private static final String[][] resources = { { "GlobalName", "Shutter" }, //$NON-NLS-1$ //$NON-NLS-2$

    { "LocalName", "Shutter" }, //$NON-NLS-1$ //$NON-NLS-2$

    { "Vendor", "" }, //$NON-NLS-1$ //$NON-NLS-2$

    { "Description", "Apply an shutter to the image" }, //$NON-NLS-1$ //$NON-NLS-2$

    { "DocURL", "" }, //$NON-NLS-1$ //$NON-NLS-2$

    { "Version", "1.0" } }; //$NON-NLS-1$ //$NON-NLS-2$

    private static final String[] paramNames = { "roi", "color" }; //$NON-NLS-1$ //$NON-NLS-2$
    private static final Class[] paramClasses = { ROIShape.class, int[].class };
    private static final Object[] paramDefaults = { null, null };

    public ShutterDescriptor() {
        super(resources, new String[] { "rendered" }, 1, paramNames, paramClasses, paramDefaults, null); //$NON-NLS-1$
    }

    /**
     * Creates a SampleOpImage with the given ParameterBlock if the SampleOpImage can handle the particular
     * ParameterBlock.
     */
    @Override
    public RenderedImage create(ParameterBlock paramBlock, RenderingHints renderHints) {
        PlanarImage source1 = (PlanarImage) paramBlock.getRenderedSource(0);
        if (source1 == null) {
            return null;
        }
        ROIShape shape = (ROIShape) paramBlock.getObjectParameter(0);
        if (shape == null) {
            return source1;
        }

        TiledImage image;
        if (ImageUtil.isBinary(source1.getSampleModel())) {
            image =
                new TiledImage(source1.getMinX(), source1.getMinY(), source1.getWidth(), source1.getHeight(),
                    source1.getTileGridXOffset(), source1.getTileGridYOffset(), LayoutUtil.createBinarySampelModel(),
                    LayoutUtil.createBinaryIndexColorModel());
        } else {
            int[] rgb = (int[]) paramBlock.getObjectParameter(1);

            Byte[] bandValues;
            if (source1.getSampleModel().getNumBands() == 3) {
                if (rgb == null || rgb.length < 3) {
                    rgb = new int[3];
                }
                bandValues = new Byte[] { (byte) rgb[0], (byte) rgb[1], (byte) rgb[2] };
            } else {
                if (rgb == null || rgb.length < 1) {
                    rgb = new int[1];
                }
                bandValues = new Byte[] { (byte) rgb[0] };
            }
            image = ImageFiler.getEmptyTiledImage(bandValues, source1.getWidth(), source1.getHeight());
        }

        image.set(source1, shape);
        return image;
    }

    public static RenderedOp create(RenderedImage source0, ROIShape roi, int[] color, RenderingHints hints) {
        ParameterBlockJAI pb = new ParameterBlockJAI("Shutter", RenderedRegistryMode.MODE_NAME); //$NON-NLS-1$
        pb.setSource("source0", source0); //$NON-NLS-1$
        pb.setParameter("roi", roi); //$NON-NLS-1$
        pb.setParameter("color", color); //$NON-NLS-1$
        return JAI.create("Shutter", pb, hints); //$NON-NLS-1$
    }
}
