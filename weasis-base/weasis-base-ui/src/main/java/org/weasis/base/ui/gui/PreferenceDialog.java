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
package org.weasis.base.ui.gui;

import java.awt.Dimension;

import javax.swing.tree.DefaultMutableTreeNode;

import org.weasis.base.ui.Messages;
import org.weasis.base.ui.internal.Activator;
import org.weasis.core.api.gui.util.AbstractWizardDialog;

public class PreferenceDialog extends AbstractWizardDialog {

    public PreferenceDialog() {
        super(WeasisWin.getInstance(), Messages.getString("OpenPreferencesAction.title"), true, new Dimension(640, 480)); //$NON-NLS-1$
        initializePages();
        pack();
        initGUI();
    }

    @Override
    protected void initializePages() {
        pagesRoot.add(new DefaultMutableTreeNode(new GeneralSetting()));
        final Object[] servicesPref = Activator.getPreferencesPages();
        for (int i = 0; (servicesPref != null) && (i < servicesPref.length); i++) {
            pagesRoot.add(new DefaultMutableTreeNode(servicesPref[i]));
        }
        iniTree();
    }

    @Override
    public void cancel() {
        dispose();
    }

    @Override
    public void dispose() {
        closeAllPages();
        super.dispose();
    }

    private void initGUI() {
        showPageFirstPage();
    }
}
