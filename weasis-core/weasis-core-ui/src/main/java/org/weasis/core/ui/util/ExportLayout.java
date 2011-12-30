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

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.plaf.PanelUI;

import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ExportImage;

public class ExportLayout<E extends ImageElement> extends JPanel {

    /**
     * The array of display panes located in this image view panel.
     */
    protected final ArrayList<ExportImage<E>> viewList;

    protected final JPanel grid;
    protected GridBagLayoutModel layoutModel;

    public ExportLayout(ArrayList<DefaultView2d<E>> view2ds, GridBagLayoutModel layoutModel) {
        this.viewList = new ArrayList<ExportImage<E>>(view2ds.size());
        for (DefaultView2d<E> v : view2ds) {
            ExportImage export = new ExportImage(v);
            export.getInfoLayer().setBorder(3);
            viewList.add(export);
        }
        grid = new JPanel();
        // For having a black background with any Look and Feel
        grid.setUI(new PanelUI() {
        });
        grid.setBackground(Color.BLACK);
        this.layoutModel = layoutModel;
        try {
            this.layoutModel = (GridBagLayoutModel) this.layoutModel.clone();
        } catch (CloneNotSupportedException e1) {
            e1.printStackTrace();
        }
        setLayoutModel();
        add(grid, BorderLayout.CENTER);

    }

    /** Get the layout of this view panel. */

    public GridBagLayoutModel getLayoutModel() {
        return layoutModel;
    }

    private void setLayoutModel() {
        final LinkedHashMap<LayoutConstraints, JComponent> elements = this.layoutModel.getConstraints();
        Iterator<LayoutConstraints> enumVal = elements.keySet().iterator();
        int index = 0;
        while (enumVal.hasNext()) {
            LayoutConstraints e = enumVal.next();
            ExportImage<E> v = viewList.get(index);
            elements.put(e, v);
            grid.add(v, e);
            index++;
        }
        grid.revalidate();
    }

    public void dispose() {
        for (ExportImage<E> v : viewList) {
            v.dispose();
        }
    }

}
