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
package org.weasis.base.viewer2d.dockable;

import java.awt.BorderLayout;

import javax.swing.Icon;
import javax.swing.JScrollPane;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.weasis.core.ui.docking.PluginTool;

public class DrawTool extends PluginTool {

    public static final String BUTTON_NAME = "Draw";

    public DrawTool(String pluginName, Icon icon) {
        super(BUTTON_NAME, pluginName, ToolWindowAnchor.RIGHT, PluginTool.TYPE.tool);
        // setTooltips("Measurements, Annotations and ROI");
        JScrollPane jsp = new JScrollPane();
        add(jsp, BorderLayout.CENTER);
    }

    @Override
    protected void changeToolWindowAnchor(ToolWindowAnchor anchor) {
        // TODO Auto-generated method stub

    }
}
