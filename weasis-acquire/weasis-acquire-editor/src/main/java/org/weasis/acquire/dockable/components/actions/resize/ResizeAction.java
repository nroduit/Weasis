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
package org.weasis.acquire.dockable.components.actions.resize;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 * 
 */
public class ResizeAction extends AbstractAcquireAction {

    public ResizeAction(AcquireActionButtonsPanel panel) {
        super(panel);
    }

    @Override
    public void validate(AcquireImageInfo imageInfo, ViewCanvas<ImageElement> view) {
        // Nothing to do
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new ResizePanel();
    }

}
