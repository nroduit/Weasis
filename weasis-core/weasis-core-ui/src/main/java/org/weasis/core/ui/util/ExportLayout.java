/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.weasis.core.ui.util;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
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

    protected final JPanel grid = new JPanel();
    protected GridBagLayoutModel layoutModel;

    public ExportLayout(GridBagLayoutModel layoutModel) {
        initGrid();
        adaptLayoutModel(layoutModel);
    }

    public ExportLayout(ViewCanvas<E> viewCanvas) {
        initGrid();
        adaptLayoutModel(viewCanvas);

    }

    private void initGrid() {
        // For having a black background with any Look and Feel
        grid.setUI(new PanelUI() {
        });
        setGridBackground(Color.BLACK);
        add(grid, BorderLayout.CENTER);
    }

    public void setGridBackground(Color bg) {
        grid.setBackground(bg);
    }

    /** Get the layout of this view panel. */

    public GridBagLayoutModel getLayoutModel() {
        return layoutModel;
    }

    private void adaptLayoutModel(ViewCanvas<E> viewCanvas) {
        final Map<LayoutConstraints, Component> map = new LinkedHashMap<>(1);
        this.layoutModel = new GridBagLayoutModel(map, "exp_tmp", ""); //$NON-NLS-1$ //$NON-NLS-2$

        ExportImage<E> export = new ExportImage<>(viewCanvas);
        export.getInfoLayer().setBorder(3);
        LayoutConstraints e = new LayoutConstraints(viewCanvas.getClass().getName(), 0, 0, 0, 1, 1, 1.0, 1.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH);
        map.put(e, export);
        grid.add(export, e);
        grid.revalidate();
    }

    private void adaptLayoutModel(GridBagLayoutModel layoutModel) {
        final Map<LayoutConstraints, Component> oldMap = layoutModel.getConstraints();
        final Map<LayoutConstraints, Component> map = new LinkedHashMap<>(oldMap.size());
        this.layoutModel = new GridBagLayoutModel(map, "exp_tmp", ""); //$NON-NLS-1$ //$NON-NLS-2$
        Iterator<LayoutConstraints> enumVal = oldMap.keySet().iterator();

        while (enumVal.hasNext()) {
            LayoutConstraints e = enumVal.next();
            Component v = oldMap.get(e);
            LayoutConstraints constraint = e.copy();

            if (v instanceof ViewCanvas) {
                ExportImage<?> export = new ExportImage<>((ViewCanvas) v);
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
