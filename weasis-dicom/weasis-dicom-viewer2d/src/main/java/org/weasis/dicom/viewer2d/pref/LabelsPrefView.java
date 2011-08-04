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
package org.weasis.dicom.viewer2d.pref;

import java.awt.BorderLayout;
import java.util.ArrayList;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.util.PaintLabel;
import org.weasis.dicom.viewer2d.EventManager;

public class LabelsPrefView extends AbstractItemDialogPage {

    public LabelsPrefView() {
        setTitle(MeasureTool.LABEL_PREF_NAME);
        setLayout(new BorderLayout(0, 0));

        JPanel panel = new JPanel();
        add(panel);
        ArrayList<Graphic> tools = new ArrayList<Graphic>(MeasureToolBar.graphicList);
        tools.remove(0);
        JComboBox comboBox = new JComboBox(tools.toArray());
        add(comboBox, BorderLayout.NORTH);

        addSubPage(new PaintLabel(EventManager.getInstance()));
        // addSubPage(subPage);
        // addSubPage(subPage);
    }

    private void init() {

    }

    @Override
    public void closeAdditionalWindow() {
    }

    @Override
    public void resetoDefaultValues() {
        // TODO Auto-generated method stub

    }

}
