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
package org.weasis.core.api.gui;

import java.awt.Point;
import java.awt.datatransfer.Transferable;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.TransferHandler;

import org.weasis.core.api.Messages;
import org.weasis.core.api.media.data.TagW;

public abstract class InfoViewListPanel extends JPanel {

    public InfoViewListPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    protected abstract void setInfoElement(TagW tag, Point dropPoint);

    private class InfoElementHandler extends TransferHandler {

        public InfoElementHandler() {
            super("InfoElement"); //$NON-NLS-1$
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) {
                return false;
            }
            return support.isDataFlavorSupported(TagW.infoElementDataFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            Transferable transferable = support.getTransferable();
            TagW tag;

            try {
                tag = (TagW) transferable.getTransferData(TagW.infoElementDataFlavor);
                setInfoElement(tag, support.getDropLocation().getDropPoint());
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return true;
        }
    }

}
