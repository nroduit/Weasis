/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.pref;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.swing.Box;
import javax.swing.tree.DefaultMutableTreeNode;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.ui.Messages;

@SuppressWarnings("serial")
public class PreferenceDialog extends AbstractWizardDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreferenceDialog.class);

    public PreferenceDialog(Window parentWin) {
        super(parentWin, Messages.getString("OpenPreferencesAction.title"), ModalityType.APPLICATION_MODAL, //$NON-NLS-1$
            new Dimension(700, 520));
        initializePages();
        pack();

        Component horizontalStrut = Box.createHorizontalStrut(20);
        GridBagConstraints gbcHorizontalStrut = new GridBagConstraints();
        gbcHorizontalStrut.weightx = 1.0;
        gbcHorizontalStrut.insets = new Insets(0, 0, 0, 5);
        gbcHorizontalStrut.gridx = 0;
        gbcHorizontalStrut.gridy = 0;
        jPanelButtom.add(horizontalStrut, gbcHorizontalStrut);
        showPageFirstPage();
    }

    @Override
    protected void initializePages() {
        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put("weasis.user.prefs", System.getProperty("weasis.user.prefs", "user")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
        list.add(new GeneralSetting());
        list.add(new ProxyPrefView());
        list.add(new LabelsPrefView());
        list.add(new ScreenPrefView());

        BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        try {
            for (ServiceReference<PreferencesPageFactory> service : context
                .getServiceReferences(PreferencesPageFactory.class, null)) {
                PreferencesPageFactory factory = context.getService(service);
                if (factory != null) {
                    AbstractItemDialogPage page = factory.createInstance(properties);
                    if (page != null) {
                        list.add(page);
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            LOGGER.error("Get Preference pages from service", e); //$NON-NLS-1$
        }

        InsertableUtil.sortInsertable(list);
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

}
