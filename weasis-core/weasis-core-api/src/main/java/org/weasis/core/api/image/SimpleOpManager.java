/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.image;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.opencv.data.PlanarImage;

public class SimpleOpManager implements OpManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleOpManager.class);

    public static final String IMAGE_OP_NAME = Messages.getString("SimpleOpManager.img_op"); //$NON-NLS-1$

    public enum Position {
        BEFORE, AFTER
    }

    private final HashMap<String, ImageOpNode> nodes;
    private final List<ImageOpNode> operations;
    private String name;

    public SimpleOpManager() {
        this(IMAGE_OP_NAME);
    }

    public SimpleOpManager(String name) {
        this.operations = new ArrayList<>();
        this.nodes = new HashMap<>();
        setName(name);
    }

    public SimpleOpManager(SimpleOpManager som) {
        this.operations = new ArrayList<>();
        this.nodes = new HashMap<>();
        setName(som.name);

        som.nodes.entrySet().forEach(el -> {
            Optional.ofNullable(el.getValue()).ifPresent(n -> {
                ImageOpNode node = n.copy();
                operations.add(node);
                nodes.put(el.getKey(), node);
            });
        });
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized void setName(String name) {
        this.name = name == null ? IMAGE_OP_NAME : name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public List<ImageOpNode> getOperations() {
        return operations;
    }

    public void addImageOperationAction(ImageOpNode action) {
        addImageOperationAction(action, null, null);
    }

    public void addImageOperationAction(ImageOpNode action, Position pos, ImageOpNode positionRef) {
        if (action != null) {
            String title = action.getName();
            int k = 2;
            while (nodes.get(title) != null) {
                title += " " + k; //$NON-NLS-1$
                k++;
            }
            if (k > 2) {
                action.setName(title);
                LOGGER.warn("This name already exists, rename to {}.", title); //$NON-NLS-1$
            }
            nodes.put(title, action);
            if (positionRef != null) {
                int index = operations.indexOf(positionRef);
                if (Position.AFTER.equals(pos)) {
                    index++;
                }
                if (index >= 0) {
                    operations.add(index, action);
                } else {
                    operations.add(action);
                }
            } else {
                operations.add(action);
            }
        }
    }

    public void removeImageOperationAction(ImageOpNode action) {
        if (action != null) {
            boolean remove = operations.remove(action);
            if (nodes.remove(action.getName()) == null && remove) {
                for (Entry<String, ImageOpNode> entry : nodes.entrySet()) {
                    if (entry.getValue() == action) {
                        nodes.remove(entry.getKey());
                        break;
                    }
                }
            }
        }
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
    public void setFirstNode(PlanarImage imgSource) {
        ImageOpNode node = getFirstNode();
        if (node != null) {
            node.setParam(Param.INPUT_IMG, imgSource);
        }
    }

    @Override
    public PlanarImage getFirstNodeInputImage() {
        ImageOpNode node = getFirstNode();
        if (node != null) {
            return (PlanarImage) node.getParam(Param.INPUT_IMG);
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
    public PlanarImage getLastNodeOutputImage() {
        ImageOpNode node = getLastNode();
        if (node != null) {
            return (PlanarImage) node.getParam(Param.OUTPUT_IMG);
        }
        return null;
    }

    /**
     * Allow to remove the preprocessing cache
     *
     * @param imgSource
     */
    public void resetLastNodeOutputImage() {
        ImageOpNode node = getLastNode();
        if (node != null) {
            node.setParam(Param.OUTPUT_IMG, null);
        }
    }

    @Override
    public PlanarImage process() {
        PlanarImage source = getFirstNodeInputImage();
        if (source != null && source.width() > 0) {
            for (int i = 0; i < operations.size(); i++) {
                ImageOpNode op = operations.get(i);
                try {
                    if (i > 0) {
                        op.setParam(Param.INPUT_IMG, operations.get(i - 1).getParam(Param.OUTPUT_IMG));
                    }
                    if (op.isEnabled()) {
                        op.process();
                    } else {
                        // Skip this operation
                        op.setParam(Param.OUTPUT_IMG, op.getParam(Param.INPUT_IMG));
                    }
                } catch (Exception e) {
                    LOGGER.error("Image {} failed", op.getParam(Param.NAME), e); //$NON-NLS-1$
                    op.setParam(Param.OUTPUT_IMG, op.getParam(Param.INPUT_IMG));
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
    public void removeParam(String opName, String param) {
        if (opName != null && param != null) {
            ImageOpNode node = getNode(opName);
            if (node != null) {
                node.removeParam(param);
            }
        }
    }

    @Override
    public void handleImageOpEvent(ImageOpEvent event) {
        for (ImageOpNode node : operations) {
            node.handleImageOpEvent(event);
        }
    }

    @Override
    public SimpleOpManager copy() {
        return new SimpleOpManager(this);
    }

}
