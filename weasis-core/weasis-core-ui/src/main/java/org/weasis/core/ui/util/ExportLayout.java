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

import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.editor.image.ViewCanvas;

public class ExportLayout<E extends ImageElement> extends JPanel {

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
        final Map<LayoutConstraints, Component> oldMap = layoutModel.getConstraints();
        final Map<LayoutConstraints, Component> map = new LinkedHashMap<>(oldMap.size());
        this.layoutModel = new GridBagLayoutModel(map, "exp_tmp", "", null);
        Iterator<LayoutConstraints> enumVal = oldMap.keySet().iterator();

        while (enumVal.hasNext()) {
            LayoutConstraints e = enumVal.next();
            Component v = oldMap.get(e);
            LayoutConstraints constraint = (LayoutConstraints) e.clone();

            if (v instanceof ViewCanvas) {
                ExportImage<E> export = new ExportImage<>((ViewCanvas<E>) v);
                export.getInfoLayer().setBorder(3);
                map.put(constraint, export);
                v = export;
            } else {
                // Non printable component. Create a new empty panel to not steel the component from the original UI
                v = new JPanel();
                map.put(constraint, v);
            }

            grid.add(v, e);
        }
        grid.revalidate();
    }

    public void dispose() {
        for (Component c : layoutModel.getConstraints().values()) {
            if (c instanceof ExportImage) {
                ((ExportImage) c).disposeView();
            }
        }
    }

}
