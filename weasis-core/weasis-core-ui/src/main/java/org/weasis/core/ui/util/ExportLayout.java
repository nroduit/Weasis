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
import java.awt.Component;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.plaf.PanelUI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.editor.image.ViewCanvas;

public class ExportLayout<E extends ImageElement> extends JPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportLayout.class);

    /**
     * The array of display panes located in this image view panel.
     */
    protected final ArrayList<ExportImage<E>> viewList;

    protected final JPanel grid;
    protected GridBagLayoutModel layoutModel;

    public ExportLayout(List<ViewCanvas<E>> view2ds, GridBagLayoutModel layoutModel) {
        this.viewList = new ArrayList<>(view2ds.size());
        for (ViewCanvas<E> v : view2ds) {
            ExportImage<E> export = new ExportImage<>(v);
            export.getInfoLayer().setBorder(3);
            viewList.add(export);
        }
        grid = new JPanel();
        // For having a black background with any Look and Feel
        grid.setUI(new PanelUI() {
        });
        setGridBackground(Color.BLACK);
        this.layoutModel = layoutModel;
        try {
            this.layoutModel = (GridBagLayoutModel) this.layoutModel.clone();
        } catch (CloneNotSupportedException e) {
            LOGGER.error("Clone layoutModel", e);
        }
        setLayoutModel();
        add(grid, BorderLayout.CENTER);
    }

    public void setGridBackground(Color bg) {
        grid.setBackground(bg);
    }

    /** Get the layout of this view panel. */

    public GridBagLayoutModel getLayoutModel() {
        return layoutModel;
    }

    private void setLayoutModel() {
        final LinkedHashMap<LayoutConstraints, Component> elements = this.layoutModel.getConstraints();
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
            v.disposeView();
        }
    }

}
