package org.weasis.acquire.explorer.gui.model.publish;

import java.awt.event.MouseEvent;

import javax.swing.tree.TreePath;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;

public class PublishCheckboxTree extends CheckboxTree {
    private static final long serialVersionUID = 4136646391737842615L;

    public PublishCheckboxTree(PublishTreeModel publishTreeModel) {
        super(publishTreeModel.getModel());
    }
    
    @Override
    public String getToolTipText(MouseEvent evt) {
        // TODO return thumbnail
        TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
        if (curPath != null) {
            return "test";
//            Object object = curPath.getLastPathComponent();
//            if (object instanceof ToolTipTreeNode) {
//                return ((ToolTipTreeNode) object).getToolTipText();
//            }
        }
        return null;
    }
}
