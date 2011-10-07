package org.weasis.core.api.image;

import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;

public class CropOperation extends AbstractOperation {

    public static final String name = Messages.getString("Crop");

    @Override
    public String getOperationName() {
        return name;
    }

    @Override
    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        Rectangle area = (Rectangle) imageOperation.getActionValue(ActionW.CROP.cmd());
        if (area != null) {
            area =
                area.intersection(new Rectangle(source.getMinX(), source.getMinY(), source.getWidth(), source
                    .getHeight()));
            if (area.width > 1 && area.height > 1) {
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(source);
                pb.add((float) area.x).add((float) area.y);
                pb.add((float) area.width).add((float) area.height);
                result = JAI.create("crop", pb, null); //$NON-NLS-1$

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

        return result;
    }

}
