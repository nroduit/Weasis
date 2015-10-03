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
import org.weasis.core.api.Messages;

public class SimpleOpManager implements OpManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleOpManager.class);
    public static final String NAME = Messages.getString("SimpleOpManager.img_op"); //$NON-NLS-1$

    public enum Position {
        BEFORE, AFTER
    }

    private final HashMap<String, ImageOpNode> nodes;
    private final List<ImageOpNode> operations;
    private String name;

    public SimpleOpManager() {
        this(null);
    }

    public SimpleOpManager(String name) {
        this.operations = new ArrayList<ImageOpNode>();
        this.nodes = new HashMap<String, ImageOpNode>();
        setName(name);
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized void setName(String name) {
        this.name = name == null ? NAME : name;
    }

    @Override
    public String toString() {
        return name;
    }

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
                    LOGGER.error("Image {} failed: {}", op.getParam(ImageOpNode.NAME), e.getMessage()); //$NON-NLS-1$
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
