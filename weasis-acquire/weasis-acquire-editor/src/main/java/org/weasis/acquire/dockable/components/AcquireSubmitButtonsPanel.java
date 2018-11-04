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
package org.weasis.acquire.dockable.components;

import java.awt.FlowLayout;

import javax.swing.JPanel;
import javax.swing.UIManager;

import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.AcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireAction.Cmd;

public class AcquireSubmitButtonsPanel extends JPanel {
    private static final long serialVersionUID = 4890844708371941062L;

    private final AcquireActionButton cancelBtn;
    private final AcquireActionButton resetBtn;

    public AcquireSubmitButtonsPanel() {
        setBorder(UIManager.getBorder("TitledBorder.border")); //$NON-NLS-1$
        FlowLayout flowLayout = new FlowLayout(FlowLayout.CENTER, 10, 10);
        setLayout(flowLayout);

        cancelBtn = new AcquireActionButton(Messages.getString("AcquireSubmitButtonsPanel.cancel"), Cmd.CANCEL); //$NON-NLS-1$
        cancelBtn.setToolTipText(Messages.getString("AcquireSubmitButtonsPanel.return_prev")); //$NON-NLS-1$
        resetBtn = new AcquireActionButton(Messages.getString("AcquireSubmitButtonsPanel.reset"), Cmd.RESET); //$NON-NLS-1$
        resetBtn.setToolTipText(Messages.getString("AcquireSubmitButtonsPanel.reset_def")); //$NON-NLS-1$

        add(cancelBtn);
        add(resetBtn);
    }

    public void setAcquireAction(AcquireAction acquireAction) {
        cancelBtn.setAcquireAction(acquireAction);
        resetBtn.setAcquireAction(acquireAction);
    }
}
