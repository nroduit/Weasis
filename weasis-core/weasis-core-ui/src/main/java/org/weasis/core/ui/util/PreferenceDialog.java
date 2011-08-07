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
package org.weasis.core.ui.util;

import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.swing.tree.DefaultMutableTreeNode;

import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.internal.Activator;

public class PreferenceDialog extends AbstractWizardDialog {

    public PreferenceDialog(Window parentWin) {
        super(parentWin,
            Messages.getString("OpenPreferencesAction.title"), ModalityType.APPLICATION_MODAL, new Dimension(640, 480)); //$NON-NLS-1$
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
        ArrayList<AbstractItemDialogPage> list = new ArrayList<AbstractItemDialogPage>();
        for (int i = 0; (servicesPref != null) && (i < servicesPref.length); i++) {
            if (servicesPref[i] instanceof PreferencesPageFactory) {
                AbstractItemDialogPage page =
                    ((PreferencesPageFactory) servicesPref[i]).createPreferencesPage(properties);
                if (page != null) {
                    list.add(page);
                }
            }
        }
        Collections.sort(list, new Comparator<AbstractItemDialogPage>() {

            @Override
            public int compare(AbstractItemDialogPage o1, AbstractItemDialogPage o2) {
                return o1.getTitle().compareToIgnoreCase(o2.getTitle());
            }
        });
        for (AbstractItemDialogPage page : list) {
            pagesRoot.add(new DefaultMutableTreeNode(page));
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

    public void showPage(String pageName) {
        if (pageName != null) {
            Enumeration children = pagesRoot.children();
            int index = 0;
            while (children.hasMoreElements()) {
                DefaultMutableTreeNode page = (DefaultMutableTreeNode) children.nextElement();
                Object object = page.getUserObject();
                if (object instanceof AbstractItemDialogPage
                    && pageName.equals(((AbstractItemDialogPage) object).getTitle())) {
                    tree.setSelectionRow(index);
                    tree.expandRow(index);
                    break;
                }
                index++;
            }
        }
    }
}
