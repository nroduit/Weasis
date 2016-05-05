package org.weasis.core.api.image;

import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;

public class BrightnessOp extends AbstractOp {
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

        double[] constants = { (double) params.get(P_CONTRAST_VALUE) / 100D };
        double[] offsets = { (double) params.get(P_BRIGTNESS_VALUE) };

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(source);
        pb.add(constants);
        pb.add(offsets);

        result = JAI.create("rescale", pb, null);
        params.put(Param.OUTPUT_IMG, result);
    }

}
