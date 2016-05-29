package org.weasis.core.api.image;

import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrightnessOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrightnessOp.class);

    public static final String OP_NAME = "rescale";

    public static final String P_BRIGTNESS_VALUE = "rescale.brightness";
    public static final String P_CONTRAST_VALUE = "rescale.contrast";

    public BrightnessOp() {
        setName(OP_NAME);
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(Param.INPUT_IMG);
        RenderedImage result = source;

        Double contrast = (Double)params.get(P_CONTRAST_VALUE);
        Double brigtness = (Double) params.get(P_BRIGTNESS_VALUE);
       
        if (contrast == null || brigtness == null) {
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", OP_NAME); //$NON-NLS-1$
        } else {
            double[] constants = { contrast / 100D };
            double[] offsets = { brigtness };
            
            ParameterBlock pb = new ParameterBlock();
            pb.addSource(source);
            pb.add(constants);
            pb.add(offsets);

            result = JAI.create("rescale", pb, null);
        }

        params.put(Param.OUTPUT_IMG, result);
    }

}
