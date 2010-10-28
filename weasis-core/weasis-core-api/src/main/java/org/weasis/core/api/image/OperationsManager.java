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

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.ImageOperation;

public class OperationsManager {

    private final ImageOperation imageOperation;
    private final ArrayList<ImageOperationAction> operations;

    public OperationsManager(ImageOperation imageOperation) {
        if (imageOperation == null) {
            throw new IllegalArgumentException("ImageOperation cannot be null"); //$NON-NLS-1$
        }
        this.imageOperation = imageOperation;
        operations = new ArrayList<ImageOperationAction>();
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
        if (imageOperation.getImage() == null) {
            return null;
        }
        RenderedImage source = imageOperation.getImage().getImage();
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
