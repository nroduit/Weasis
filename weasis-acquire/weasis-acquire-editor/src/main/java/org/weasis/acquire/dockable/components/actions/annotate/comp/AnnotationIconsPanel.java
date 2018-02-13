/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions.annotate.comp;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;

@SuppressWarnings("serial")
public class AnnotationIconsPanel extends JPanel {

    public AnnotationIconsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        MeasureTool.buildIconPanel(this, EventManager.getInstance(), ActionW.MEASURE, ActionW.DRAW_MEASURE, 7);
        MeasureTool.buildIconPanel(this, EventManager.getInstance(), ActionW.DRAW, ActionW.DRAW_GRAPHICS, 7);
    }
}
