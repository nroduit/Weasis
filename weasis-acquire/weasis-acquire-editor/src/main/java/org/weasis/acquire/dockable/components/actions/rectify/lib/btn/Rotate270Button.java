/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions.rectify.lib.btn;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.rectify.RectifyAction;
import org.weasis.acquire.dockable.components.actions.rectify.lib.AbstractRectifyButton;
import org.weasis.acquire.operations.impl.RotationActionListener;
import org.weasis.core.ui.editor.image.MouseActions;

public class Rotate270Button extends AbstractRectifyButton {
    private static final long serialVersionUID = -7825964657723427829L;
    
    private static final int ANGLE = -90;
    private static final Icon ICON = new ImageIcon(MouseActions.class.getResource("/icon/32x32/rotate270.png"));
    private static final String TOOL_TIP = Messages.getString("EditionTool.rotate.270");
    

    
    public Rotate270Button(RectifyAction rectifyAction) {
        super(new RotationActionListener(ANGLE, rectifyAction));
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getToolTip() {
        return TOOL_TIP;
    }


}
