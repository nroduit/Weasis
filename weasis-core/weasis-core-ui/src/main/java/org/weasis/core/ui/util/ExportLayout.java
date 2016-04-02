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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.plaf.PanelUI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ExportImage;

public class ExportLayout<E extends ImageElement> extends JPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportLayout.class);

    protected final JPanel grid;
    protected GridBagLayoutModel layoutModel;

    public ExportLayout(GridBagLayoutModel layoutModel) {
        grid = new JPanel();
        // For having a black background with any Look and Feel
        grid.setUI(new PanelUI() {
        });
        setGridBackground(Color.BLACK);
        adaptLayoutModel(layoutModel);
        add(grid, BorderLayout.CENTER);
    }

    public void setGridBackground(Color bg) {
        grid.setBackground(bg);
    }

    /** Get the layout of this view panel. */

    public GridBagLayoutModel getLayoutModel() {
        return layoutModel;
    }

    private void adaptLayoutModel(GridBagLayoutModel layoutModel) {
        final Map<LayoutConstraints, Component> old = layoutModel.getConstraints();
        final LinkedHashMap<LayoutConstraints, Component> elements =
            new LinkedHashMap<LayoutConstraints, Component>(old.size());
        this.layoutModel = new GridBagLayoutModel(elements, "exp_tmp", "", null);
        Iterator<LayoutConstraints> enumVal = old.keySet().iterator();

        while (enumVal.hasNext()) {
            LayoutConstraints e = enumVal.next();
            Component v = old.get(e);
            LayoutConstraints constraint = (LayoutConstraints) e.clone();

            if (v instanceof DefaultView2d) {
                ExportImage export = new ExportImage((DefaultView2d) v);
                export.getInfoLayer().setBorder(3);
                elements.put(constraint, export);
                v = export;
            } else {
                // Create a new empty panel to net steel the component from the original layout
                v = new JPanel();
                elements.put(constraint, v);
            }

            grid.add(v, e);
        }
        grid.revalidate();
    }

    public void dispose() {
        for (Component c : layoutModel.getConstraints().values()) {
            if (c instanceof ExportImage) {
                ((ExportImage) c).dispose();
            }
        }
    }

}
