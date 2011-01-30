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
package org.weasis.dicom.codec;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;

import javax.swing.JComponent;
import javax.swing.TransferHandler;

import org.weasis.core.api.gui.InfoViewListPanel;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;
import org.weasis.dicom.codec.display.CornerInfoData;

public class InfoViewElementPanel extends InfoViewListPanel {

    private CornerInfoData corner;
    private final InfoView[] infoView;

    public InfoViewElementPanel() {
        setBackground(Color.BLACK);
        infoView = new InfoView[CornerInfoData.ELEMENT_NUMBER];
        for (int i = 0; i < infoView.length; i++) {
            infoView[i] = new InfoView();
            this.add(infoView[i]);
        }
    }

    public CornerInfoData getCorner() {
        return corner;
    }

    public void setCorner(CornerInfoData corner) {
        this.corner = corner;
        if (corner != null) {
            TagW[] elements = corner.getInfos();
            for (int i = 0; i < infoView.length; i++) {
                infoView[i].setInfoElement(elements[i]);
            }
        }
        this.repaint();
    }

    @Override
    protected void setInfoElement(TagW tag, Point dropPoint) {
        Component c = this.getComponentAt(dropPoint);
        if (c instanceof InfoView) {
            int index = getInfoViewIndex((InfoView) c);
            if (index >= 0) {
                corner.getInfos()[index] = tag;
                infoView[index].setInfoElement(tag);
                infoView[index].repaint();
            }
        }
    }

    private int getInfoViewIndex(InfoView info) {
        for (int i = 0; i < infoView.length; i++) {
            if (info.equals(infoView[i])) {
                return i;
            }
        }
        return -1;
    }

    private static class InfoView extends JComponent {

        private TagW infoElement;

        public InfoView() {
            setBackground(Color.BLACK);
            setForeground(Color.WHITE);
        }

        public TagW getInfoElement() {
            return infoElement;
        }

        public void setInfoElement(TagW infoElement) {
            this.infoElement = infoElement;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            final Rectangle rect = getBounds();
            if (infoElement != null) {
                FontMetrics frc = g2.getFontMetrics();
                final float midfontHeight = FontTools.getAccurateFontHeight(g2) * FontTools.getMidFontHeightFactor();
                String str = infoElement.getName();
                int stringWidth = frc.stringWidth(str);
                g2.drawString(str, (rect.width / 2.0f - stringWidth / 2.0f), (rect.height / 2.0f + midfontHeight));
            }
            float[] dashes = { 10.0F, 5.0F };
            g2.setPaint(Color.LIGHT_GRAY);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND, 10.0f, dashes, 0.0f));
            g2.drawRoundRect(10, 5, rect.width - 20, rect.height - 10, 10, 10);
        }
    }

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
            DicomTag tag;

            try {
                tag = (DicomTag) transferable.getTransferData(TagW.infoElementDataFlavor);
                setInfoElement(tag, support.getDropLocation().getDropPoint());
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return true;
        }
    }

}
