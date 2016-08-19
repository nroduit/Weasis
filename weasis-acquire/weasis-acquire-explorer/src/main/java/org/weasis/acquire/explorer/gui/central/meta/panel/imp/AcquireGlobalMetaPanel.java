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
package org.weasis.acquire.explorer.gui.central.meta.panel.imp;

import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireGlobalMeta;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;

public class AcquireGlobalMetaPanel extends AcquireMetadataPanel {
    private static final long serialVersionUID = -2751941971479265507L;
        
    public AcquireGlobalMetaPanel(String title) {
       super(title);
       setMetaVisible(true);
    }

    @Override
    public AcquireMetadataTableModel newTableModel() {
        return new AcquireGlobalMeta();
    }
}
