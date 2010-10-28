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
package org.weasis.core.ui.docking;

import java.awt.Component;

import org.noos.xing.mydoggy.ToolWindow;

public interface DockableTool {

    public ToolWindow registerToolAsDockable();

    public String getDockableUID();

    public ToolWindow getToolWindow();

    public void showDockable();

    public void closeDockable();

    public Component getToolComponent();

}
