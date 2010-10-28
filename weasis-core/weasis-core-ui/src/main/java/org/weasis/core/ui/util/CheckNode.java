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
package org.weasis.core.ui.util;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * The Class CheckNode.
 * 
 * @author Nicolas Roduit
 */
public class CheckNode extends DefaultMutableTreeNode {

    private boolean selected;
    private final boolean updateParent;
    private final boolean updateChildren;

    public CheckNode(Object object) {
        this(object, false, false, false);
    }

    public CheckNode(Object object, boolean selected) {
        this(object, selected, false, false);
    }

    public CheckNode(Object object, boolean selected, boolean updateParent, boolean updateChildren) {
        super(object);
        this.selected = selected;
        this.updateParent = updateParent;
        this.updateChildren = updateChildren;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean newValue) {
        selected = newValue;
    }

    public boolean isUpdateParent() {
        return updateParent;
    }

    public boolean isUpdateChildren() {
        return updateChildren;
    }

    @Override
    public String toString() {
        return getUserObject().toString();
    }
}
