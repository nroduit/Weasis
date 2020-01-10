#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package ${package};

import java.awt.Component;

import javax.swing.ImageIcon;
import javax.swing.JScrollPane;
import javax.swing.JViewport;

import org.weasis.core.ui.docking.PluginTool;

import bibliothek.gui.dock.common.CLocation;

public class SampleTool extends PluginTool {

    public static final String BUTTON_NAME = "Tool Sample";

    private final JScrollPane rootPane = new JScrollPane();

    public SampleTool(Type type) {
        super(BUTTON_NAME, "Sample Tool", type, 120);
        dockable.setTitleIcon(new ImageIcon(SampleTool.class.getResource("/icon/22x22/text-html.png"))); //${symbol_dollar}NON-NLS-1${symbol_dollar}
        setDockableWidth(290);
    }

    @Override
    public Component getToolComponent() {
        JViewport viewPort = rootPane.getViewport();
        if (viewPort == null) {
            viewPort = new JViewport();
            rootPane.setViewport(viewPort);
        }
        if (viewPort.getView() != this) {
            viewPort.setView(this);
        }
        return rootPane;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        // TODO Auto-generated method stub
    }

}