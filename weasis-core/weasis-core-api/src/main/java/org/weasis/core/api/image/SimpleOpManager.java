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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleOpManager implements OpManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleOpManager.class);

    private final HashMap<String, ImageOpNode> nodes;
    private final List<ImageOpNode> operations;

    public SimpleOpManager() {
        this.operations = new ArrayList<ImageOpNode>();
        this.nodes = new HashMap<String, ImageOpNode>();
    }

    public List<ImageOpNode> getOperations() {
        return operations;
    }

    public void addImageOperationAction(ImageOpNode action) {
        if (action != null) {
            operations.add(action);
            Object name = action.getParam(ImageOpNode.NAME);
            if (name instanceof String) {
                nodes.put((String) name, action);
            }
        }
    }

    public void removeImageOperationAction(ImageOpNode action) {
        if (action != null) {
            operations.remove(action);
            nodes.remove(action);
        }
    }

    @Override
    public SimpleOpManager clone() throws CloneNotSupportedException {
        SimpleOpManager obj = new SimpleOpManager();
        for (Iterator<Entry<String, ImageOpNode>> iter = nodes.entrySet().iterator(); iter.hasNext();) {
            Entry<String, ImageOpNode> el = iter.next();
            ImageOpNode node = el.getValue();
            if (node != null) {
                ImageOpNode n = node.clone();
                obj.operations.add(n);
                obj.nodes.put(el.getKey(), n);
            }
        }
        return obj;
    }

    @Override
    public void removeAllImageOperationAction() {
        clearNodeParams();
        operations.clear();
        nodes.clear();
    }

    @Override
    public void clearNodeParams() {
        for (ImageOpNode node : operations) {
            node.clearParams();
        }
    }

    @Override
    public void clearNodeIOCache() {
        for (ImageOpNode node : operations) {
            node.clearIOCache();
        }
    }

    @Override
    public void setFirstNode(RenderedImage imgSource) {
        ImageOpNode node = getFirstNode();
        if (node != null) {
            node.setParam(ImageOpNode.INPUT_IMG, imgSource);
        }
    }

    @Override
    public RenderedImage getFirstNodeInputImage() {
        ImageOpNode node = getFirstNode();
        if (node != null) {
            return (RenderedImage) node.getParam(ImageOpNode.INPUT_IMG);
        }
        return null;
    }

    @Override
    public ImageOpNode getFirstNode() {
        int size = operations.size();
        if (size > 0) {
            return operations.get(0);
        }
        return null;
    }

    @Override
    public ImageOpNode getNode(String opName) {
        if (opName == null) {
            return null;
        }
        return nodes.get(opName);
    }

    @Override
    public ImageOpNode getLastNode() {
        int size = operations.size();
        if (size > 0) {
            return operations.get(size - 1);
        }
        return null;
    }

    @Override
    public RenderedImage getLastNodeOutputImage() {
        ImageOpNode node = getLastNode();
        if (node != null) {
            return (RenderedImage) node.getParam(ImageOpNode.OUTPUT_IMG);
        }
        return null;
    }

    @Override
    public RenderedImage process() {
        RenderedImage source = getFirstNodeInputImage();
        if (source != null) {
            for (int i = 0; i < operations.size(); i++) {
                ImageOpNode op = operations.get(i);
                try {
                    if (i > 0) {
                        op.setParam(ImageOpNode.INPUT_IMG, operations.get(i - 1).getParam(ImageOpNode.OUTPUT_IMG));
                    }
                    op.process();
                } catch (Exception e) {
                    LOGGER.error("Image {} failed: {}", op.getParam(ImageOpNode.NAME), e.getMessage());
                    // Skip this operation
                    op.setParam(ImageOpNode.OUTPUT_IMG, op.getParam(ImageOpNode.INPUT_IMG));
                }
            }
        } else {
            clearNodeIOCache();
        }
        return getLastNodeOutputImage();
    }

    @Override
    public Object getParamValue(String opName, String param) {
        if (opName != null && param != null) {
            ImageOpNode node = getNode(opName);
            if (node != null) {
                return node.getParam(param);
            }
        }
        return null;
    }

    @Override
    public boolean setParamValue(String opName, String param, Object value) {
        if (opName != null && param != null) {
            ImageOpNode node = getNode(opName);
            if (node != null) {
                node.setParam(param, value);
                return true;
            }
        }
        return false;
    }

    @Override
    public void handleImageOpEvent(ImageOpEvent event) {
        for (ImageOpNode node : operations) {
            node.handleImageOpEvent(event);
        }
    }

}
