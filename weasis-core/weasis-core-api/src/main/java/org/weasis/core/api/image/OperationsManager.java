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
package org.weasis.core.api.image;

import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.List;

import org.weasis.core.api.gui.ImageOperation;

public class OperationsManager {

    private final ImageOperation imageOperation;
    private final List<ImageOperationAction> operations;

    public OperationsManager(ImageOperation imageOperation) {
        if (imageOperation == null) {
            throw new IllegalArgumentException("ImageOperation cannot be null"); //$NON-NLS-1$
        }
        this.imageOperation = imageOperation;
        this.operations = new ArrayList<ImageOperationAction>();
    }

    public List<ImageOperationAction> getOperations() {
        return operations;
    }

    public RenderedImage getSourceImage(String name) {
        for (int i = 0; i < operations.size(); i++) {
            if (operations.get(i).getOperationName().equals(name)) {
                if (i == 0) {
                    return imageOperation.getSourceImage();
                }
                return operations.get(i - 1).getRenderedImageNode();
            }
        }
        return null;
    }

    public RenderedImage getFinalImage() {
        int size = operations.size();
        RenderedImage source = imageOperation.getSourceImage();
        if (size == 0) {
            return source;
        }
        RenderedImage result = source;
        if (source != null && operations.size() > 0) {
            result = source;
            for (int i = 0; i < operations.size(); i++) {
                if (result == null) {
                    return updateAllOperations();
                }
                result = operations.get(i).getRenderedImage(result, imageOperation);
            }
        }
        return result;
    }

    public void addImageOperationAction(ImageOperationAction action) {
        if (action != null) {
            operations.add(action);
        }
    }

    public void removeAllImageOperationAction() {
        operations.clear();
    }

    public void clearCacheNodes() {
        for (int i = 1; i < operations.size(); i++) {
            operations.get(i).clearNode();
        }
    }

    public RenderedImage updateAllOperations() {
        RenderedImage source = imageOperation.getSourceImage();
        if (operations.size() == 0) {
            return source;
        }
        RenderedImage result = null;
        if (source != null && operations.size() > 0) {
            result = operations.get(0).getRenderedImage(source, imageOperation);
            for (int i = 1; i < operations.size(); i++) {
                if (result == null) {
                    return null;
                }
                result = operations.get(i).getRenderedImage(result, imageOperation);
            }
        }
        return result;
    }

    public RenderedImage updateOperation(String name) {
        RenderedImage result = null;
        int index = getOperationIndex(name);
        if (index == 0) {
            result = updateAllOperations();
        } else if (index > 0) {
            if (operations.size() > 0) {
                result = operations.get(index - 1).getRenderedImageNode();
                for (int i = index; i < operations.size(); i++) {
                    if (result == null) {
                        return updateAllOperations();
                    }
                    result = operations.get(i).getRenderedImage(result, imageOperation);
                }
            }
        }
        return result;
    }

    private int getOperationIndex(String name) {
        for (int i = 0; i < operations.size(); i++) {
            if (operations.get(i).getOperationName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

}
