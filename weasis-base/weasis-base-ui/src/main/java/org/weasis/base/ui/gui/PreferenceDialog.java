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
import java.util.Hashtable;

import javax.swing.tree.DefaultMutableTreeNode;

import org.weasis.base.ui.Messages;
import org.weasis.base.ui.internal.Activator;
import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
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
        Hashtable<String, Object> properties = new Hashtable<String, Object>();
        properties.put("weasis.user.prefs", System.getProperty("weasis.user.prefs", "user"));
        final Object[] servicesPref = Activator.getPreferencesPages();
        for (int i = 0; (servicesPref != null) && (i < servicesPref.length); i++) {
            if (servicesPref[i] instanceof PreferencesPageFactory) {
                AbstractItemDialogPage page =
                    ((PreferencesPageFactory) servicesPref[i]).createPreferencesPage(properties);
                if (page != null) {
                    pagesRoot.add(new DefaultMutableTreeNode(page));
                }
            }
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
