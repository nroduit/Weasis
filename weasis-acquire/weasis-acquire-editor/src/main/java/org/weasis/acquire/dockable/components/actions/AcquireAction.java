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
package org.weasis.acquire.dockable.components.actions;

import java.awt.event.ActionListener;

import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

public interface AcquireAction extends ActionListener {
    public enum Cmd {
        INIT, VALIDATE, CANCEL, RESET
    }

    AcquireActionPanel getCentralPanel();

    void validate(AcquireImageInfo imageInfo, ViewCanvas<ImageElement> view);

    void validate();

    boolean cancel();

    boolean reset();
}
