/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse  License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.docking;

import java.awt.Component;

import org.noos.xing.mydoggy.ToolWindow;

public interface DockableTool {

    ToolWindow registerToolAsDockable();

    String getDockableUID();

    ToolWindow getToolWindow();

    void showDockable();

    void closeDockable();

    Component getToolComponent();

}
