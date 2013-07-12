package org.weasis.core.api.image;

import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.weasis.core.api.media.data.ImageElement;

public class OpRecord {
    // TODO add limiting parameters, like only binary
    private ImageElement source;
    private final List<ImageOperationAction> operations;

    public OpRecord(ImageElement source) {
        this.source = source;
        this.operations = new ArrayList<ImageOperationAction>();
    }

    public void addImageOperationAction(ImageOperationAction op) {
        if (op != null) {
            operations.add(op);
        }
    }

    public void addImageOperationAction(Collection<? extends ImageOperationAction> items) {
        if (items != null) {
            operations.addAll(items);
        }
    }

    public ImageElement getSource() {
        return source;
    }

    public void setSource(ImageElement source) {
        this.source = source;
    }

    public List<ImageOperationAction> getOperations() {
        return operations;
    }

    public RenderedImage getFinalImage() {
        int size = operations.size();
        RenderedImage srcImage = source.getImage();
        if (size == 0) {
            return srcImage;
        }
        RenderedImage result = srcImage;
        if (srcImage != null && operations.size() > 0) {
            result = srcImage;
            for (int i = 0; i < operations.size(); i++) {
                // if (result == null) {
                // return updateAllOperations();
                // }
                // result = operations.get(i).getRenderedImage(result, imageOperation);
            }
        }
        return result;
    }
}
