package org.weasis.core.api.image;

import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.JMVUtils;

public class CropOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(CropOp.class);

    public static final String OP_NAME = Messages.getString("CropOperation.name"); //$NON-NLS-1$

    /**
     * Set the area to crop (Required parameter).
     * 
     * java.awt.Rectangle value.
     */
    public static final String P_AREA = "area";

    /**
     * Whether or not the image origin is shift after cropping.
     * 
     * Boolean value. Default value is false (keep the original image referential).
     */
    public static final String P_SHIFT_TO_ORIGIN = "shift.origin";

    public CropOp() {
        setName(OP_NAME);
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(INPUT_IMG);
        RenderedImage result = source;
        Rectangle area = (Rectangle) params.get(P_AREA);

        if (area == null) {
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", OP_NAME); //$NON-NLS-1$
        } else {
            area =
                area.intersection(new Rectangle(source.getMinX(), source.getMinY(), source.getWidth(), source
                    .getHeight()));
            if (area.width > 1 && area.height > 1) {
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(source);
                pb.add((float) area.x).add((float) area.y);
                pb.add((float) area.width).add((float) area.height);
                result = JAI.create("crop", pb, null); //$NON-NLS-1$

                if (JMVUtils.getNULLtoFalse(params.get(P_SHIFT_TO_ORIGIN))) {
                    float diffw = source.getMinX() - result.getMinX();
                    float diffh = source.getMinY() - result.getMinY();
                    if (diffw != 0.0f || diffh != 0.0f) {
                        pb = new ParameterBlock();
                        pb.addSource(result);
                        pb.add(diffw);
                        pb.add(diffh);
                        result = JAI.create("translate", pb, null); //$NON-NLS-1$
                    }
                }
            }
        }
        params.put(OUTPUT_IMG, result);
    }

}
